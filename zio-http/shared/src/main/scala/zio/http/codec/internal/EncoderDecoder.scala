/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.codec.internal

import scala.util.Try

import zio._

import zio.schema.codec.{BinaryCodec, DecodeError}
import zio.schema.{Schema, StandardType}

import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http._
import zio.http.codec.HttpCodec.Query.QueryType
import zio.http.codec._

private[codec] trait EncoderDecoder[-AtomTypes, Value] { self =>
  def decode(config: CodecConfig, url: URL, status: Status, method: Method, headers: Headers, body: Body)(implicit
    trace: Trace,
  ): Task[Value]

  def encodeWith[Z](config: CodecConfig, value: Value, outputTypes: Chunk[MediaTypeWithQFactor])(
    f: (URL, Option[Status], Option[Method], Headers, Body) => Z,
  ): Z

}
private[codec] object EncoderDecoder {

  def apply[AtomTypes, Value](
    httpCodec: HttpCodec[AtomTypes, Value],
  ): EncoderDecoder[AtomTypes, Value] = {
    val flattened = httpCodec.alternatives

    flattened.length match {
      case 0 => Undefined()
      case 1 => Single(flattened.head._1)
      case _ => Multiple(flattened)
    }
  }

  private final case class Multiple[-AtomTypes, Value](
    httpCodecs: Chunk[(HttpCodec[AtomTypes, Value], HttpCodec.Fallback.Condition)],
  ) extends EncoderDecoder[AtomTypes, Value] {
    val singles = httpCodecs.map { case (httpCodec, condition) => Single(httpCodec) -> condition }

    override def decode(config: CodecConfig, url: URL, status: Status, method: Method, headers: Headers, body: Body)(
      implicit trace: Trace,
    ): Task[Value] = {
      def tryDecode(i: Int, lastError: Cause[Throwable]): Task[Value] = {
        if (i >= singles.length) ZIO.refailCause(lastError)
        else {
          val (codec, condition) = singles(i)

          if (condition.isMissingDataOnly && !HttpCodecError.isMissingDataOnly(lastError))
            tryDecode(i + 1, lastError)
          else
            codec
              .decode(config, url, status, method, headers, body)
              .catchAllCause(cause =>
                if (HttpCodecError.isHttpCodecError(cause)) {
                  tryDecode(i + 1, lastError && cause)
                } else ZIO.refailCause(cause),
              )
        }
      }

      tryDecode(0, Cause.empty)
    }

    override def encodeWith[Z](config: CodecConfig, value: Value, outputTypes: Chunk[MediaTypeWithQFactor])(
      f: (URL, Option[Status], Option[Method], Headers, Body) => Z,
    ): Z = {
      var i         = 0
      var encoded   = null.asInstanceOf[Z]
      var lastError = null.asInstanceOf[Throwable]

      while (i < singles.length) {
        val (current, _) = singles(i)

        try {
          encoded = current.encodeWith(config, value, outputTypes)(f)

          i = singles.length // break
        } catch {
          case error: HttpCodecError =>
            // TODO: Aggregate all errors in disjunction:
            lastError = error
        }

        i = i + 1
      }

      if (encoded == null) throw lastError
      else encoded
    }
  }

  private final case class Undefined[-AtomTypes, Value]() extends EncoderDecoder[AtomTypes, Value] {

    val encodeWithErrorMessage =
      """
        |Trying to encode with Undefined codec. That means that encode was invoked for object of type Nothing - which cannot exist.
        |Verify that middleware and endpoint have proper types or submit bug report at https://github.com/zio/zio-http/issues
    """.stripMargin.trim()

    val decodeErrorMessage =
      """
        |Trying to decode with Undefined codec. That means that decode was invoked for object of type Nothing - which cannot exist.
        |Verify that middleware and endpoint have proper types or submit bug report at https://github.com/zio/zio-http/issues
    """.stripMargin.trim()

    override def encodeWith[Z](
      config: CodecConfig,
      value: Value,
      outputTypes: Chunk[MediaTypeWithQFactor],
    )(f: (zio.http.URL, Option[zio.http.Status], Option[zio.http.Method], zio.http.Headers, zio.http.Body) => Z): Z = {
      throw new IllegalStateException(encodeWithErrorMessage)
    }

    override def decode(
      config: CodecConfig,
      url: zio.http.URL,
      status: zio.http.Status,
      method: zio.http.Method,
      headers: zio.http.Headers,
      body: zio.http.Body,
    )(implicit trace: zio.Trace): zio.Task[Value] = {
      ZIO.fail(new IllegalStateException(decodeErrorMessage))
    }
  }

  private final case class Single[-AtomTypes, Value](
    httpCodec: HttpCodec[AtomTypes, Value],
  ) extends EncoderDecoder[AtomTypes, Value] {
    private val constructor   = Mechanic.makeConstructor(httpCodec)
    private val deconstructor = Mechanic.makeDeconstructor(httpCodec)

    private val flattened: AtomizedCodecs = AtomizedCodecs.flatten(httpCodec)

    implicit val trace: Trace     = Trace.empty
    private lazy val formBoundary = Boundary("----zio-http-boundary-D4792A5C-93E0-43B5-9A1F-48E38FDE5714")
    private lazy val indexByName  = flattened.content.zipWithIndex.map { case (codec, idx) =>
      codec.name.getOrElse("field" + idx.toString) -> idx
    }.toMap
    private lazy val nameByIndex  = indexByName.map(_.swap)

    override def decode(config: CodecConfig, url: URL, status: Status, method: Method, headers: Headers, body: Body)(
      implicit trace: Trace,
    ): Task[Value] = ZIO.suspendSucceed {
      val inputsBuilder = flattened.makeInputsBuilder()

      decodePaths(url.path, inputsBuilder.path)
      decodeQuery(config, url.queryParams, inputsBuilder.query)
      decodeStatus(status, inputsBuilder.status)
      decodeMethod(method, inputsBuilder.method)
      decodeHeaders(headers, inputsBuilder.header)
      decodeBody(config, body, inputsBuilder.content).as(constructor(inputsBuilder))
    }

    override def encodeWith[Z](config: CodecConfig, value: Value, outputTypes: Chunk[MediaTypeWithQFactor])(
      f: (URL, Option[Status], Option[Method], Headers, Body) => Z,
    ): Z = {
      val inputs = deconstructor(value)

      val path               = encodePath(inputs.path)
      val query              = encodeQuery(config, inputs.query)
      val status             = encodeStatus(inputs.status)
      val method             = encodeMethod(inputs.method)
      val headers            = encodeHeaders(inputs.header)
      def contentTypeHeaders = encodeContentType(inputs.content, outputTypes)
      val body               = encodeBody(config, inputs.content, outputTypes)

      val headers0 = if (headers.contains("content-type")) headers else headers ++ contentTypeHeaders
      f(URL(path, queryParams = query), status, method, headers0, body)
    }

    private def genericDecode[A, Codec](
      a: A,
      codecs: Chunk[Codec],
      inputs: Array[Any],
      decode: (Codec, A) => Any,
    ): Unit = {
      for (i <- 0 until inputs.length) {
        val codec = codecs(i)
        inputs(i) = decode(codec, a)
      }
    }

    private def decodePaths(path: Path, inputs: Array[Any]): Unit =
      genericDecode[Path, PathCodec[_]](
        path,
        flattened.path,
        inputs,
        (codec, path) => {
          codec.erase.decode(path) match {
            case Left(error)  => throw HttpCodecError.MalformedPath(path, codec, error)
            case Right(value) => value
          }
        },
      )

    private def decodeQuery(config: CodecConfig, queryParams: QueryParams, inputs: Array[Any]): Unit =
      genericDecode[QueryParams, HttpCodec.Query[_, _]](
        queryParams,
        flattened.query,
        inputs,
        (codec, queryParams) => {
          val query      = codec.erase
          val isOptional = query.isOptional
          query.queryType match {
            case QueryType.Primitive(name, bc @ BinaryCodecWithSchema(_, schema))     =>
              val count    = queryParams.valueCount(name)
              val hasParam = queryParams.hasQueryParam(name)
              if (!hasParam && isOptional) None
              else if (!hasParam) throw HttpCodecError.MissingQueryParam(name)
              else if (count != 1) throw HttpCodecError.InvalidQueryParamCount(name, 1, count)
              else {
                val decoded          = bc
                  .codec(config)
                  .decode(
                    Chunk.fromArray(queryParams.unsafeQueryParam(name).getBytes(Charsets.Utf8)),
                  ) match {
                  case Left(error)  => throw HttpCodecError.MalformedQueryParam(name, error)
                  case Right(value) => value
                }
                val validationErrors = schema.validate(decoded)(schema)
                if (validationErrors.nonEmpty) throw HttpCodecError.InvalidEntity.wrap(validationErrors)
                if (isOptional && decoded == None && emptyStringIsValue(schema.asInstanceOf[Schema.Optional[_]].schema))
                  Some("")
                else decoded
              }
            case c @ QueryType.Collection(_, QueryType.Primitive(name, bc), optional) =>
              if (!queryParams.hasQueryParam(name)) {
                if (!optional) c.toCollection(Chunk.empty)
                else None
              } else {
                val values           = queryParams.queryParams(name)
                val decoded          = c.toCollection {
                  values.map { value =>
                    bc.codec(config).decode(Chunk.fromArray(value.getBytes(Charsets.Utf8))) match {
                      case Left(error)  => throw HttpCodecError.MalformedQueryParam(name, error)
                      case Right(value) => value
                    }
                  }
                }
                val erasedSchema     = c.colSchema.asInstanceOf[Schema[Any]]
                val validationErrors = erasedSchema.validate(decoded)(erasedSchema)
                if (validationErrors.nonEmpty) throw HttpCodecError.InvalidEntity.wrap(validationErrors)
                if (optional) Some(decoded)
                else decoded
              }
            case query @ QueryType.Record(recordSchema)                               =>
              val hasAllParams = query.fieldAndCodecs.forall { case (field, _) =>
                queryParams.hasQueryParam(field.name) || field.optional || field.defaultValue.isDefined
              }
              if (!hasAllParams && recordSchema.isInstanceOf[Schema.Optional[_]]) None
              else if (!hasAllParams && isOptional) {
                recordSchema.defaultValue match {
                  case Left(err)    =>
                    throw new IllegalStateException(s"Cannot compute default value for $recordSchema. Error was: $err")
                  case Right(value) => value
                }
              } else if (!hasAllParams) throw HttpCodecError.MissingQueryParams {
                query.fieldAndCodecs.collect {
                  case (field, _)
                      if !(queryParams.hasQueryParam(field.name) || field.optional || field.defaultValue.isDefined) =>
                    field.name
                }
              }
              else {
                val decoded = query.fieldAndCodecs.map {
                  case (field, codec) if field.schema.isInstanceOf[Schema.Collection[_, _]] =>
                    if (!queryParams.hasQueryParam(field.name) && field.defaultValue.nonEmpty) field.defaultValue.get
                    else {
                      val values            = queryParams.queryParams(field.name)
                      val decoded           = values.map { value =>
                        codec.codec(config).decode(Chunk.fromArray(value.getBytes(Charsets.Utf8))) match {
                          case Left(error)  => throw HttpCodecError.MalformedQueryParam(field.name, error)
                          case Right(value) => value
                        }
                      }
                      val decodedCollection =
                        field.schema match {
                          case s @ Schema.Sequence(_, fromChunk, _, _, _) =>
                            val collection       = fromChunk.asInstanceOf[Chunk[Any] => Any](decoded)
                            val erasedSchema     = s.asInstanceOf[Schema[Any]]
                            val validationErrors = erasedSchema.validate(collection)(erasedSchema)
                            if (validationErrors.nonEmpty) throw HttpCodecError.InvalidEntity.wrap(validationErrors)
                            collection
                          case s @ Schema.Set(_, _)                       =>
                            val collection       = decoded.toSet[Any]
                            val erasedSchema     = s.asInstanceOf[Schema.Set[Any]]
                            val validationErrors = erasedSchema.validate(collection)(erasedSchema)
                            if (validationErrors.nonEmpty) throw HttpCodecError.InvalidEntity.wrap(validationErrors)
                            collection
                          case _ => throw new IllegalStateException("Only Sequence and Set are supported.")
                        }
                      decodedCollection
                    }
                  case (field, codec)                                                       =>
                    val value            = queryParams.queryParamOrElse(field.name, null)
                    val decoded          = {
                      if (value == null) field.defaultValue.get
                      else {
                        codec.codec(config).decode(Chunk.fromArray(value.getBytes(Charsets.Utf8))) match {
                          case Left(error)  => throw HttpCodecError.MalformedQueryParam(field.name, error)
                          case Right(value) => value
                        }
                      }
                    }
                    val validationErrors = codec.schema.validate(decoded)(codec.schema)
                    if (validationErrors.nonEmpty) throw HttpCodecError.InvalidEntity.wrap(validationErrors)
                    decoded
                }
                if (recordSchema.isInstanceOf[Schema.Optional[_]]) {
                  val schema = recordSchema.asInstanceOf[Schema.Optional[_]].schema.asInstanceOf[Schema.Record[Any]]
                  val constructed = schema.construct(decoded)(Unsafe.unsafe)
                  constructed match {
                    case Left(value)  =>
                      throw HttpCodecError.MalformedQueryParam(
                        s"${schema.id}",
                        DecodeError.ReadError(Cause.empty, value),
                      )
                    case Right(value) =>
                      schema.validate(value)(schema) match {
                        case errors if errors.nonEmpty => throw HttpCodecError.InvalidEntity.wrap(errors)
                        case _                         => Some(value)
                      }
                  }
                } else {
                  val schema      = recordSchema.asInstanceOf[Schema.Record[Any]]
                  val constructed = schema.construct(decoded)(Unsafe.unsafe)
                  constructed match {
                    case Left(value)  =>
                      throw HttpCodecError.MalformedQueryParam(
                        s"${schema.id}",
                        DecodeError.ReadError(Cause.empty, value),
                      )
                    case Right(value) =>
                      schema.validate(value)(schema) match {
                        case errors if errors.nonEmpty => throw HttpCodecError.InvalidEntity.wrap(errors)
                        case _                         => value
                      }
                  }
                }
              }
          }
        },
      )

    private def emptyStringIsValue(schema: Schema[_]): Boolean =
      schema.asInstanceOf[Schema.Primitive[_]].standardType match {
        case StandardType.UnitType   => true
        case StandardType.StringType => true
        case StandardType.BinaryType => true
        case StandardType.CharType   => true
        case _                       => false
      }

    private def decodeHeaders(headers: Headers, inputs: Array[Any]): Unit =
      genericDecode[Headers, HttpCodec.Header[_]](
        headers,
        flattened.header,
        inputs,
        (codec, headers) =>
          headers.get(codec.name) match {
            case Some(value) =>
              codec.erase.textCodec
                .decode(value)
                .getOrElse(throw HttpCodecError.MalformedHeader(codec.name, codec.textCodec))

            case None =>
              throw HttpCodecError.MissingHeader(codec.name)
          },
      )

    private def decodeStatus(status: Status, inputs: Array[Any]): Unit =
      genericDecode[Status, SimpleCodec[Status, _]](
        status,
        flattened.status,
        inputs,
        (codec, status) =>
          codec match {
            case SimpleCodec.Specified(expected) if expected != status =>
              throw HttpCodecError.MalformedStatus(expected, status)
            case _: SimpleCodec.Unspecified[_]                         => status
            case _                                                     => ()
          },
      )

    private def decodeMethod(method: Method, inputs: Array[Any]): Unit =
      genericDecode[Method, SimpleCodec[Method, _]](
        method,
        flattened.method,
        inputs,
        (codec, method) =>
          codec match {
            case SimpleCodec.Specified(expected) if expected != method =>
              throw HttpCodecError.MalformedMethod(expected, method)
            case _: SimpleCodec.Unspecified[_]                         => method
            case _                                                     => ()
          },
      )

    private def decodeBody(config: CodecConfig, body: Body, inputs: Array[Any])(implicit
      trace: Trace,
    ): Task[Unit] = {
      val isNonMultiPart = inputs.length < 2
      if (isNonMultiPart) {
        val codecs = flattened.content

        // noinspection SimplifyUnlessInspection
        if (codecs.isEmpty) ZIO.unit
        else {
          val codec = codecs.head
          codec
            .decodeFromBody(body, config)
            .mapBoth(
              { err => HttpCodecError.MalformedBody(err.getMessage, Some(err)) },
              result => inputs(0) = result,
            )
        }
      } else {
        // multi-part
        decodeForm(body.asMultipartFormStream, inputs, config) *> check(inputs)
      }
    }

    private def decodeForm(
      form: Task[StreamingForm],
      inputs: Array[Any],
      config: CodecConfig,
    ): ZIO[Any, Throwable, Unit] =
      form.flatMap(_.collectAll).flatMap { collectedForm =>
        ZIO.foreachDiscard(collectedForm.formData) { field =>
          val codecs = flattened.content
          val i      = indexByName
            .get(field.name)
            .getOrElse(throw HttpCodecError.MalformedBody(s"Unexpected multipart/form-data field: ${field.name}"))
          val codec  = codecs(i).erase
          for {
            decoded <- codec.decodeFromField(field, config)
            _       <- ZIO.attempt { inputs(i) = decoded }
          } yield ()
        }
      }

    private def check(inputs: Array[Any]): ZIO[Any, Throwable, Unit] =
      ZIO.attempt {
        for (i <- 0 until inputs.length) {
          if (inputs(i) == null)
            throw HttpCodecError.MalformedBody(
              s"Missing multipart/form-data field (${Try(nameByIndex(i))}",
            )
        }
      }

    private def genericEncode[A, Codec](
      codecs: Chunk[Codec],
      inputs: Array[Any],
      init: A,
      encoding: (Codec, Any, A) => A,
    ): A = {
      var res = init
      for (i <- 0 until inputs.length) {
        val codec = codecs(i)
        val input = inputs(i)
        res = encoding(codec, input, res)
      }
      res
    }

    private def simpleEncode[A](codecs: Chunk[SimpleCodec[A, _]], inputs: Array[Any]): Option[A] =
      codecs.headOption.map { codec =>
        codec match {
          case _: SimpleCodec.Unspecified[_] => inputs(0).asInstanceOf[A]
          case SimpleCodec.Specified(elem)   => elem
        }
      }

    private def encodePath(inputs: Array[Any]): Path =
      genericEncode[Path, PathCodec[_]](
        flattened.path,
        inputs,
        Path.empty,
        (codec, a, acc) => {
          val encoded = codec.erase.encode(a) match {
            case Left(error)  =>
              throw HttpCodecError.MalformedPath(acc, codec, error)
            case Right(value) => value
          }
          acc ++ encoded
        },
      )

    private def encodeQuery(config: CodecConfig, inputs: Array[Any]): QueryParams =
      genericEncode[QueryParams, HttpCodec.Query[_, _]](
        flattened.query,
        inputs,
        QueryParams.empty,
        (codec, input, queryParams) => {
          val query = codec.erase

          query.queryType match {
            case QueryType.Primitive(name, codec)                                                        =>
              val schema = codec.schema
              if (schema.isInstanceOf[Schema.Primitive[_]]) {
                if (schema.asInstanceOf[Schema.Primitive[_]].standardType.isInstanceOf[StandardType.UnitType.type]) {
                  queryParams.addQueryParams(name, Chunk.empty[String])
                } else {
                  val encoded = codec.codec(config).asInstanceOf[BinaryCodec[Any]].encode(input).asString
                  queryParams.addQueryParams(name, Chunk(encoded))
                }
              } else if (schema.isInstanceOf[Schema.Optional[_]]) {
                val encoded = codec.codec(config).asInstanceOf[BinaryCodec[Any]].encode(input).asString
                if (encoded.nonEmpty) queryParams.addQueryParams(name, Chunk(encoded)) else queryParams
              } else {
                throw new IllegalStateException(
                  "Only primitive schema is supported for query parameters of type Primitive",
                )
              }
            case QueryType.Collection(_, QueryType.Primitive(name, codec), optional)                     =>
              var in: Any = input
              if (optional) {
                in = input.asInstanceOf[Option[Any]].getOrElse(Chunk.empty)
              }
              val values  = input.asInstanceOf[Iterable[Any]]
              if (values.nonEmpty) {
                queryParams.addQueryParams(
                  name,
                  Chunk.fromIterable(
                    values.map { value =>
                      codec.codec(config).asInstanceOf[BinaryCodec[Any]].encode(value).asString
                    },
                  ),
                )
              } else queryParams
            case query @ QueryType.Record(recordSchema) if recordSchema.isInstanceOf[Schema.Optional[_]] =>
              input match {
                case None        => queryParams
                case Some(value) =>
                  val innerSchema =
                    recordSchema.asInstanceOf[Schema.Optional[_]].schema.asInstanceOf[Schema.Record[Any]]
                  val fieldValues = innerSchema.deconstruct(value)(Unsafe.unsafe)
                  var j           = 0
                  var qp          = queryParams
                  while (j < fieldValues.size) {
                    val (field, codec) = query.fieldAndCodecs(j)
                    val name           = field.name
                    val value          = fieldValues(j) match {
                      case Some(value) => value
                      case None        => field.defaultValue
                    }
                    value match {
                      case values: Iterable[_] =>
                        qp = qp.addQueryParams(
                          name,
                          Chunk.fromIterable(values.map { v =>
                            codec.codec(config).asInstanceOf[BinaryCodec[Any]].encode(v).asString
                          }),
                        )
                      case _                   =>
                        val encoded = codec.codec(config).asInstanceOf[BinaryCodec[Any]].encode(value).asString
                        qp = qp.addQueryParam(name, encoded)
                    }
                    j = j + 1
                  }
                  qp
              }
            case query @ QueryType.Record(recordSchema)                                                  =>
              val innerSchema = recordSchema.asInstanceOf[Schema.Record[Any]]
              val fieldValues = innerSchema.deconstruct(input)(Unsafe.unsafe)
              var j           = 0
              var qp          = queryParams
              while (j < fieldValues.size) {
                val (field, codec) = query.fieldAndCodecs(j)
                val name           = field.name
                val value          = fieldValues(j) match {
                  case Some(value) => value
                  case None        => field.defaultValue
                }
                value match {
                  case values if values.isInstanceOf[Iterable[_]] =>
                    qp = qp.addQueryParams(
                      name,
                      Chunk.fromIterable(values.asInstanceOf[Iterable[Any]].map { v =>
                        codec.codec(config).asInstanceOf[BinaryCodec[Any]].encode(v).asString
                      }),
                    )
                  case _                                          =>
                    val encoded = codec.codec(config).asInstanceOf[BinaryCodec[Any]].encode(value).asString
                    qp = qp.addQueryParam(name, encoded)
                }
                j = j + 1
              }
              qp
          }
        },
      )

    private def encodeHeaders(inputs: Array[Any]): Headers =
      genericEncode[Headers, HttpCodec.Header[_]](
        flattened.header,
        inputs,
        Headers.empty,
        (codec, input, headers) => headers ++ Headers(codec.name, codec.erase.textCodec.encode(input)),
      )

    private def encodeStatus(inputs: Array[Any]): Option[Status] =
      simpleEncode(flattened.status, inputs)

    private def encodeMethod(inputs: Array[Any]): Option[Method] =
      simpleEncode(flattened.method, inputs)

    private def encodeBody(config: CodecConfig, inputs: Array[Any], outputTypes: Chunk[MediaTypeWithQFactor]): Body =
      inputs.length match {
        case 0 =>
          Body.empty
        case 1 =>
          val bodyCodec = flattened.content(0)
          bodyCodec.erase.encodeToBody(inputs(0), outputTypes, config)
        case _ =>
          Body.fromMultipartForm(encodeMultipartFormData(inputs, outputTypes, config), formBoundary)

      }

    private def encodeMultipartFormData(
      inputs: Array[Any],
      outputTypes: Chunk[MediaTypeWithQFactor],
      config: CodecConfig,
    ): Form = {
      val formFields = flattened.content.zipWithIndex.map { case (bodyCodec, idx) =>
        val input = inputs(idx)
        val name  = nameByIndex(idx)
        bodyCodec.erase.encodeToField(input, outputTypes, name, config)
      }

      Form(formFields: _*)
    }

    private def encodeContentType(inputs: Array[Any], outputTypes: Chunk[MediaTypeWithQFactor]): Headers =
      inputs.length match {
        case 0 =>
          Headers.empty
        case 1 =>
          val mediaType = flattened
            .content(0)
            .mediaType(outputTypes)
            .getOrElse(throw HttpCodecError.CustomError("InvalidHttpContentCodec", "No codecs found."))
          Headers(Header.ContentType(mediaType))
        case _ =>
          Headers(Header.ContentType(MediaType.multipart.`form-data`))
      }
  }
}
