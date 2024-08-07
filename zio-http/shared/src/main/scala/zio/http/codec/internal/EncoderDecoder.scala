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

import scala.annotation.nowarn
import scala.util.Try

import zio._

import zio.stream.ZStream

import zio.schema.codec.{BinaryCodec, DecodeError}
import zio.schema.{Schema, StandardType}

import zio.http.Header.Accept.{MediaTypeWithQFactor, render}
import zio.http._
import zio.http.codec.HttpCodec.Query.QueryType
import zio.http.codec._

private[codec] trait EncoderDecoder[-AtomTypes, Value] { self =>
  def decode(url: URL, status: Status, method: Method, headers: Headers, body: Body)(implicit
    trace: Trace,
  ): Task[Value]

  def encodeWith[Z](value: Value, outputTypes: Chunk[MediaTypeWithQFactor])(
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

    def decode(url: URL, status: Status, method: Method, headers: Headers, body: Body)(implicit
      trace: Trace,
    ): Task[Value] = {
      def tryDecode(i: Int, lastError: Cause[Throwable]): Task[Value] = {
        if (i >= singles.length) ZIO.refailCause(lastError)
        else {
          val (codec, condition) = singles(i)

          if (condition.isMissingDataOnly && !HttpCodecError.isMissingDataOnly(lastError))
            tryDecode(i + 1, lastError)
          else
            codec
              .decode(url, status, method, headers, body)
              .catchAllCause(cause =>
                if (HttpCodecError.isHttpCodecError(cause)) {
                  tryDecode(i + 1, lastError && cause)
                } else ZIO.refailCause(cause),
              )
        }
      }

      tryDecode(0, Cause.empty)
    }

    def encodeWith[Z](value: Value, outputTypes: Chunk[MediaTypeWithQFactor])(
      f: (URL, Option[Status], Option[Method], Headers, Body) => Z,
    ): Z = {
      var i         = 0
      var encoded   = null.asInstanceOf[Z]
      var lastError = null.asInstanceOf[Throwable]

      while (i < singles.length) {
        val (current, _) = singles(i)

        try {
          encoded = current.encodeWith(value, outputTypes)(f)

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
      value: Value,
      outputTypes: Chunk[MediaTypeWithQFactor],
    )(f: (zio.http.URL, Option[zio.http.Status], Option[zio.http.Method], zio.http.Headers, zio.http.Body) => Z): Z = {
      throw new IllegalStateException(encodeWithErrorMessage)
    }

    override def decode(
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

    private val formFieldEncoders: Chunk[(String, Any) => FormField] =
      flattened.content.map { bodyCodec => (name: String, value: Any) =>
        {
          val (mediaType, codec) = {
            bodyCodec match {
              case BodyCodec.Single(codec, _)   =>
                codec.choices.headOption
              case BodyCodec.Multiple(codec, _) =>
                codec.choices.headOption
              case _                            =>
                None
            }
          }.getOrElse {
            throw HttpCodecError.CustomError(
              "CodecNotFound",
              s"Cannot encode multipart/form-data field $name: no codec found",
            )
          }

          if (mediaType.binary) {
            FormField.binaryField(
              name,
              codec.codec.asInstanceOf[BinaryCodec[Any]].encode(value),
              mediaType,
            )
          } else {
            FormField.textField(
              name,
              codec.codec.asInstanceOf[BinaryCodec[Any]].encode(value).asString,
              mediaType,
            )
          }
        }
      }

    implicit val trace: Trace = Trace.empty

    private val formFieldDecoders: Chunk[FormField => IO[Throwable, Any]] =
      flattened.content.map { bodyCodec => (field: FormField) =>
        {
          val mediaType = field.contentType
          val codec     = {
            bodyCodec match {
              case BodyCodec.Empty              =>
                None
              case BodyCodec.Single(codec, _)   =>
                codec.lookup(mediaType)
              case BodyCodec.Multiple(codec, _) =>
                codec.lookup(mediaType)
            }
          }.getOrElse { throw HttpCodecError.UnsupportedContentType(mediaType.fullType) }

          field.asChunk.flatMap(chunk => ZIO.fromEither(codec.codec.decode(chunk)))

        }
      }
    private val formBoundary                = Boundary("----zio-http-boundary-D4792A5C-93E0-43B5-9A1F-48E38FDE5714")
    private val indexByName                 = flattened.content.zipWithIndex.map { case (codec, idx) =>
      codec.name.getOrElse("field" + idx.toString) -> idx
    }.toMap
    private val nameByIndex                 = indexByName.map(_.swap)
    private val isByteStream                =
      if (flattened.content.length == 1) {
        isByteStreamBody(flattened.content(0))
      } else {
        false
      }
    private val onlyTheLastFieldIsStreaming =
      if (flattened.content.size > 1) {
        !flattened.content.init.exists(isByteStreamBody) && isByteStreamBody(flattened.content.last)
      } else {
        false
      }

    def decode(url: URL, status: Status, method: Method, headers: Headers, body: Body)(implicit
      trace: Trace,
    ): Task[Value] = ZIO.suspendSucceed {
      val inputsBuilder = flattened.makeInputsBuilder()

      decodePaths(url.path, inputsBuilder.path)
      decodeQuery(url.queryParams, inputsBuilder.query)
      decodeStatus(status, inputsBuilder.status)
      decodeMethod(method, inputsBuilder.method)
      decodeHeaders(headers, inputsBuilder.header)
      decodeBody(body, inputsBuilder.content).as(constructor(inputsBuilder))
    }

    final def encodeWith[Z](value: Value, outputTypes: Chunk[MediaTypeWithQFactor])(
      f: (URL, Option[Status], Option[Method], Headers, Body) => Z,
    ): Z = {
      val inputs = deconstructor(value)

      val path               = encodePath(inputs.path)
      val query              = encodeQuery(inputs.query)
      val status             = encodeStatus(inputs.status)
      val method             = encodeMethod(inputs.method)
      val headers            = encodeHeaders(inputs.header)
      def contentTypeHeaders = encodeContentType(inputs.content, outputTypes)
      val body               = encodeBody(inputs.content, outputTypes)

      val headers0 = if (headers.contains("content-type")) headers else headers ++ contentTypeHeaders
      f(URL(path, queryParams = query), status, method, headers0, body)
    }

    private def decodePaths(path: Path, inputs: Array[Any]): Unit = {
      assert(flattened.path.length == inputs.length)

      var i = 0

      while (i < inputs.length) {
        val pathCodec = flattened.path(i).erase

        val decoded = pathCodec.decode(path)

        inputs(i) = decoded match {
          case Left(error) =>
            throw HttpCodecError.MalformedPath(path, pathCodec, error)

          case Right(value) => value
        }

        i = i + 1
      }
    }

    private def decodeQuery(queryParams: QueryParams, inputs: Array[Any]): Unit = {
      var i       = 0
      val queries = flattened.query
      while (i < queries.length) {
        val query = queries(i).erase

        val isOptional = query.isOptional

        query.queryType match {
          case QueryType.Primitive(name, BinaryCodecWithSchema(codec, schema))                                   =>
            val count    = queryParams.valueCount(name)
            val hasParam = queryParams.hasQueryParam(name)
            if (!hasParam && isOptional) inputs(i) = None
            else if (!hasParam) throw HttpCodecError.MissingQueryParam(name)
            else if (count != 1) throw HttpCodecError.InvalidQueryParamCount(name, 1, count)
            else {
              val decoded          = codec.decode(
                Chunk.fromArray(queryParams.unsafeQueryParam(name).getBytes(Charsets.Utf8)),
              ) match {
                case Left(error)  => throw HttpCodecError.MalformedQueryParam(name, error)
                case Right(value) => value
              }
              val validationErrors = schema.validate(decoded)(schema)
              if (validationErrors.nonEmpty) throw HttpCodecError.InvalidEntity.wrap(validationErrors)
              inputs(i) =
                if (isOptional && decoded == None && emptyStringIsValue(schema.asInstanceOf[Schema.Optional[_]].schema))
                  Some("")
                else decoded
            }
          case c @ QueryType.Collection(_, QueryType.Primitive(name, BinaryCodecWithSchema(codec, _)), optional) =>
            if (!queryParams.hasQueryParam(name)) {
              if (!optional) inputs(i) = c.toCollection(Chunk.empty)
              else inputs(i) = None
            } else {
              val values           = queryParams.queryParams(name)
              val decoded          = c.toCollection {
                values.map { value =>
                  codec.decode(Chunk.fromArray(value.getBytes(Charsets.Utf8))) match {
                    case Left(error)  => throw HttpCodecError.MalformedQueryParam(name, error)
                    case Right(value) => value
                  }
                }
              }
              val erasedSchema     = c.colSchema.asInstanceOf[Schema[Any]]
              val validationErrors = erasedSchema.validate(decoded)(erasedSchema)
              if (validationErrors.nonEmpty) throw HttpCodecError.InvalidEntity.wrap(validationErrors)
              inputs(i) =
                if (optional) Some(decoded)
                else decoded
            }
          case query @ QueryType.Record(recordSchema)                                                            =>
            val hasAllParams = query.fieldAndCodecs.forall { case (field, _) =>
              queryParams.hasQueryParam(field.name) || field.optional || field.defaultValue.isDefined
            }
            if (!hasAllParams && recordSchema.isInstanceOf[Schema.Optional[_]]) inputs(i) = None
            else if (!hasAllParams && isOptional) {
              inputs(i) = recordSchema.defaultValue match {
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
                      codec.codec.decode(Chunk.fromArray(value.getBytes(Charsets.Utf8))) match {
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
                      codec.codec.decode(Chunk.fromArray(value.getBytes(Charsets.Utf8))) match {
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
                val schema      = recordSchema.asInstanceOf[Schema.Optional[_]].schema.asInstanceOf[Schema.Record[Any]]
                val constructed = schema.construct(decoded)(Unsafe.unsafe)
                constructed match {
                  case Left(value)  =>
                    throw HttpCodecError.MalformedQueryParam(s"${schema.id}", DecodeError.ReadError(Cause.empty, value))
                  case Right(value) =>
                    schema.validate(value)(schema) match {
                      case errors if errors.nonEmpty => throw HttpCodecError.InvalidEntity.wrap(errors)
                      case _                         => inputs(i) = Some(value)
                    }
                }
              } else {
                val schema      = recordSchema.asInstanceOf[Schema.Record[Any]]
                val constructed = schema.construct(decoded)(Unsafe.unsafe)
                constructed match {
                  case Left(value)  =>
                    throw HttpCodecError.MalformedQueryParam(s"${schema.id}", DecodeError.ReadError(Cause.empty, value))
                  case Right(value) =>
                    schema.validate(value)(schema) match {
                      case errors if errors.nonEmpty => throw HttpCodecError.InvalidEntity.wrap(errors)
                      case _                         => inputs(i) = value
                    }
                }
              }
            }
        }
        i = i + 1
      }
    }

    private def emptyStringIsValue(schema: Schema[_]): Boolean =
      schema.asInstanceOf[Schema.Primitive[_]].standardType match {
        case StandardType.UnitType   => true
        case StandardType.StringType => true
        case StandardType.BinaryType => true
        case StandardType.CharType   => true
        case _                       => false
      }

    private def decodeStatus(status: Status, inputs: Array[Any]): Unit = {
      var i = 0
      while (i < inputs.length) {
        inputs(i) = flattened.status(i) match {
          case _: SimpleCodec.Unspecified[_]   => status
          case SimpleCodec.Specified(expected) =>
            if (status != expected)
              throw HttpCodecError.MalformedStatus(expected, status)
            else ()
        }

        i = i + 1
      }
    }

    private def decodeMethod(method: Method, inputs: Array[Any]): Unit = {
      var i = 0
      while (i < inputs.length) {
        inputs(i) = flattened.method(i) match {
          case _: SimpleCodec.Unspecified[_]   => method
          case SimpleCodec.Specified(expected) =>
            if (method != expected) throw HttpCodecError.MalformedMethod(expected, method)
            else ()
        }

        i = i + 1
      }
    }

    private def decodeHeaders(headers: Headers, inputs: Array[Any]): Unit = {
      var i = 0
      while (i < flattened.header.length) {
        val header = flattened.header(i).erase

        headers.get(header.name) match {
          case Some(value) =>
            inputs(i) = header.textCodec
              .decode(value)
              .getOrElse(throw HttpCodecError.MalformedHeader(header.name, header.textCodec))

          case None =>
            throw HttpCodecError.MissingHeader(header.name)
        }

        i = i + 1
      }
    }

    private def decodeBody(body: Body, inputs: Array[Any])(implicit
      trace: Trace,
    ): Task[Unit] = {
      if (isByteStream) {
        ZIO.attempt(inputs(0) = body.asStream.orDie)
      } else if (flattened.content.isEmpty) {
        ZIO.unit
      } else if (flattened.content.size == 1) {
        val bodyCodec = flattened.content(0)
        bodyCodec
          .decodeFromBody(body)
          .mapBoth(
            { err => HttpCodecError.MalformedBody(err.getMessage(), Some(err)) },
            result => inputs(0) = result,
          )
      } else {
        body.asMultipartFormStream.flatMap { form =>
          if (onlyTheLastFieldIsStreaming)
            processStreamingForm(form, inputs)
          else
            collectAndProcessForm(form, inputs)
        }.zipRight {
          ZIO.attempt {
            var idx = 0
            while (idx < inputs.length) {
              if (inputs(idx) == null)
                throw HttpCodecError.MalformedBody(
                  s"Missing multipart/form-data field (${Try(nameByIndex(idx))}",
                )
              idx += 1
            }
          }
        }
      }
    }

    private def processStreamingForm(form: StreamingForm, inputs: Array[Any])(implicit
      trace: Trace,
    ): ZIO[Any, Throwable, Unit] =
      Promise.make[Throwable, Unit].flatMap { ready =>
        form.fields.mapZIO { field =>
          indexByName.get(field.name) match {
            case Some(idx) =>
              (flattened.content(idx) match {
                case BodyCodec.Multiple(codec, _) if codec.defaultMediaType.binary =>
                  field match {
                    case FormField.Binary(_, data, _, _, _)          =>
                      inputs(idx) = ZStream.fromChunk(data)
                    case FormField.StreamingBinary(_, _, _, _, data) =>
                      inputs(idx) = data
                    case FormField.Text(_, value, _, _)              =>
                      inputs(idx) = ZStream.fromChunk(Chunk.fromArray(value.getBytes(Charsets.Utf8)))
                    case FormField.Simple(_, value)                  =>
                      inputs(idx) = ZStream.fromChunk(Chunk.fromArray(value.getBytes(Charsets.Utf8)))
                  }
                  ZIO.unit
                case _                                                             =>
                  formFieldDecoders(idx)(field).map { result => inputs(idx) = result }
              })
                .zipRight(
                  ready
                    .succeed(())
                    .unless(
                      inputs.exists(_ == null),
                    ), // Marking as ready so the handler can start consuming the streaming field before this stream ends
                )
            case None      =>
              ready.fail(HttpCodecError.MalformedBody(s"Unexpected multipart/form-data field: ${field.name}"))
          }
        }.runDrain
          .intoPromise(ready)
          .forkDaemon
          .zipRight(
            ready.await,
          )
      }

    private def collectAndProcessForm(form: StreamingForm, inputs: Array[Any])(implicit
      trace: Trace,
    ): ZIO[Any, Throwable, Unit] =
      form.collectAll.flatMap { collectedForm =>
        ZIO.foreachDiscard(collectedForm.formData) { field =>
          indexByName.get(field.name) match {
            case Some(idx) =>
              flattened.content(idx) match {
                case BodyCodec.Multiple(codec, _) if codec.defaultMediaType.binary =>
                  field match {
                    case FormField.Binary(_, data, _, _, _)          =>
                      inputs(idx) = ZStream.fromChunk(data)
                    case FormField.StreamingBinary(_, _, _, _, data) =>
                      inputs(idx) = data
                    case FormField.Text(_, value, _, _)              =>
                      inputs(idx) = ZStream.fromChunk(Chunk.fromArray(value.getBytes(Charsets.Utf8)))
                    case FormField.Simple(_, value)                  =>
                      inputs(idx) = ZStream.fromChunk(Chunk.fromArray(value.getBytes(Charsets.Utf8)))
                  }
                  ZIO.unit
                case _                                                             =>
                  formFieldDecoders(idx)(field).map { result => inputs(idx) = result }
              }
            case None      =>
              ZIO.fail(HttpCodecError.MalformedBody(s"Unexpected multipart/form-data field: ${field.name}"))
          }
        }
      }

    private def encodePath(inputs: Array[Any]): Path = {
      var path: Path = Path.empty

      var i = 0
      while (i < inputs.length) {
        val pathCodec = flattened.path(i).erase
        val input     = inputs(i)

        val encoded = pathCodec.encode(input) match {
          case Left(error)  =>
            throw HttpCodecError.MalformedPath(path, pathCodec, error)
          case Right(value) => value
        }
        path = path ++ encoded

        i = i + 1
      }

      path
    }

    private def encodeQuery(inputs: Array[Any]): QueryParams = {
      var queryParams = QueryParams.empty

      var i = 0
      while (i < inputs.length) {
        val query = flattened.query(i).erase
        val input = inputs(i)

        query.queryType match {
          case QueryType.Primitive(name, codec)                                                        =>
            val schema = codec.schema
            if (schema.isInstanceOf[Schema.Primitive[_]]) {
              if (schema.asInstanceOf[Schema.Primitive[_]].standardType.isInstanceOf[StandardType.UnitType.type]) {
                queryParams = queryParams.addQueryParams(name, Chunk.empty[String])
              } else {
                val encoded = codec.codec.asInstanceOf[BinaryCodec[Any]].encode(input).asString
                queryParams = queryParams.addQueryParams(name, Chunk(encoded))
              }
            } else if (schema.isInstanceOf[Schema.Optional[_]]) {
              val encoded = codec.codec.asInstanceOf[BinaryCodec[Any]].encode(input).asString
              if (encoded.nonEmpty) queryParams = queryParams.addQueryParams(name, Chunk(encoded))
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
              queryParams = queryParams.addQueryParams(
                name,
                Chunk.fromIterable(
                  values.map { value =>
                    codec.codec.asInstanceOf[BinaryCodec[Any]].encode(value).asString
                  },
                ),
              )
            }
          case query @ QueryType.Record(recordSchema) if recordSchema.isInstanceOf[Schema.Optional[_]] =>
            input match {
              case None        =>
                ()
              case Some(value) =>
                val innerSchema = recordSchema.asInstanceOf[Schema.Optional[_]].schema.asInstanceOf[Schema.Record[Any]]
                val fieldValues = innerSchema.deconstruct(value)(Unsafe.unsafe)
                var j           = 0
                while (j < fieldValues.size) {
                  val (field, codec) = query.fieldAndCodecs(j)
                  val name           = field.name
                  val value          = fieldValues(j) match {
                    case Some(value) => value
                    case None        => field.defaultValue
                  }
                  value match {
                    case values: Iterable[_] =>
                      queryParams = queryParams.addQueryParams(
                        name,
                        Chunk.fromIterable(values.map { v =>
                          codec.codec.asInstanceOf[BinaryCodec[Any]].encode(v).asString
                        }),
                      )
                    case _                   =>
                      val encoded = codec.codec.asInstanceOf[BinaryCodec[Any]].encode(value).asString
                      queryParams = queryParams.addQueryParam(name, encoded)
                  }
                  j = j + 1
                }
            }
          case query @ QueryType.Record(recordSchema)                                                  =>
            val innerSchema = recordSchema.asInstanceOf[Schema.Record[Any]]
            val fieldValues = innerSchema.deconstruct(input)(Unsafe.unsafe)
            var j           = 0
            while (j < fieldValues.size) {
              val (field, codec) = query.fieldAndCodecs(j)
              val name           = field.name
              val value          = fieldValues(j) match {
                case Some(value) => value
                case None        => field.defaultValue
              }
              value match {
                case values if values.isInstanceOf[Iterable[_]] =>
                  queryParams = queryParams.addQueryParams(
                    name,
                    Chunk.fromIterable(values.asInstanceOf[Iterable[Any]].map { v =>
                      codec.codec.asInstanceOf[BinaryCodec[Any]].encode(v).asString
                    }),
                  )
                case _                                          =>
                  val encoded = codec.codec.asInstanceOf[BinaryCodec[Any]].encode(value).asString
                  queryParams = queryParams.addQueryParam(name, encoded)
              }
              j = j + 1
            }
        }
        i = i + 1
      }

      queryParams
    }

    private def encodeStatus(inputs: Array[Any]): Option[Status] = {
      if (flattened.status.length == 0) {
        None
      } else {
        flattened.status(0) match {
          case _: SimpleCodec.Unspecified[_] => Some(inputs(0).asInstanceOf[Status])
          case SimpleCodec.Specified(status) => Some(status)
        }
      }
    }

    private def encodeHeaders(inputs: Array[Any]): Headers = {
      var headers = Headers.empty

      var i = 0
      while (i < inputs.length) {
        val header = flattened.header(i).erase
        val input  = inputs(i)

        val value = header.textCodec.encode(input)

        headers = headers ++ Headers(header.name, value)

        i = i + 1
      }

      headers
    }

    private def encodeMethod(inputs: Array[Any]): Option[zio.http.Method]                      =
      if (flattened.method.nonEmpty) {
        flattened.method.head match {
          case _: SimpleCodec.Unspecified[_] => Some(inputs(0).asInstanceOf[Method])
          case SimpleCodec.Specified(method) => Some(method)
        }
      } else None
    private def encodeBody(inputs: Array[Any], outputTypes: Chunk[MediaTypeWithQFactor]): Body =
      if (isByteStream) {
        Body.fromStreamChunked(inputs(0).asInstanceOf[ZStream[Any, Nothing, Byte]])
      } else {
        inputs.length match {
          case 0 =>
            Body.empty
          case 1 =>
            val bodyCodec = flattened.content(0)
            bodyCodec.erase.encodeToBody(inputs(0), outputTypes)
          case _ =>
            Body.fromMultipartForm(encodeMultipartFormData(inputs, outputTypes), formBoundary)
        }
      }

    private def encodeMultipartFormData(inputs: Array[Any], outputTypes: Chunk[MediaTypeWithQFactor]): Form = {
      Form(
        flattened.content.zipWithIndex.map { case (bodyCodec, idx) =>
          val input = inputs(idx)
          val name  = nameByIndex(idx)
          bodyCodec match {
            case BodyCodec.Multiple(codec, _) if codec.defaultMediaType.binary =>
              FormField.streamingBinaryField(
                name,
                input.asInstanceOf[ZStream[Any, Nothing, Byte]],
                bodyCodec.mediaType(outputTypes).getOrElse(MediaType.application.`octet-stream`),
              )
            case _                                                             =>
              formFieldEncoders(idx)(name, input)
          }
        }: _*,
      )
    }

    private def encodeContentType(inputs: Array[Any], outputTypes: Chunk[MediaTypeWithQFactor]): Headers = {
      if (isByteStream) {
        val mediaType = flattened.content(0).mediaType(outputTypes).getOrElse(MediaType.application.`octet-stream`)
        Headers(Header.ContentType(mediaType))
      } else {
        if (inputs.length > 1) {
          Headers(Header.ContentType(MediaType.multipart.`form-data`))
        } else {
          if (flattened.content.length < 1) Headers.empty
          else {
            val mediaType = flattened
              .content(0)
              .mediaType(outputTypes)
              .getOrElse(throw HttpCodecError.CustomError("InvalidHttpContentCodec", "No codecs found."))
            Headers(Header.ContentType(mediaType))
          }
        }
      }
    }

    private def isByteStreamBody(codec: BodyCodec[_]): Boolean =
      codec match {
        case BodyCodec.Multiple(codec, _) if codec.defaultMediaType.binary => true
        case _                                                             => false
      }
  }

}
