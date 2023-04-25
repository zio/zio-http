package zio.http.endpoint.openapi

import java.util.UUID

import zio.Chunk
import zio.json.EncoderOps
import zio.json.ast.Json

import zio.schema.Schema.Record
import zio.schema.codec.JsonCodec
import zio.schema.{Schema, TypeId}

import zio.http._
import zio.http.codec.HttpCodec.Metadata
import zio.http.codec._
import zio.http.endpoint.openapi.JsonSchema.ReferenceType
import zio.http.endpoint.{Endpoint, EndpointMiddleware}

object OpenAPIGen {
  private val PathWildcard = "pathWildcard"
  final case class MetaCodec[T](codec: T, annotations: Chunk[HttpCodec.Metadata[Any]]) {
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
    query: Chunk[MetaCodec[HttpCodec.Query[_]]],
    header: Chunk[MetaCodec[HttpCodec.Header[_]]],
    content: Chunk[MetaCodec[HttpCodec.Atom[HttpCodecType.Content, _]]],
    status: Chunk[MetaCodec[HttpCodec.Status[_]]],
  ) {
    def append(metaCodec: MetaCodec[_]): AtomizedMetaCodecs = metaCodec match {
      case MetaCodec(codec: HttpCodec.Method[_], annotations)        =>
        copy(method = method :+ MetaCodec(codec.codec, annotations))
      case MetaCodec(_: SegmentCodec[_], _)                          =>
        copy(path = path :+ metaCodec.asInstanceOf[MetaCodec[SegmentCodec[_]]])
      case MetaCodec(_: HttpCodec.Query[_], _)                       =>
        copy(query = query :+ metaCodec.asInstanceOf[MetaCodec[HttpCodec.Query[_]]])
      case MetaCodec(_: HttpCodec.Header[_], _)                      =>
        copy(header = header :+ metaCodec.asInstanceOf[MetaCodec[HttpCodec.Header[_]]])
      case MetaCodec(_: HttpCodec.Status[_], _)                      =>
        copy(status = status :+ metaCodec.asInstanceOf[MetaCodec[HttpCodec.Status[_]]])
      case MetaCodec(_: HttpCodec.Atom[HttpCodecType.Content, _], _) =>
        copy(content = content :+ metaCodec.asInstanceOf[MetaCodec[HttpCodec.Atom[HttpCodecType.Content, _]]])
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
        case mc @ MetaCodec(HttpCodec.Content(schema, _, _, _), _)       =>
          mc.examples.map { case (name, value) =>
            name -> OpenAPI.ReferenceOr.Or(OpenAPI.Example(toJsonAst(schema, value)))
          }
        case mc @ MetaCodec(HttpCodec.ContentStream(schema, _, _, _), _) =>
          mc.examples.map { case (name, value) =>
            name -> OpenAPI.ReferenceOr.Or(OpenAPI.Example(toJsonAst(schema, value)))
          }
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
        .map(_.reduce(_ + _))
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
        case HttpCodec.Combine(left, right, _)       =>
          flattenedAtoms(left, annotations) ++ flattenedAtoms(right, annotations)
        case path: HttpCodec.Path[_]                 => path.pathCodec.segments.map(metaCodecFromSegment)
        case atom: HttpCodec.Atom[_, _]              => Chunk(MetaCodec(atom, annotations))
        case map: HttpCodec.TransformOrFail[_, _, _] => flattenedAtoms(map.api, annotations)
        case HttpCodec.Empty                         => Chunk.empty
        case HttpCodec.Halt                          => Chunk.empty
        case _: HttpCodec.Fallback[_, _, _]          => in.alternatives.flatMap(flattenedAtoms(_, annotations))
        case HttpCodec.Annotated(api, annotation)    =>
          flattenedAtoms(api, annotations :+ annotation.asInstanceOf[HttpCodec.Metadata[Any]])
      }
  }

  private def metaCodecFromSegment(segment: SegmentCodec[_]) = {
    segment match {
      case SegmentCodec.Annotated(codec, annotations) =>
        MetaCodec(
          codec,
          annotations.map {
            case SegmentCodec.MetaData.Documented(value)  => HttpCodec.Metadata.Documented(value)
            case SegmentCodec.MetaData.Examples(examples) => HttpCodec.Metadata.Examples(examples)
          },
        )
      case other                                      => MetaCodec(other, Chunk.empty)
    }
  }

  def contentAsJsonSchema[R, A](
    codec: HttpCodec[R, A],
    metadata: Chunk[HttpCodec.Metadata[_]] = Chunk.empty,
    referenceType: ReferenceType = ReferenceType.Inline,
    wrapInObject: Boolean = false,
  ): JsonSchema = {
    codec match {
      case atom: HttpCodec.Atom[_, _]           =>
        atom match {
          case HttpCodec.Content(schema, _, maybeName, _) if wrapInObject                                 =>
            val name =
              findName(metadata).orElse(maybeName).getOrElse(throw new Exception("Multipart content without name"))
            JsonSchema.obj(
              name -> JsonSchema
                .fromZSchema(schema, referenceType)
                .description(description(metadata))
                .deprecated(deprecated(metadata))
                .nullable(optional(metadata)),
            )
          case HttpCodec.ContentStream(schema, _, maybeName, _) if wrapInObject && schema == Schema[Byte] =>
            val name =
              findName(metadata).orElse(maybeName).getOrElse(throw new Exception("Multipart content without name"))
            JsonSchema.obj(
              name -> JsonSchema
                .fromZSchema(schema, referenceType)
                .description(description(metadata))
                .deprecated(deprecated(metadata))
                .nullable(optional(metadata))
                // currently we have no information about the encoding. So we just assume binary
                .contentEncoding(JsonSchema.ContentEncoding.Binary)
                .contentMediaType(MediaType.application.`octet-stream`.fullType),
            )
          case HttpCodec.ContentStream(schema, _, maybeName, _) if wrapInObject                           =>
            val name =
              findName(metadata).orElse(maybeName).getOrElse(throw new Exception("Multipart content without name"))
            JsonSchema.obj(
              name -> JsonSchema
                .fromZSchema(schema, referenceType)
                .description(description(metadata))
                .deprecated(deprecated(metadata))
                .nullable(optional(metadata)),
            )
          case HttpCodec.Content(schema, _, _, _)                                                         =>
            JsonSchema
              .fromZSchema(schema, referenceType)
              .description(description(metadata))
              .deprecated(deprecated(metadata))
              .nullable(optional(metadata))
          case HttpCodec.ContentStream(schema, _, _, _)                                                   =>
            JsonSchema
              .fromZSchema(schema, referenceType)
              .description(description(metadata))
              .deprecated(deprecated(metadata))
              .nullable(optional(metadata))
          case _ => JsonSchema.Null
        }
      case HttpCodec.Annotated(codec, data)     =>
        contentAsJsonSchema(codec, metadata :+ data, referenceType, wrapInObject)
      case HttpCodec.TransformOrFail(api, _, _) => contentAsJsonSchema(api, metadata, referenceType, wrapInObject)
      case HttpCodec.Empty                      => JsonSchema.Null
      case HttpCodec.Halt                       => JsonSchema.Null
      case HttpCodec.Combine(left, right, _) if isMultipart(codec) =>
        (
          contentAsJsonSchema(left, Chunk.empty, referenceType, wrapInObject = true),
          contentAsJsonSchema(right, Chunk.empty, referenceType, wrapInObject = true),
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
                  .description(description(metadata))
                  .annotate(annotations)

            }

        }
      case HttpCodec.Combine(left, right, _)                       =>
        (
          contentAsJsonSchema(left, Chunk.empty, referenceType, wrapInObject),
          contentAsJsonSchema(right, Chunk.empty, referenceType, wrapInObject),
        ) match {
          case (JsonSchema.Null, JsonSchema.Null) =>
            JsonSchema.Null
          case (JsonSchema.Null, schema)          =>
            schema
              .deprecated(deprecated(metadata))
              .nullable(optional(metadata))
              .description(description(metadata))
          case (schema, JsonSchema.Null)          =>
            schema
              .deprecated(deprecated(metadata))
              .nullable(optional(metadata))
              .description(description(metadata))
        }
      case HttpCodec.Fallback(_, _) => throw new IllegalArgumentException("Fallback not supported at this point")
    }
  }

  private def findName(metadata: Chunk[HttpCodec.Metadata[_]]): Option[String] =
    metadata
      .findLast(_.isInstanceOf[Metadata.Named[_]])
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
      case HttpCodec.Status(simpleCodec, _) if simpleCodec.isInstanceOf[SimpleCodec.Specified[Status]] =>
        Some(simpleCodec.asInstanceOf[SimpleCodec.Specified[Status]].value)
      case HttpCodec.Annotated(codec, _)                                                               =>
        status(codec)
      case HttpCodec.TransformOrFail(api, _, _)                                                        =>
        status(api)
      case HttpCodec.Empty                                                                             =>
        None
      case HttpCodec.Halt                                                                              =>
        None
      case HttpCodec.Combine(left, right, _)                                                           =>
        status(left).orElse(status(right))
      case HttpCodec.Fallback(left, right)                                                             =>
        status(left).orElse(status(right))
      case _                                                                                           =>
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
      case HttpCodec.Content(_, _, _, _)          => true
      case HttpCodec.ContentStream(_, _, _, _)    => true
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

  def fromEndpoints[PathInput, Input, Err, Output, Middleware <: EndpointMiddleware](
    endpoint1: Endpoint[PathInput, Input, Err, Output, Middleware],
    endpoints: Endpoint[PathInput, Input, Err, Output, Middleware]*,
  ): OpenAPI = fromEndpoints(endpoint1 +: endpoints)

  def fromEndpoints[PathInput, Input, Err, Output, Middleware <: EndpointMiddleware](
    title: String,
    version: String,
    endpoint1: Endpoint[PathInput, Input, Err, Output, Middleware],
    endpoints: Endpoint[PathInput, Input, Err, Output, Middleware]*,
  ): OpenAPI = fromEndpoints(title, version, endpoint1 +: endpoints)

  def fromEndpoints[PathInput, Input, Err, Output, Middleware <: EndpointMiddleware](
    title: String,
    version: String,
    referenceType: ReferenceType,
    endpoint1: Endpoint[PathInput, Input, Err, Output, Middleware],
    endpoints: Endpoint[PathInput, Input, Err, Output, Middleware]*,
  ): OpenAPI = fromEndpoints(title, version, referenceType, endpoint1 +: endpoints)

  def fromEndpoints[PathInput, Input, Err, Output, Middleware <: EndpointMiddleware](
    referenceType: ReferenceType,
    endpoints: Iterable[Endpoint[PathInput, Input, Err, Output, Middleware]],
  ): OpenAPI = if (endpoints.isEmpty) OpenAPI.empty else endpoints.map(gen(_, referenceType)).reduce(_ ++ _)

  def fromEndpoints[PathInput, Input, Err, Output, Middleware <: EndpointMiddleware](
    endpoints: Iterable[Endpoint[PathInput, Input, Err, Output, Middleware]],
  ): OpenAPI = if (endpoints.isEmpty) OpenAPI.empty else endpoints.map(gen(_, ReferenceType.Compact)).reduce(_ ++ _)

  def fromEndpoints[PathInput, Input, Err, Output, Middleware <: EndpointMiddleware](
    title: String,
    version: String,
    endpoints: Iterable[Endpoint[PathInput, Input, Err, Output, Middleware]],
  ): OpenAPI = fromEndpoints(endpoints).title(title).version(version)

  def fromEndpoints[PathInput, Input, Err, Output, Middleware <: EndpointMiddleware](
    title: String,
    version: String,
    referenceType: ReferenceType,
    endpoints: Iterable[Endpoint[PathInput, Input, Err, Output, Middleware]],
  ): OpenAPI = fromEndpoints(referenceType, endpoints).title(title).version(version)

  def gen[PathInput, Input, Err, Output, Middleware <: EndpointMiddleware](
    endpoint: Endpoint[PathInput, Input, Err, Output, Middleware],
    referenceType: ReferenceType = ReferenceType.Compact,
  ): OpenAPI = {
    val inAtoms = AtomizedMetaCodecs.flatten(endpoint.input)
    val outs: Map[OpenAPI.StatusOrDefault, Map[MediaType, (JsonSchema, AtomizedMetaCodecs)]] =
      schemaByStatusAndMediaType(endpoint.output.alternatives ++ endpoint.error.alternatives, referenceType)
    // there is no status for inputs. So we just take the first one (default)
    val ins = schemaByStatusAndMediaType(endpoint.input.alternatives, referenceType).values.headOption

    def path: OpenAPI.Paths = {
      val path           = buildPath(endpoint.input)
      val method0        = method(inAtoms.method)
      // Endpoint has only one doc. But open api has a summery and a description
      val pathItem       = OpenAPI.PathItem.empty
        .copy(description = Some(endpoint.doc + endpoint.input.doc.getOrElse(Doc.empty)).filter(!_.isEmpty))
      val pathItemWithOp = method0 match {
        case Method.OPTIONS => pathItem.options(operation(endpoint))
        case Method.GET     => pathItem.get(operation(endpoint))
        case Method.HEAD    => pathItem.head(operation(endpoint))
        case Method.POST    => pathItem.post(operation(endpoint))
        case Method.PUT     => pathItem.put(operation(endpoint))
        case Method.PATCH   => pathItem.patch(operation(endpoint))
        case Method.DELETE  => pathItem.delete(operation(endpoint))
        case Method.TRACE   => pathItem.trace(operation(endpoint))
        case Method.ANY     => pathItem.any(operation(endpoint))
        case method         => throw new IllegalArgumentException(s"OpenAPI does not support method $method")
      }
      Map(path -> pathItemWithOp)
    }

    def buildPath(in: HttpCodec[_, _]): OpenAPI.Path = {

      def pathCodec(in1: HttpCodec[_, _]): Option[HttpCodec.Path[_]] = in1 match {
        case atom: HttpCodec.Atom[_, _]           =>
          atom match {
            case codec @ HttpCodec.Path(_, _) => Some(codec)
            case _                            => None
          }
        case HttpCodec.Annotated(in, _)           => pathCodec(in)
        case HttpCodec.TransformOrFail(api, _, _) => pathCodec(api)
        case HttpCodec.Empty                      => None
        case HttpCodec.Halt                       => None
        case HttpCodec.Combine(left, right, _)    => pathCodec(left).orElse(pathCodec(right))
        case HttpCodec.Fallback(left, right)      => pathCodec(left).orElse(pathCodec(right))
      }

      val pathString = {
        val codec = pathCodec(in).getOrElse(throw new Exception("No path found.")).pathCodec
        if (codec.render.endsWith(SegmentCodec.Trailing.render))
          codec.renderIgnoreTrailing + s"{$PathWildcard}"
        else codec.render
      }
      OpenAPI.Path.fromString(pathString).getOrElse(throw new Exception(s"Invalid path: $pathString"))
    }

    def method(in: Chunk[MetaCodec[SimpleCodec[Method, _]]]): Method = {
      if (in.size > 1) throw new Exception("Multiple methods not supported")
      in.collectFirst { case MetaCodec(SimpleCodec.Specified(method: Method), _) => method }
        .getOrElse(throw new Exception("No method specified"))
    }

    def operation(endpoint: Endpoint[_, _, _, _, _]): OpenAPI.Operation =
      OpenAPI.Operation(
        tags = Nil,
        summary = None,
        description = Some(endpoint.doc + pathDoc).filter(!_.isEmpty),
        externalDocs = None,
        operationId = None,
        parameters = parameters,
        requestBody = requestBody,
        responses = responses,
        callbacks = Map.empty,
        security = Nil,
        servers = Nil,
      )

    def pathDoc: Doc = {
      def loop(codec: PathCodec[_]): Doc = codec match {
        case PathCodec.Segment(_)                 =>
          // segment docs are used in path parameters
          Doc.empty
        case PathCodec.Concat(left, right, _, _)  =>
          loop(left) + loop(right)
        case PathCodec.TransformOrFail(api, _, _) =>
          loop(api)
      }
      loop(endpoint.route.pathCodec)
    }

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
      }

    def responses: OpenAPI.Responses =
      responsesForAlternatives(outs)

    def parameters: Set[OpenAPI.ReferenceOr[OpenAPI.Parameter]] =
      queryParams ++ pathParams ++ headerParams

    def queryParams: Set[OpenAPI.ReferenceOr[OpenAPI.Parameter]] = {
      inAtoms.query.collect { case mc @ MetaCodec(HttpCodec.Query(name, codec, _), _) =>
        OpenAPI.ReferenceOr.Or(
          OpenAPI.Parameter.queryParameter(
            name = name,
            description = mc.docsOpt,
            schema = Some(OpenAPI.ReferenceOr.Or(JsonSchema.fromTextCodec(codec))),
            deprecated = mc.deprecated,
            style = OpenAPI.Parameter.Style.Form,
            explode = false,
            allowReserved = false,
            examples = mc.examples.map { case (name, value) =>
              name -> OpenAPI.ReferenceOr.Or(OpenAPI.Example(value = Json.Str(value.toString)))
            },
            required = mc.required,
          ),
        )
      }
    }.toSet

    def pathParams: Set[OpenAPI.ReferenceOr[OpenAPI.Parameter]] =
      inAtoms.path.collect {
        case mc @ MetaCodec(codec, _) if codec != SegmentCodec.Empty && !codec.isInstanceOf[SegmentCodec.Literal] =>
          OpenAPI.ReferenceOr.Or(
            OpenAPI.Parameter.pathParameter(
              name = mc.name.getOrElse(throw new Exception("Path parameter must have a name")),
              description = mc.docsOpt,
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
        case enum: Schema.Enum[_] =>
          val discriminatorName =
            enum.annotations.collectFirst { case zio.schema.annotation.discriminatorName(name) => name }
          val noDiscriminator   = enum.annotations.contains(zio.schema.annotation.noDiscriminator())
          val typeMapping       = enum.cases.map { case_ =>
            val caseName =
              case_.annotations.collectFirst { case zio.schema.annotation.caseName(name) => name }.getOrElse(case_.id)
            // There should be no enums with cases that are not records with a nominal id
            val typeId   =
              case_.schema
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
      schemas = componentSchemas,
      responses = Map.empty,
      parameters = Map.empty,
      examples = Map.empty,
      requestBodies = Map.empty,
      headers = Map.empty,
      securitySchemes = Map.empty,
      links = Map.empty,
      callbacks = Map.empty,
    )

    def segmentToJson(codec: SegmentCodec[_], value: Any) = {
      codec match {
        case SegmentCodec.Empty      => throw new Exception("Empty segment not allowed")
        case SegmentCodec.Literal(_) => throw new Exception("Literal segment not allowed")
        case SegmentCodec.BoolSeg(_) => Json.Bool(value.asInstanceOf[Boolean])
        case SegmentCodec.IntSeg(_)  => Json.Num(value.asInstanceOf[Int])
        case SegmentCodec.LongSeg(_) => Json.Num(value.asInstanceOf[Long])
        case SegmentCodec.Text(_)    => Json.Str(value.asInstanceOf[String])
        case SegmentCodec.UUID(_)    => Json.Str(value.asInstanceOf[UUID].toString)
        case SegmentCodec.Trailing   => throw new Exception("Trailing segment not allowed")
      }
    }

    def componentSchemas: Map[OpenAPI.Key, OpenAPI.ReferenceOr[JsonSchema]] =
      (endpoint.input.alternatives.map(AtomizedMetaCodecs.flatten).flatMap(_.content)
        ++ endpoint.error.alternatives.map(AtomizedMetaCodecs.flatten).flatMap(_.content)
        ++ endpoint.output.alternatives.map(AtomizedMetaCodecs.flatten).flatMap(_.content)).collect {
        case MetaCodec(HttpCodec.Content(schema, _, _, _), _) if nominal(schema, referenceType).isDefined       =>
          OpenAPI.Key.fromString(nominal(schema, referenceType).get).get ->
            OpenAPI.ReferenceOr.Or(JsonSchema.fromZSchema(schema).discriminator(genDiscriminator(schema)))
        case MetaCodec(HttpCodec.ContentStream(schema, _, _, _), _) if nominal(schema, referenceType).isDefined =>
          OpenAPI.Key.fromString(nominal(schema, referenceType).get).get ->
            OpenAPI.ReferenceOr.Or(JsonSchema.fromZSchema(schema).discriminator(genDiscriminator(schema)))
      }.toMap

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
      paths = path,
      components = Some(components),
      security = Nil,
      tags = Nil,
      externalDocs = None,
    )
  }

  private def schemaByStatusAndMediaType(
    alternatives: Chunk[HttpCodec[_, _]],
    referenceType: ReferenceType,
  ): Map[OpenAPI.StatusOrDefault, Map[MediaType, (JsonSchema, AtomizedMetaCodecs)]] = {
    val statusAndCodec =
      alternatives.map { codec =>
        val statusOrDefault =
          status(codec).map(OpenAPI.StatusOrDefault.StatusValue(_)).getOrElse(OpenAPI.StatusOrDefault.Default)
        statusOrDefault -> (AtomizedMetaCodecs
          .flatten(codec), contentAsJsonSchema(codec, referenceType = referenceType))
      }

    statusAndCodec.groupMap { case (status, _) => status } { case (_, atomizedAndSchema) =>
      atomizedAndSchema
    }.map { case (status, values) =>
      status -> values
        .foldLeft(Chunk.empty[(MediaType, (AtomizedMetaCodecs, JsonSchema))]) { case (acc, (atomized, schema)) =>
          if (atomized.content.size > 1) {
            acc :+ (MediaType.multipart.`form-data` -> (atomized, schema))
          } else {
            val mediaType = atomized.content.headOption match {
              case Some(MetaCodec(HttpCodec.Content(_, Some(mediaType), _, _), _))       =>
                mediaType
              case Some(MetaCodec(HttpCodec.ContentStream(_, Some(mediaType), _, _), _)) =>
                mediaType
              case Some(MetaCodec(HttpCodec.ContentStream(schema, None, _, _), _))       =>
                if (schema == Schema[Byte]) MediaType.application.`octet-stream`
                else MediaType.application.`json`
              case _                                                                     =>
                MediaType.application.`json`
            }
            acc :+ (mediaType -> (atomized, schema))
          }
        }
        .groupMap { case (mediaType, _) => mediaType } { case (_, atomizedAndSchema) =>
          atomizedAndSchema
        }
        .map {
          case (mediaType, Chunk((atomized, schema))) if values.size == 1 =>
            mediaType -> (schema, atomized)
          case (mediaType, values)                                        =>
            val combinedAtomized: AtomizedMetaCodecs = values.map(_._1).reduce(_ ++ _)
            val combinedContentDoc                   = combinedAtomized.contentDocs.toCommonMark
            val alternativesSchema                   =
              JsonSchema
                .AnyOfSchema(values.map { case (_, schema) =>
                  schema.description match {
                    case Some(value) => schema.description(value.replace(combinedContentDoc, ""))
                    case None        => schema
                  }
                })
                .description(combinedContentDoc)
            mediaType -> (alternativesSchema, combinedAtomized)
        }
    }
  }

  def nominal(schema: Schema[_], referenceType: ReferenceType): Option[String] =
    schema match {
      case enum: Schema.Enum[_] =>
        enum.id match {
          case TypeId.Structural                                                 =>
            None
          case nominal: TypeId.Nominal if referenceType == ReferenceType.Compact =>
            Some(nominal.typeName)
          case nominal: TypeId.Nominal                                           =>
            Some(nominal.fullyQualified.replace(".", "_"))
        }
      case record: Record[_]    =>
        record.id match {
          case TypeId.Structural                                                 =>
            None
          case nominal: TypeId.Nominal if referenceType == ReferenceType.Compact =>
            Some(nominal.typeName)
          case nominal: TypeId.Nominal                                           =>
            Some(nominal.fullyQualified.replace(".", "_"))
        }
      case _                    => None
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
          headers = headersFrom(combinedAtomizedCodecs),
          content = mediaTypeResponses,
          links = Map.empty,
        ),
      )
    }

  private def headersFrom(codec: AtomizedMetaCodecs)                                             = {
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
  private def schemaReferencePath(nominal: TypeId.Nominal, referenceType: ReferenceType): String = {
    referenceType match {
      case ReferenceType.Compact => s"#/components/schemas/${nominal.typeName}}"
      case _                     => s"#/components/schemas/${nominal.fullyQualified.replace(".", "_")}}"
    }
  }
}
