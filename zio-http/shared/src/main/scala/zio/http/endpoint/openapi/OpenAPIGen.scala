package zio.http.endpoint.openapi

import java.util.UUID

import scala.collection.immutable.ListMap
import scala.collection.{immutable, mutable}

import zio._
import zio.json.EncoderOps
import zio.json.ast.Json

import zio.schema.Schema.Record
import zio.schema.codec.JsonCodec
import zio.schema.{Schema, TypeId}

import zio.http._
import zio.http.codec.HttpCodec.Metadata
import zio.http.codec._
import zio.http.endpoint._
import zio.http.endpoint.openapi.JsonSchema.SchemaStyle
import zio.http.endpoint.openapi.OpenAPI.{Path, PathItem}

object OpenAPIGen {
  private val PathWildcard = "pathWildcard"

  private[openapi] def groupMap[A, K, B](chunk: Chunk[A])(key: A => K)(f: A => B): immutable.Map[K, Chunk[B]] = {
    val m = mutable.Map.empty[K, mutable.Builder[B, Chunk[B]]]
    for (elem <- chunk) {
      val k    = key(elem)
      val bldr = m.getOrElseUpdate(k, Chunk.newBuilder[B])
      bldr += f(elem)
    }
    class Result extends runtime.AbstractFunction1[(K, mutable.Builder[B, Chunk[B]]), Unit] {
      var built = immutable.Map.empty[K, Chunk[B]]

      def apply(kv: (K, mutable.Builder[B, Chunk[B]])): Unit =
        built = built.updated(kv._1, kv._2.result())
    }
    val result = new Result
    m.foreach(result)
    result.built
  }

  final case class MetaCodec[T](codec: T, annotations: Chunk[HttpCodec.Metadata[_]]) {
    lazy val docs: Doc = {
      val annotatedDoc    = annotations.foldLeft(Doc.empty) {
        case (doc, HttpCodec.Metadata.Documented(nextDoc)) => doc + nextDoc
        case (doc, _)                                      => doc
      }
      val trailingPathDoc = codec.asInstanceOf[Any] match {
        case SegmentCodec.Trailing =>
          Doc.p(
            Doc.Span.bold("WARNING: This is wildcard path segment. There is no official OpenAPI support for this."),
          ) +
            Doc.p("Tools might URL encode this segment and it might not work as expected.")
        case _                     =>
          Doc.empty
      }
      annotatedDoc + trailingPathDoc
    }

    lazy val docsOpt: Option[Doc] = if (docs.isEmpty) None else Some(docs)

    lazy val examples: Map[String, Any] = annotations.foldLeft(Map.empty[String, Any]) {
      case (examples, HttpCodec.Metadata.Examples(nextExamples)) => examples ++ nextExamples
      case (examples, _)                                         => examples
    }

    def examples(schema: Schema[_]): Map[String, OpenAPI.ReferenceOr.Or[OpenAPI.Example]] =
      examples.map { case (k, v) =>
        k -> OpenAPI.ReferenceOr.Or(OpenAPI.Example(toJsonAst(schema, v)))
      }

    def name: Option[String] =
      codec match {
        case value: SegmentCodec[_] =>
          value match {
            case SegmentCodec.BoolSeg(name) => Some(name)
            case SegmentCodec.IntSeg(name)  => Some(name)
            case SegmentCodec.LongSeg(name) => Some(name)
            case SegmentCodec.Text(name)    => Some(name)
            case SegmentCodec.UUID(name)    => Some(name)
            case SegmentCodec.Trailing      => Some(PathWildcard)
            case _                          => None
          }
        case _                      =>
          findName(annotations)
      }

    def required: Boolean =
      !annotations.exists(_.isInstanceOf[HttpCodec.Metadata.Optional[_]])

    def deprecated: Boolean =
      annotations.exists(_.isInstanceOf[HttpCodec.Metadata.Deprecated[_]])
  }
  final case class AtomizedMetaCodecs(
    method: Chunk[MetaCodec[SimpleCodec[Method, _]]],
    path: Chunk[MetaCodec[SegmentCodec[_]]],
    query: Chunk[MetaCodec[HttpCodec.Query[_, _]]],
    header: Chunk[MetaCodec[HttpCodec.Header[_]]],
    content: Chunk[MetaCodec[HttpCodec.Atom[HttpCodecType.Content, _]]],
    status: Chunk[MetaCodec[HttpCodec.Status[_]]],
  ) {
    def append(metaCodec: MetaCodec[_]): AtomizedMetaCodecs = metaCodec match {
      case MetaCodec(codec: HttpCodec.Method[_], annotations) =>
        copy(method =
          (method :+ MetaCodec(codec.codec, annotations)).asInstanceOf[Chunk[MetaCodec[SimpleCodec[Method, _]]]],
        )
      case MetaCodec(_: SegmentCodec[_], _)                   =>
        copy(path = path :+ metaCodec.asInstanceOf[MetaCodec[SegmentCodec[_]]])
      case MetaCodec(_: HttpCodec.Query[_, _], _)             =>
        copy(query = query :+ metaCodec.asInstanceOf[MetaCodec[HttpCodec.Query[_, _]]])
      case MetaCodec(_: HttpCodec.Header[_], _)               =>
        copy(header = header :+ metaCodec.asInstanceOf[MetaCodec[HttpCodec.Header[_]]])
      case MetaCodec(_: HttpCodec.Status[_], _)               =>
        copy(status = status :+ metaCodec.asInstanceOf[MetaCodec[HttpCodec.Status[_]]])
      case MetaCodec(_: HttpCodec.Content[_], _)              =>
        copy(content = content :+ metaCodec.asInstanceOf[MetaCodec[HttpCodec.Atom[HttpCodecType.Content, _]]])
      case MetaCodec(_: HttpCodec.ContentStream[_], _)        =>
        copy(content = content :+ metaCodec.asInstanceOf[MetaCodec[HttpCodec.Atom[HttpCodecType.Content, _]]])
      case _                                                  => this
    }

    def ++(that: AtomizedMetaCodecs): AtomizedMetaCodecs =
      AtomizedMetaCodecs(
        method ++ that.method,
        path ++ that.path,
        query ++ that.query,
        header ++ that.header,
        content ++ that.content,
        status ++ that.status,
      )

    def contentExamples: Map[String, OpenAPI.ReferenceOr.Or[OpenAPI.Example]] =
      content.flatMap {
        case mc @ MetaCodec(HttpCodec.Content(codec, _, _), _) if codec.lookup(MediaType.application.json).isDefined =>
          mc.examples(codec.lookup(MediaType.application.json).get.schema)
        case mc @ MetaCodec(HttpCodec.ContentStream(codec, _, _), _)
            if codec.lookup(MediaType.application.json).isDefined =>
          mc.examples(codec.lookup(MediaType.application.json).get.schema)
        case _                                                                                                       =>
          Map.empty[String, OpenAPI.ReferenceOr.Or[OpenAPI.Example]]
      }.toMap

    // in case of alternatives,
    // the doc to the alternation is added to all sub elements of the alternatives.
    // This is not ideal. But it is the best we can do.
    // To get the doc that is only for the alternation, we take the intersection of all docs,
    // since only the alternation doc is added to all sub elements.
    def contentDocs: Doc =
      content
        .flatMap(_.docsOpt)
        .map(_.flattened)
        .reduceOption(_ intersect _)
        .flatMap(_.reduceOption(_ + _))
        .getOrElse(Doc.empty)

    def optimize: AtomizedMetaCodecs =
      AtomizedMetaCodecs(
        method.materialize,
        path.materialize,
        query.materialize,
        header.materialize,
        content.materialize,
        status.materialize,
      )
  }

  object AtomizedMetaCodecs {
    def empty: AtomizedMetaCodecs = AtomizedMetaCodecs(
      method = Chunk.empty,
      path = Chunk.empty,
      query = Chunk.empty,
      header = Chunk.empty,
      content = Chunk.empty,
      status = Chunk.empty,
    )

    def flatten[R, A](codec: HttpCodec[R, A]): AtomizedMetaCodecs = {
      val atoms = flattenedAtoms(codec)

      val flattened = atoms
        .foldLeft(AtomizedMetaCodecs.empty) { case (acc, atom) =>
          acc.append(atom)
        }
        .optimize
      flattened
    }

    private def flattenedAtoms[R, A](
      in: HttpCodec[R, A],
      annotations: Chunk[HttpCodec.Metadata[Any]] = Chunk.empty,
    ): Chunk[MetaCodec[_]] =
      in match {
        case codec @ HttpCodec.Combine(left, right, combiner) =>
          flattenedAtoms(
            left,
            HttpCodec
              .reduceExamplesLeft[A, codec.Left, codec.Right](
                annotations.asInstanceOf[Chunk[HttpCodec.Metadata[A]]],
                combiner,
              )
              .asInstanceOf[Chunk[HttpCodec.Metadata[Any]]],
          ) ++
            flattenedAtoms(
              right,
              HttpCodec
                .reduceExamplesRight[A, codec.Left, codec.Right](
                  annotations.asInstanceOf[Chunk[HttpCodec.Metadata[A]]],
                  combiner,
                )
                .asInstanceOf[Chunk[HttpCodec.Metadata[Any]]],
            )
        case path: HttpCodec.Path[_]                          => metaCodecFromPathCodec(path.pathCodec, annotations)
        case atom: HttpCodec.Atom[_, A]                       => Chunk(MetaCodec(atom, annotations))
        case map: HttpCodec.TransformOrFail[_, _, _]          =>
          flattenedAtoms(map.api, annotations.map(_.transformOrFail(map.g.asInstanceOf[Any => Either[String, Any]])))
        case HttpCodec.Empty                                  => Chunk.empty
        case HttpCodec.Halt                                   => Chunk.empty
        case _: HttpCodec.Fallback[_, _, _]       => in.alternatives.map(_._1).flatMap(flattenedAtoms(_, annotations))
        case HttpCodec.Annotated(api, annotation) =>
          flattenedAtoms(api, annotations :+ annotation.asInstanceOf[HttpCodec.Metadata[Any]])
      }
  }

  def method(in: Chunk[MetaCodec[SimpleCodec[Method, _]]]): Method = {
    if (in.size > 1) throw new Exception("Multiple methods not supported")
    in.collectFirst { case MetaCodec(SimpleCodec.Specified(method: Method), _) => method }
      .getOrElse(throw new Exception("No method specified"))
  }

  def metaCodecFromPathCodec(
    codec: PathCodec[_],
    annotations: Chunk[HttpCodec.Metadata[_]],
  ): Chunk[MetaCodec[SegmentCodec[_]]] = {
    def loop(
      path: PathCodec[_],
      annotations: Chunk[HttpCodec.Metadata[_]],
    ): Chunk[(SegmentCodec[_], Chunk[HttpCodec.Metadata[_]])] = path match {
      case PathCodec.Annotated(codec, newAnnotations) =>
        loop(codec, newAnnotations.map(toHttpCodecAnnotations) ++ annotations)
      case PathCodec.Segment(segment)                 => Chunk(segment -> annotations)

      case PathCodec.Concat(left, right, combiner) =>
        loop(left, HttpCodec.reduceExamplesLeft(annotations.asInstanceOf[Chunk[HttpCodec.Metadata[Any]]], combiner)) ++
          loop(right, HttpCodec.reduceExamplesRight(annotations.asInstanceOf[Chunk[HttpCodec.Metadata[Any]]], combiner))

      case codec @ PathCodec.TransformOrFail(api, _, g) =>
        loop(
          api,
          annotations.map(_.transform { v =>
            g(v.asInstanceOf[codec.Out]) match {
              case Left(error)  => throw new Exception(error)
              case Right(value) => value
            }
          }),
        )
      case PathCodec.Fallback(left, _)                  =>
        loop(left, annotations)
    }

    loop(codec, annotations).map { case (sc, annotations) =>
      MetaCodec(sc.asInstanceOf[SegmentCodec[_]], annotations.asInstanceOf[Chunk[HttpCodec.Metadata[Any]]])
    }.asInstanceOf[Chunk[MetaCodec[SegmentCodec[_]]]]
  }

  def toHttpCodecAnnotations(annotation: PathCodec.MetaData[_]): HttpCodec.Metadata[_] =
    annotation match {
      case PathCodec.MetaData.Documented(value)  => HttpCodec.Metadata.Documented(value)
      case PathCodec.MetaData.Examples(examples) => HttpCodec.Metadata.Examples(examples)
    }

  def contentAsJsonSchema[R, A](
    codec: HttpCodec[R, A],
    metadata: Chunk[HttpCodec.Metadata[_]] = Chunk.empty,
    referenceType: SchemaStyle = SchemaStyle.Inline,
    wrapInObject: Boolean = false,
    omitDescription: Boolean = false,
  )(mediaType: MediaType): JsonSchema = {
    val descriptionFromMeta = if (omitDescription) None else description(metadata)
    codec match {
      case atom: HttpCodec.Atom[_, _]                              =>
        atom match {
          case HttpCodec.Content(codec, maybeName, _) if wrapInObject       =>
            val name =
              findName(metadata).orElse(maybeName).getOrElse(throw new Exception("Multipart content without name"))
            JsonSchema.obj(
              name -> JsonSchema
                .fromZSchema(codec.lookup(mediaType).map(_.schema).getOrElse(codec.defaultSchema), referenceType)
                .description(descriptionFromMeta)
                .deprecated(deprecated(metadata))
                .nullable(optional(metadata)),
            )
          case HttpCodec.ContentStream(codec, maybeName, _)
              if wrapInObject && codec.lookup(mediaType).map(_.schema).getOrElse(codec.defaultSchema) == Schema[Byte] =>
            val name =
              findName(metadata).orElse(maybeName).getOrElse(throw new Exception("Multipart content without name"))
            JsonSchema.obj(
              name -> JsonSchema
                .fromZSchema(codec.lookup(mediaType).map(_.schema).getOrElse(codec.defaultSchema), referenceType)
                .description(descriptionFromMeta)
                .deprecated(deprecated(metadata))
                .nullable(optional(metadata))
                // currently we have no information about the encoding. So we just assume binary
                .contentEncoding(JsonSchema.ContentEncoding.Binary)
                .contentMediaType(MediaType.application.`octet-stream`.fullType),
            )
          case HttpCodec.ContentStream(codec, maybeName, _) if wrapInObject =>
            val name =
              findName(metadata).orElse(maybeName).getOrElse(throw new Exception("Multipart content without name"))
            JsonSchema.obj(
              name -> JsonSchema
                .fromZSchema(codec.lookup(mediaType).map(_.schema).getOrElse(codec.defaultSchema), referenceType)
                .description(descriptionFromMeta)
                .deprecated(deprecated(metadata))
                .nullable(optional(metadata)),
            )
          case HttpCodec.Content(codec, _, _)                               =>
            JsonSchema
              .fromZSchema(codec.lookup(mediaType).map(_.schema).getOrElse(codec.defaultSchema), referenceType)
              .description(descriptionFromMeta)
              .deprecated(deprecated(metadata))
              .nullable(optional(metadata))
          case HttpCodec.ContentStream(codec, _, _)                         =>
            JsonSchema
              .fromZSchema(codec.lookup(mediaType).map(_.schema).getOrElse(codec.defaultSchema), referenceType)
              .description(descriptionFromMeta)
              .deprecated(deprecated(metadata))
              .nullable(optional(metadata))
          case _                                                            => JsonSchema.Null
        }
      case HttpCodec.Annotated(codec, data)                        =>
        contentAsJsonSchema(codec, metadata :+ data, referenceType, wrapInObject, omitDescription)(mediaType)
      case HttpCodec.TransformOrFail(api, _, _)                    =>
        contentAsJsonSchema(api, metadata, referenceType, wrapInObject)(mediaType)
      case HttpCodec.Empty                                         => JsonSchema.Null
      case HttpCodec.Halt                                          => JsonSchema.Null
      case HttpCodec.Combine(left, right, _) if isMultipart(codec) =>
        (
          contentAsJsonSchema(left, Chunk.empty, referenceType, wrapInObject = true)(mediaType),
          contentAsJsonSchema(right, Chunk.empty, referenceType, wrapInObject = true)(mediaType),
        ) match {
          case (left, right) =>
            val annotations = left.annotations ++ right.annotations
            (left.withoutAnnotations, right.withoutAnnotations) match {
              case (JsonSchema.Object(p1, _, r1), JsonSchema.Object(p2, _, r2)) =>
                // seems odd to allow additional properties for multipart. So just hardcode it to false
                JsonSchema
                  .Object(p1 ++ p2, Left(false), r1 ++ r2)
                  .deprecated(deprecated(metadata))
                  .nullable(optional(metadata))
                  .description(descriptionFromMeta)
                  .annotate(annotations)
              case (JsonSchema.Object(p, _, r), JsonSchema.Null)                =>
                JsonSchema
                  .Object(p, Left(false), r)
                  .deprecated(deprecated(metadata))
                  .nullable(optional(metadata))
                  .description(descriptionFromMeta)
                  .annotate(annotations)
              case (JsonSchema.Null, JsonSchema.Object(p, _, r))                =>
                JsonSchema
                  .Object(p, Left(false), r)
                  .deprecated(deprecated(metadata))
                  .nullable(optional(metadata))
                  .description(descriptionFromMeta)
                  .annotate(annotations)
              case _ => throw new IllegalArgumentException("Multipart content without name.")
            }

        }
      case HttpCodec.Combine(left, right, _)                       =>
        (
          contentAsJsonSchema(left, Chunk.empty, referenceType, wrapInObject, omitDescription)(mediaType),
          contentAsJsonSchema(right, Chunk.empty, referenceType, wrapInObject, omitDescription)(mediaType),
        ) match {
          case (JsonSchema.Null, JsonSchema.Null) =>
            JsonSchema.Null
          case (JsonSchema.Null, schema)          =>
            schema
              .deprecated(deprecated(metadata))
              .nullable(optional(metadata))
              .description(descriptionFromMeta)
          case (schema, JsonSchema.Null)          =>
            schema
              .deprecated(deprecated(metadata))
              .nullable(optional(metadata))
              .description(descriptionFromMeta)
          case _                                  =>
            throw new IllegalStateException("A non multipart combine, should lead to at least one null schema.")
        }
      case HttpCodec.Fallback(_, _, _, _) => throw new IllegalArgumentException("Fallback not supported at this point")
    }
  }

  private def findName(metadata: Chunk[HttpCodec.Metadata[_]]): Option[String] =
    metadata.reverse
      .find(_.isInstanceOf[Metadata.Named[_]])
      .asInstanceOf[Option[Metadata.Named[Any]]]
      .map(_.name)

  private def description(metadata: Chunk[HttpCodec.Metadata[_]]): Option[String] =
    metadata.collect { case HttpCodec.Metadata.Documented(doc) => doc }
      .reduceOption(_ + _)
      .map(_.toCommonMark)

  private def deprecated(metadata: Chunk[HttpCodec.Metadata[_]]): Boolean =
    metadata.exists(_.isInstanceOf[HttpCodec.Metadata.Deprecated[_]])

  private def optional(metadata: Chunk[HttpCodec.Metadata[_]]): Boolean =
    metadata.exists(_.isInstanceOf[HttpCodec.Metadata.Optional[_]])

  def status[R, A](codec: HttpCodec[R, A]): Option[Status] =
    codec match {
      case HttpCodec.Status(simpleCodec, _) if simpleCodec.isInstanceOf[SimpleCodec.Specified[_]] =>
        Some(simpleCodec.asInstanceOf[SimpleCodec.Specified[Status]].value)
      case HttpCodec.Annotated(codec, _)                                                          =>
        status(codec)
      case HttpCodec.TransformOrFail(api, _, _)                                                   =>
        status(api)
      case HttpCodec.Empty                                                                        =>
        None
      case HttpCodec.Halt                                                                         =>
        None
      case HttpCodec.Combine(left, right, _)                                                      =>
        status(left).orElse(status(right))
      case HttpCodec.Fallback(left, right, _, _)                                                  =>
        status(left).orElse(status(right))
      case _                                                                                      =>
        None
    }

  def isMultipart[R, A](codec: HttpCodec[R, A]): Boolean =
    codec match {
      case HttpCodec.Combine(left, right, _)      =>
        (isContent(left) && isContent(right)) ||
        isMultipart(left) || isMultipart(right)
      case HttpCodec.Annotated(codec, _)          => isMultipart(codec)
      case HttpCodec.TransformOrFail(codec, _, _) => isMultipart(codec)
      case _                                      => false
    }

  def isContent(value: HttpCodec[_, _]): Boolean =
    value match {
      case HttpCodec.Content(_, _, _)             => true
      case HttpCodec.ContentStream(_, _, _)       => true
      case HttpCodec.Annotated(codec, _)          => isContent(codec)
      case HttpCodec.TransformOrFail(codec, _, _) => isContent(codec)
      case HttpCodec.Combine(left, right, _)      => isContent(left) || isContent(right)
      case _                                      => false
    }

  private def toJsonAst(schema: Schema[_], v: Any): Json =
    JsonCodec
      .jsonEncoder(schema.asInstanceOf[Schema[Any]])
      .toJsonAST(v)
      .toOption
      .get

  def fromEndpoints(
    endpoint1: Endpoint[_, _, _, _, _],
    endpoints: Endpoint[_, _, _, _, _]*,
  ): OpenAPI = fromEndpoints(endpoint1 +: endpoints)

  def fromEndpoints(
    title: String,
    version: String,
    endpoint1: Endpoint[_, _, _, _, _],
    endpoints: Endpoint[_, _, _, _, _]*,
  ): OpenAPI = fromEndpoints(title, version, endpoint1 +: endpoints)

  def fromEndpoints(
    title: String,
    version: String,
    referenceType: SchemaStyle,
    endpoint1: Endpoint[_, _, _, _, _],
    endpoints: Endpoint[_, _, _, _, _]*,
  ): OpenAPI = fromEndpoints(title, version, referenceType, endpoint1 +: endpoints)

  def fromEndpoints(
    referenceType: SchemaStyle,
    endpoints: Iterable[Endpoint[_, _, _, _, _]],
  ): OpenAPI = if (endpoints.isEmpty) OpenAPI.empty else endpoints.map(gen(_, referenceType)).reduce(_ ++ _)

  def fromEndpoints(
    endpoints: Iterable[Endpoint[_, _, _, _, _]],
  ): OpenAPI = if (endpoints.isEmpty) OpenAPI.empty else endpoints.map(gen(_, SchemaStyle.Compact)).reduce(_ ++ _)

  def fromEndpoints(
    title: String,
    version: String,
    endpoints: Iterable[Endpoint[_, _, _, _, _]],
  ): OpenAPI = fromEndpoints(endpoints).title(title).version(version)

  def fromEndpoints(
    title: String,
    version: String,
    referenceType: SchemaStyle,
    endpoints: Iterable[Endpoint[_, _, _, _, _]],
  ): OpenAPI = fromEndpoints(referenceType, endpoints).title(title).version(version)

  def gen(
    endpoint: Endpoint[_, _, _, _, _],
    referenceType: SchemaStyle = SchemaStyle.Compact,
  ): OpenAPI = {
    val inAtoms = AtomizedMetaCodecs.flatten(endpoint.input)
    val outs: Map[OpenAPI.StatusOrDefault, Map[MediaType, (JsonSchema, AtomizedMetaCodecs)]] =
      schemaByStatusAndMediaType(
        endpoint.output.alternatives.map(_._1) ++ endpoint.error.alternatives.map(_._1),
        referenceType,
        omitContentDescription = true,
      )
    // there is no status for inputs. So we just take the first one (default)
    val ins = schemaByStatusAndMediaType(endpoint.input.alternatives.map(_._1), referenceType).values.headOption

    def path: Map[Path, PathItem] = {
      val path           = buildPath(endpoint.input)
      val method0        = method(inAtoms.method)
      // Endpoint has only one doc. But open api has a summery and a description
      val pathItem       = OpenAPI.PathItem.empty
        .copy(description = Some(endpoint.documentation + endpoint.input.doc.getOrElse(Doc.empty)).filter(!_.isEmpty))
      val pathItemWithOp = method0 match {
        case Method.OPTIONS => pathItem.addOptions(operation(endpoint))
        case Method.GET     => pathItem.addGet(operation(endpoint))
        case Method.HEAD    => pathItem.addHead(operation(endpoint))
        case Method.POST    => pathItem.addPost(operation(endpoint))
        case Method.PUT     => pathItem.addPut(operation(endpoint))
        case Method.PATCH   => pathItem.addPatch(operation(endpoint))
        case Method.DELETE  => pathItem.addDelete(operation(endpoint))
        case Method.TRACE   => pathItem.addTrace(operation(endpoint))
        case Method.ANY     => pathItem.any(operation(endpoint))
        case method         => throw new IllegalArgumentException(s"OpenAPI does not support method $method")
      }
      Map(path -> pathItemWithOp)
    }

    def buildPath(in: HttpCodec[_, _]): OpenAPI.Path = {

      def pathCodec(in1: HttpCodec[_, _]): Option[HttpCodec.Path[_]] = in1 match {
        case atom: HttpCodec.Atom[_, _]            =>
          atom match {
            case codec @ HttpCodec.Path(_, _) => Some(codec)
            case _                            => None
          }
        case HttpCodec.Annotated(in, _)            => pathCodec(in)
        case HttpCodec.TransformOrFail(api, _, _)  => pathCodec(api)
        case HttpCodec.Empty                       => None
        case HttpCodec.Halt                        => None
        case HttpCodec.Combine(left, right, _)     => pathCodec(left).orElse(pathCodec(right))
        case HttpCodec.Fallback(left, right, _, _) => pathCodec(left).orElse(pathCodec(right))
      }

      val pathString = {
        val codec = pathCodec(in).getOrElse(throw new Exception("No path found.")).pathCodec
        if (codec.render.endsWith(SegmentCodec.Trailing.render))
          codec.renderIgnoreTrailing + s"{$PathWildcard}"
        else codec.render
      }
      OpenAPI.Path.fromString(pathString).getOrElse(throw new Exception(s"Invalid path: $pathString"))
    }

    def operation(endpoint: Endpoint[_, _, _, _, _]): OpenAPI.Operation = {
      val maybeDoc = Some(endpoint.documentation + pathDoc).filter(!_.isEmpty)
      OpenAPI.Operation(
        tags = endpoint.tags,
        summary = None,
        description = maybeDoc,
        externalDocs = None,
        operationId = None,
        parameters = parameters,
        requestBody = requestBody,
        responses = responses,
        callbacks = Map.empty,
        security = Nil,
        servers = Nil,
      )
    }

    def pathDoc: Doc =
      inAtoms.path
        .flatMap(_.docsOpt)
        .map(_.flattened)
        .reduceOption(_ intersect _)
        .flatMap(_.reduceOption(_ + _))
        .getOrElse(Doc.empty)

    def requestBody: Option[OpenAPI.ReferenceOr[OpenAPI.RequestBody]] =
      ins.map { mediaTypes =>
        val combinedAtomizedCodecs = mediaTypes.map { case (_, (_, atomized)) => atomized }.reduce(_ ++ _)
        val mediaTypeResponses     = mediaTypes.map { case (mediaType, (schema, atomized)) =>
          mediaType.fullType -> OpenAPI.MediaType(
            schema = OpenAPI.ReferenceOr.Or(schema),
            examples = atomized.contentExamples,
            encoding = Map.empty,
          )
        }
        OpenAPI.ReferenceOr.Or(
          OpenAPI.RequestBody(
            content = mediaTypeResponses,
            required = combinedAtomizedCodecs.content.exists(_.required),
          ),
        )
      }.filter(_.value.content.exists {
        case (_, OpenAPI.MediaType(OpenAPI.ReferenceOr.Or(schema), _, _)) =>
          schema.withoutAnnotations != JsonSchema.Null
        case _                                                            => true
      })

    def responses: OpenAPI.Responses =
      responsesForAlternatives(outs)

    def parameters: Set[OpenAPI.ReferenceOr[OpenAPI.Parameter]] =
      queryParams ++ pathParams ++ headerParams

    def queryParams: Set[OpenAPI.ReferenceOr[OpenAPI.Parameter]] = {
      inAtoms.query.collect {
        case mc @ MetaCodec(HttpCodec.Query(HttpCodec.Query.QueryType.Primitive(name, codec), _), _)  =>
          OpenAPI.ReferenceOr.Or(
            OpenAPI.Parameter.queryParameter(
              name = name,
              description = mc.docsOpt,
              schema = Some(OpenAPI.ReferenceOr.Or(JsonSchema.fromZSchema(codec.schema))),
              deprecated = mc.deprecated,
              style = OpenAPI.Parameter.Style.Form,
              explode = false,
              allowReserved = false,
              examples = mc.examples.map { case (name, value) =>
                name -> OpenAPI.ReferenceOr.Or(OpenAPI.Example(value = Json.Str(value.toString)))
              },
              required = mc.required,
            ),
          ) :: Nil
        case mc @ MetaCodec(HttpCodec.Query(record @ HttpCodec.Query.QueryType.Record(schema), _), _) =>
          val recordSchema = (schema match {
            case schema if schema.isInstanceOf[Schema.Optional[_]] => schema.asInstanceOf[Schema.Optional[_]].schema
            case _                                                 => schema
          }).asInstanceOf[Schema.Record[Any]]
          val examples     = mc.examples.map { case (exName, ex) =>
            exName -> recordSchema.deconstruct(ex)(Unsafe.unsafe)
          }
          record.fieldAndCodecs.zipWithIndex.map { case ((field, codec), index) =>
            OpenAPI.ReferenceOr.Or(
              OpenAPI.Parameter.queryParameter(
                name = field.name,
                description = mc.docsOpt,
                schema = Some(OpenAPI.ReferenceOr.Or(JsonSchema.fromZSchema(codec.schema))),
                deprecated = mc.deprecated,
                style = OpenAPI.Parameter.Style.Form,
                explode = false,
                allowReserved = false,
                examples = examples.map { case (exName, values) =>
                  val fieldValue = values(index)
                    .orElse(field.defaultValue)
                    .getOrElse(
                      throw new Exception(s"No value or default value found for field ${exName}_${field.name}"),
                    )
                  s"${exName}_${field.name}" -> OpenAPI.ReferenceOr.Or(
                    OpenAPI.Example(value =
                      Json.Str(codec.codec(CodecConfig.defaultConfig).encode(fieldValue).asString),
                    ),
                  )
                },
                required = mc.required,
              ),
            )

          }
        case mc @ MetaCodec(
              HttpCodec.Query(
                HttpCodec.Query.QueryType.Collection(
                  _,
                  HttpCodec.Query.QueryType.Primitive(name, codec),
                  optional,
                ),
                _,
              ),
              _,
            ) =>
          OpenAPI.ReferenceOr.Or(
            OpenAPI.Parameter.queryParameter(
              name = name,
              description = mc.docsOpt,
              schema = Some(OpenAPI.ReferenceOr.Or(JsonSchema.fromZSchema(codec.schema))),
              deprecated = mc.deprecated,
              style = OpenAPI.Parameter.Style.Form,
              explode = false,
              allowReserved = false,
              examples = mc.examples.map { case (exName, value) =>
                exName -> OpenAPI.ReferenceOr.Or(OpenAPI.Example(value = Json.Str(value.toString)))
              },
              required = !optional,
            ),
          ) :: Nil
      }
    }.flatten.toSet

    def pathParams: Set[OpenAPI.ReferenceOr[OpenAPI.Parameter]] =
      inAtoms.path.collect {
        case mc @ MetaCodec(codec, _) if codec != SegmentCodec.Empty && !codec.isInstanceOf[SegmentCodec.Literal] =>
          OpenAPI.ReferenceOr.Or(
            OpenAPI.Parameter.pathParameter(
              name = mc.name.getOrElse(throw new Exception("Path parameter must have a name")),
              description = mc.docsOpt.flatMap(_.flattened.filterNot(_ == pathDoc).reduceOption(_ + _)),
              definition = Some(OpenAPI.ReferenceOr.Or(JsonSchema.fromSegmentCodec(codec))),
              deprecated = mc.deprecated,
              style = OpenAPI.Parameter.Style.Simple,
              examples = mc.examples.map { case (name, value) =>
                name -> OpenAPI.ReferenceOr.Or(OpenAPI.Example(segmentToJson(codec, value)))
              },
            ),
          )
      }.toSet

    def headerParams: Set[OpenAPI.ReferenceOr[OpenAPI.Parameter]] =
      inAtoms.header
        .asInstanceOf[Chunk[MetaCodec[HttpCodec.Header[Any]]]]
        .map { case mc @ MetaCodec(codec, _) =>
          OpenAPI.ReferenceOr.Or(
            OpenAPI.Parameter.headerParameter(
              name = mc.name.getOrElse(codec.name),
              description = mc.docsOpt,
              definition = Some(OpenAPI.ReferenceOr.Or(JsonSchema.fromTextCodec(codec.textCodec))),
              deprecated = mc.deprecated,
              examples = mc.examples.map { case (name, value) =>
                name -> OpenAPI.ReferenceOr.Or(OpenAPI.Example(codec.textCodec.encode(value).toJsonAST.toOption.get))
              },
              required = mc.required,
            ),
          )
        }
        .toSet

    def genDiscriminator(schema: Schema[_]): Option[OpenAPI.Discriminator] = {
      schema match {
        case enumSchema: Schema.Enum[_] =>
          val discriminatorName =
            enumSchema.annotations.collectFirst { case zio.schema.annotation.discriminatorName(name) => name }
          val noDiscriminator   = enumSchema.annotations.contains(zio.schema.annotation.noDiscriminator())
          val typeMapping       = enumSchema.cases.map { case_ =>
            val caseName =
              case_.annotations.collectFirst { case zio.schema.annotation.caseName(name) => name }.getOrElse(case_.id)
            // There should be no enums with cases that are not records with a nominal id
            // TODO: not true. Since one could build a schema with a enum with a case that is a primitive
            val typeId   =
              (case_.schema match {
                case lzy: Schema.Lazy[_] => lzy.schema
                case _                   => case_.schema
              })
                .asInstanceOf[Schema.Record[_]]
                .id
                .asInstanceOf[TypeId.Nominal]
            caseName -> schemaReferencePath(typeId, referenceType)
          }

          if (noDiscriminator) None
          else discriminatorName.map(name => OpenAPI.Discriminator(name, typeMapping.toMap))

        case _ => None
      }
    }

    def components = OpenAPI.Components(
      schemas = ListMap(componentSchemas.toSeq.sortBy(_._1.name): _*),
      responses = ListMap.empty,
      parameters = ListMap.empty,
      examples = ListMap.empty,
      requestBodies = ListMap.empty,
      headers = ListMap.empty,
      securitySchemes = ListMap.empty,
      links = ListMap.empty,
      callbacks = ListMap.empty,
    )

    def segmentToJson(codec: SegmentCodec[_], value: Any): Json = {
      codec match {
        case SegmentCodec.Empty             => throw new Exception("Empty segment not allowed")
        case SegmentCodec.Literal(_)        => throw new Exception("Literal segment not allowed")
        case SegmentCodec.BoolSeg(_)        => Json.Bool(value.asInstanceOf[Boolean])
        case SegmentCodec.IntSeg(_)         => Json.Num(value.asInstanceOf[Int])
        case SegmentCodec.LongSeg(_)        => Json.Num(value.asInstanceOf[Long])
        case SegmentCodec.Text(_)           => Json.Str(value.asInstanceOf[String])
        case SegmentCodec.UUID(_)           => Json.Str(value.asInstanceOf[UUID].toString)
        case SegmentCodec.Trailing          => throw new Exception("Trailing segment not allowed")
        case SegmentCodec.Combined(_, _, _) => throw new Exception("Combined segment not allowed")
      }
    }

    def jsonSchemaFromCodec(codec: HttpContentCodec[_]): Option[Schema[_]] =
      codec.lookup(MediaType.application.json).map(_.schema)

    def componentSchemas: Map[OpenAPI.Key, OpenAPI.ReferenceOr[JsonSchema]] =
      (endpoint.input.alternatives.map(_._1).map(AtomizedMetaCodecs.flatten(_)).flatMap(_.content)
        ++ endpoint.error.alternatives.map(_._1).map(AtomizedMetaCodecs.flatten(_)).flatMap(_.content)
        ++ endpoint.output.alternatives.map(_._1).map(AtomizedMetaCodecs.flatten(_)).flatMap(_.content)).collect {
        case MetaCodec(HttpCodec.Content(codec, _, _), _)
            if jsonSchemaFromCodec(codec).isDefined &&
              nominal(jsonSchemaFromCodec(codec).get, referenceType).isDefined =>
          val schemas = JsonSchema.fromZSchemaMulti(jsonSchemaFromCodec(codec).get, referenceType)
          schemas.children.map { case (key, schema) =>
            OpenAPI.Key.fromString(key.replace("#/components/schemas/", "")).get -> OpenAPI.ReferenceOr.Or(schema)
          } + (OpenAPI.Key.fromString(nominal(jsonSchemaFromCodec(codec).get, referenceType).get).get ->
            OpenAPI.ReferenceOr.Or(schemas.root.discriminator(genDiscriminator(jsonSchemaFromCodec(codec).get))))

        case MetaCodec(HttpCodec.Content(setCodec, _, _), _)
            if jsonSchemaFromCodec(setCodec).isDefined && jsonSchemaFromCodec(setCodec).get.isInstanceOf[Schema.Set[_]]
              && nominal(
                jsonSchemaFromCodec(setCodec).get.asInstanceOf[Schema.Set[_]].elementSchema,
                referenceType,
              ).isDefined =>
          val schema  = jsonSchemaFromCodec(setCodec).get.asInstanceOf[Schema.Set[_]].elementSchema
          val schemas = JsonSchema.fromZSchemaMulti(schema, referenceType)
          schemas.children.map { case (key, schema) =>
            OpenAPI.Key.fromString(key.replace("#/components/schemas/", "")).get -> OpenAPI.ReferenceOr.Or(schema)
          } + (OpenAPI.Key.fromString(nominal(schema, referenceType).get).get ->
            OpenAPI.ReferenceOr.Or(schemas.root.discriminator(genDiscriminator(schema))))

        case MetaCodec(HttpCodec.Content(seqCodec, _, _), _)
            if jsonSchemaFromCodec(seqCodec).isDefined && jsonSchemaFromCodec(seqCodec).get
              .isInstanceOf[Schema.Sequence[_, _, _]]
              && nominal(
                jsonSchemaFromCodec(seqCodec).get.asInstanceOf[Schema.Sequence[_, _, _]].elementSchema,
                referenceType,
              ).isDefined =>
          val schema  = jsonSchemaFromCodec(seqCodec).get.asInstanceOf[Schema.Sequence[_, _, _]].elementSchema
          val schemas = JsonSchema.fromZSchemaMulti(schema, referenceType)
          schemas.children.map { case (key, schema) =>
            OpenAPI.Key.fromString(key.replace("#/components/schemas/", "")).get -> OpenAPI.ReferenceOr.Or(schema)
          } + (OpenAPI.Key.fromString(nominal(schema, referenceType).get).get ->
            OpenAPI.ReferenceOr.Or(schemas.root.discriminator(genDiscriminator(schema))))

        case MetaCodec(HttpCodec.Content(mapCodec, _, _), _)
            if jsonSchemaFromCodec(mapCodec).isDefined && jsonSchemaFromCodec(mapCodec).get
              .isInstanceOf[Schema.Map[_, _]]
              && nominal(
                jsonSchemaFromCodec(mapCodec).get.asInstanceOf[Schema.Map[_, _]].valueSchema,
                referenceType,
              ).isDefined =>
          val schema  = jsonSchemaFromCodec(mapCodec).get.asInstanceOf[Schema.Map[_, _]].valueSchema
          val schemas = JsonSchema.fromZSchemaMulti(schema, referenceType)
          schemas.children.map { case (key, schema) =>
            OpenAPI.Key.fromString(key.replace("#/components/schemas/", "")).get -> OpenAPI.ReferenceOr.Or(schema)
          } + (OpenAPI.Key.fromString(nominal(schema, referenceType).get).get ->
            OpenAPI.ReferenceOr.Or(schemas.root.discriminator(genDiscriminator(schema))))

        case MetaCodec(HttpCodec.ContentStream(codec, _, _), _)
            if jsonSchemaFromCodec(codec).isDefined && nominal(
              jsonSchemaFromCodec(codec).get,
              referenceType,
            ).isDefined =>
          val schemas = JsonSchema.fromZSchemaMulti(jsonSchemaFromCodec(codec).get, referenceType)
          schemas.children.map { case (key, schema) =>
            OpenAPI.Key.fromString(key.replace("#/components/schemas/", "")).get -> OpenAPI.ReferenceOr.Or(schema)
          } + (OpenAPI.Key.fromString(nominal(jsonSchemaFromCodec(codec).get, referenceType).get).get ->
            OpenAPI.ReferenceOr.Or(schemas.root.discriminator(genDiscriminator(jsonSchemaFromCodec(codec).get))))
      }.flatten.toMap

    OpenAPI(
      "3.1.0",
      info = OpenAPI.Info(
        title = "",
        description = None,
        termsOfService = None,
        contact = None,
        license = None,
        version = "",
      ),
      servers = Nil,
      paths = ListMap(path.toSeq.sortBy(_._1.name): _*),
      components = Some(components),
      security = Nil,
      tags = Nil,
      externalDocs = None,
    )
  }

  private def schemaByStatusAndMediaType(
    alternatives: Chunk[HttpCodec[_, _]],
    referenceType: SchemaStyle,
    omitContentDescription: Boolean = false,
  ): Map[OpenAPI.StatusOrDefault, Map[MediaType, (JsonSchema, AtomizedMetaCodecs)]] = {
    val statusAndCodec =
      alternatives.map { codec =>
        val statusOrDefault =
          status(codec).map(OpenAPI.StatusOrDefault.StatusValue(_)).getOrElse(OpenAPI.StatusOrDefault.Default)
        (
          statusOrDefault,
          (
            AtomizedMetaCodecs.flatten(codec),
            contentAsJsonSchema(codec, referenceType = referenceType, omitDescription = omitContentDescription) _,
          ),
        )
      }

    groupMap(statusAndCodec) { case (status, _) => status } { case (_, atomizedAndSchema) =>
      atomizedAndSchema
    }.map { case (status, values) =>
      val mapped = values
        .foldLeft(Chunk.empty[(MediaType, (AtomizedMetaCodecs, JsonSchema))]) { case (acc, (atomized, schema)) =>
          if (atomized.content.size > 1) {
            acc :+ ((MediaType.multipart.`form-data`, (atomized, schema(MediaType.multipart.`form-data`))))
          } else {
            val mediaType = atomized.content.headOption match {
              case Some(MetaCodec(HttpCodec.Content(codec, _, _), _))       =>
                codec.defaultMediaType
              case Some(MetaCodec(HttpCodec.ContentStream(codec, _, _), _)) =>
                if (codec.defaultSchema == Schema[Byte]) MediaType.application.`octet-stream`
                else codec.defaultMediaType
              case _                                                        =>
                MediaType.application.`json`
            }
            acc :+ ((mediaType, (atomized, schema(mediaType))))
          }
        }
      status -> groupMap(mapped) { case (mediaType, _) => mediaType } { case (_, atomizedAndSchema) =>
        atomizedAndSchema
      }.map {
        case (mediaType, Chunk((atomized, schema))) if values.size == 1 =>
          (mediaType, (schema, atomized))
        case (mediaType, values)                                        =>
          val combinedAtomized: AtomizedMetaCodecs = values.map(_._1).reduce(_ ++ _)
          val combinedContentDoc                   = combinedAtomized.contentDocs.toCommonMark
          val alternativesSchema                   = {
            JsonSchema
              .AnyOfSchema(values.map { case (_, schema) =>
                schema.description match {
                  case Some(value) => schema.description(value.replace(combinedContentDoc, ""))
                  case None        => schema
                }
              })
              .minify
              .description(combinedContentDoc)
          }
          (mediaType, (alternativesSchema, combinedAtomized))
      }
    }
  }

  def nominal(schema: Schema[_], referenceType: SchemaStyle): Option[String] =
    schema match {
      case enumSchema: Schema.Enum[_] =>
        enumSchema.id match {
          case TypeId.Structural                                               =>
            None
          case nominal: TypeId.Nominal if referenceType == SchemaStyle.Compact =>
            Some(nominal.typeName)
          case nominal: TypeId.Nominal                                         =>
            Some(nominal.fullyQualified.replace(".", "_"))
        }
      case record: Record[_]          =>
        record.id match {
          case TypeId.Structural                                               =>
            None
          case nominal: TypeId.Nominal if referenceType == SchemaStyle.Compact =>
            Some(nominal.typeName)
          case nominal: TypeId.Nominal                                         =>
            Some(nominal.fullyQualified.replace(".", "_"))
        }
      case _                          => None
    }

  private def responsesForAlternatives(
    codecs: Map[OpenAPI.StatusOrDefault, Map[MediaType, (JsonSchema, AtomizedMetaCodecs)]],
  ): Map[OpenAPI.StatusOrDefault, OpenAPI.ReferenceOr[OpenAPI.Response]] =
    codecs.map { case (status, mediaTypes) =>
      val combinedAtomizedCodecs = mediaTypes.map { case (_, (_, atomized)) => atomized }.reduce(_ ++ _)
      val mediaTypeResponses     = mediaTypes.map { case (mediaType, (schema, atomized)) =>
        mediaType.fullType -> OpenAPI.MediaType(
          schema = OpenAPI.ReferenceOr.Or(schema),
          examples = atomized.contentExamples,
          encoding = Map.empty,
        )
      }
      status -> OpenAPI.ReferenceOr.Or(
        OpenAPI.Response(
          description = combinedAtomizedCodecs.status.headOption.flatMap(_.docsOpt),
          headers = headersFrom(combinedAtomizedCodecs),
          content = mediaTypeResponses,
          links = Map.empty,
        ),
      )
    }

  private def headersFrom(codec: AtomizedMetaCodecs)                                           = {
    codec.header.map { case mc @ MetaCodec(codec, _) =>
      codec.name -> OpenAPI.ReferenceOr.Or(
        OpenAPI.Header(
          description = mc.docsOpt,
          required = true,
          deprecated = mc.deprecated,
          allowEmptyValue = false,
          schema = Some(JsonSchema.fromTextCodec(codec.textCodec)),
        ),
      )
    }.toMap
  }
  private def schemaReferencePath(nominal: TypeId.Nominal, referenceType: SchemaStyle): String = {
    referenceType match {
      case SchemaStyle.Compact => s"#/components/schemas/${nominal.typeName}"
      case _                   => s"#/components/schemas/${nominal.fullyQualified.replace(".", "_")}}"
    }
  }
}
