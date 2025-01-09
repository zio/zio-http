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

import scala.annotation.tailrec
import scala.util.Try

import zio._

import zio.schema.codec.DecodeError
import zio.schema.{Schema, StandardType}

import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http._
import zio.http.codec.StringCodec.StringCodec
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
      case 0 => Undefined.asInstanceOf[EncoderDecoder[AtomTypes, Value]]
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

  private object Undefined extends EncoderDecoder[Any, Any] {

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
      value: Any,
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
    )(implicit trace: zio.Trace): zio.Task[Any] = {
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
      decodeCustomHeaders(headers, inputsBuilder.headerCustom)
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
      val headers            = encodeHeaders(inputs.header) ++ encodeCustomHeaders(inputs.headerCustom)
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
          val optional   = query.isOptionalSchema
          val hasDefault = query.codec.defaultValue != null && query.isOptional
          val default    = query.codec.defaultValue
          if (codec.isPrimitive) {
            val name     = query.nameUnsafe
            val hasParam = queryParams.hasQueryParam(name)
            if (
              (!hasParam || (queryParams
                .unsafeQueryParam(name) == "" && !emptyStringIsValue(codec.codec.schema))) && hasDefault
            )
              default
            else if (!hasParam)
              throw HttpCodecError.MissingQueryParam(name)
            else if (queryParams.valueCount(name) != 1)
              throw HttpCodecError.InvalidQueryParamCount(name, 1, queryParams.valueCount(name))
            else {
              val decoded          =
                codec.codec.stringCodec.decode(queryParams.unsafeQueryParam(name)) match {
                  case Left(error)  => throw HttpCodecError.MalformedQueryParam(name, error)
                  case Right(value) => value
                }
              val validationErrors = codec.codec.erasedSchema.validate(decoded)(codec.codec.erasedSchema)
              if (validationErrors.nonEmpty) throw HttpCodecError.InvalidEntity.wrap(validationErrors)
              else decoded
            }

          } else if (codec.isCollection) {
            val name     = query.nameUnsafe
            val hasParam = queryParams.hasQueryParam(name)

            if (!hasParam) {
              if (query.codec.defaultValue != null) query.codec.defaultValue
              else throw HttpCodecError.MissingQueryParam(name)
            } else {
              val decoded = queryParams.queryParams(name).map { value =>
                query.codec.stringCodec.decode(value) match {
                  case Left(error)  => throw HttpCodecError.MalformedQueryParam(name, error)
                  case Right(value) => value
                }
              }
              if (optional)
                Some(
                  createAndValidateCollection(
                    query.codec.schema.asInstanceOf[Schema.Optional[_]].schema.asInstanceOf[Schema.Collection[_, _]],
                    decoded,
                  ),
                )
              else createAndValidateCollection(query.codec.schema.asInstanceOf[Schema.Collection[_, _]], decoded)
            }
          } else {
            val recordSchema = query.codec.recordSchema
            val fields       = query.codec.recordFields
            val hasAllParams = fields.forall { case (field, codec) =>
              queryParams.hasQueryParam(field.fieldName) || field.optional || codec.isOptional
            }
            if (!hasAllParams && hasDefault) default
            else if (!hasAllParams) throw HttpCodecError.MissingQueryParams {
              fields.collect {
                case (field, codec)
                    if !(queryParams.hasQueryParam(field.fieldName) || field.optional || codec.isOptional) =>
                  field.fieldName
              }
            }
            else {
              val decoded = fields.map {
                case (field, codec) if field.schema.isInstanceOf[Schema.Collection[_, _]] =>
                  val schema = field.schema.asInstanceOf[Schema.Collection[_, _]]
                  if (!queryParams.hasQueryParam(field.fieldName)) {
                    if (field.defaultValue.isDefined) field.defaultValue.get
                    else throw HttpCodecError.MissingQueryParam(field.fieldName)
                  } else {
                    val values  = queryParams.queryParams(field.fieldName)
                    val decoded =
                      values.map(decodeAndUnwrap(field, codec, _, HttpCodecError.MalformedQueryParam.apply))
                    createAndValidateCollection(schema, decoded)

                  }
                case (field, codec)                                                       =>
                  val value   = queryParams.queryParamOrElse(field.fieldName, null)
                  val decoded = {
                    if (value == null || (value == "" && !emptyStringIsValue(codec.schema))) codec.defaultValue
                    else decodeAndUnwrap(field, codec, value, HttpCodecError.MalformedQueryParam.apply)
                  }
                  validateDecoded(codec, decoded)
              }
              if (optional) {
                val constructed = recordSchema.construct(decoded)(Unsafe.unsafe)
                constructed match {
                  case Left(value)  =>
                    throw HttpCodecError.MalformedQueryParam(
                      s"${recordSchema.id}",
                      DecodeError.ReadError(Cause.empty, value),
                    )
                  case Right(value) =>
                    recordSchema.validate(value)(recordSchema) match {
                      case errors if errors.nonEmpty => throw HttpCodecError.InvalidEntity.wrap(errors)
                      case _                         => Some(value)
                    }
                }
              } else {
                val constructed = recordSchema.construct(decoded)(Unsafe.unsafe)
                constructed match {
                  case Left(value)  =>
                    throw HttpCodecError.MalformedQueryParam(
                      s"${recordSchema.id}",
                      DecodeError.ReadError(Cause.empty, value),
                    )
                  case Right(value) =>
                    recordSchema.validate(value)(recordSchema) match {
                      case errors if errors.nonEmpty => throw HttpCodecError.InvalidEntity.wrap(errors)
                      case _                         => value
                    }
                }
              }
            }
          }
        },
      )

    private def createAndValidateCollection(schema: Schema.Collection[_, _], decoded: Chunk[Any]) = {
      val collection       = schema.fromChunk.asInstanceOf[Chunk[Any] => Any](decoded)
      val erasedSchema     = schema.asInstanceOf[Schema[Any]]
      val validationErrors = erasedSchema.validate(collection)(erasedSchema)
      if (validationErrors.nonEmpty) throw HttpCodecError.InvalidEntity.wrap(validationErrors)
      collection
    }

    @tailrec
    private def emptyStringIsValue(schema: Schema[_]): Boolean = {
      schema match {
        case value: Schema.Optional[_] =>
          val innerSchema = value.schema
          emptyStringIsValue(innerSchema)
        case _                         =>
          schema.asInstanceOf[Schema.Primitive[_]].standardType match {
            case StandardType.UnitType   => true
            case StandardType.StringType => true
            case StandardType.BinaryType => true
            case StandardType.CharType   => true
            case _                       => false
          }
      }
    }

    private def decodeCustomHeaders(headers: Headers, inputs: Array[Any]): Unit =
      genericDecode[Headers, HttpCodec.HeaderCustom[_]](
        headers,
        flattened.headerCustom,
        inputs,
        (header, headers) => {
          val optional = header.codec.isOptionalSchema
          if (header.codec.isPrimitive) {
            val schema = header.erase.codec.schema
            val name   = header.codec.name.get
            val value  = headers.getUnsafe(name)
            if (value ne null) {
              val decoded          = header.codec.stringCodec.decode(value) match {
                case Left(error)  => throw HttpCodecError.MalformedCustomHeader(name, error)
                case Right(value) => value
              }
              val validationErrors = schema.validate(decoded)(schema)
              if (validationErrors.nonEmpty) throw HttpCodecError.InvalidEntity.wrap(validationErrors)
              else decoded
            } else {
              if (optional) None
              else throw HttpCodecError.MissingHeader(name)
            }
          } else if (header.codec.isCollection) {
            val name    = header.codec.name.get
            val values  = headers.rawHeaders(name)
            val decoded = values.map { value =>
              header.codec.stringCodec.decode(value) match {
                case Left(error)  => throw HttpCodecError.MalformedCustomHeader(name, error)
                case Right(value) => value
              }
            }
            if (optional)
              Some(
                createAndValidateCollection(
                  header.codec.schema.asInstanceOf[Schema.Optional[_]].schema.asInstanceOf[Schema.Collection[_, _]],
                  decoded,
                ),
              )
            else createAndValidateCollection(header.codec.schema.asInstanceOf[Schema.Collection[_, _]], decoded)
          } else {
            val recordSchema = header.codec.recordSchema
            val fields       = header.codec.recordFields
            val hasAllParams = fields.forall { case (field, codec) =>
              headers.contains(field.fieldName) || field.optional || codec.isOptional
            }
            if (!hasAllParams) {
              if (header.codec.defaultValue != null && header.codec.isOptional) header.codec.defaultValue
              else
                throw HttpCodecError.MissingHeaders {
                  fields.collect {
                    case (field, codec) if !(headers.contains(field.fieldName) || field.optional || codec.isOptional) =>
                      field.fieldName
                  }
                }
            } else {
              val decoded = fields.map {
                case (field, codec) if field.schema.isInstanceOf[Schema.Collection[_, _]] =>
                  if (!headers.contains(codec.name.get)) {
                    if (codec.defaultValue != null) codec.defaultValue
                    else throw HttpCodecError.MissingHeader(codec.name.get)
                  } else {
                    val schema  = field.schema.asInstanceOf[Schema.Collection[_, _]]
                    val values  = headers.rawHeaders(codec.name.get)
                    val decoded =
                      values.map(decodeAndUnwrap(field, codec, _, HttpCodecError.MalformedCustomHeader.apply))
                    createAndValidateCollection(schema, decoded)
                  }
                case (field, codec)                                                       =>
                  val value   = headers.getUnsafe(codec.name.get)
                  val decoded =
                    if (value == null || (value == "" && !emptyStringIsValue(codec.schema))) codec.defaultValue
                    else decodeAndUnwrap(field, codec, value, HttpCodecError.MalformedCustomHeader.apply)
                  validateDecoded(codec, decoded)
              }
              if (optional) {
                val constructed = recordSchema.construct(decoded)(Unsafe.unsafe)
                constructed match {
                  case Left(value)  =>
                    throw HttpCodecError.MalformedCustomHeader(
                      s"${recordSchema.id}",
                      DecodeError.ReadError(Cause.empty, value),
                    )
                  case Right(value) =>
                    recordSchema.validate(value)(recordSchema) match {
                      case errors if errors.nonEmpty => throw HttpCodecError.InvalidEntity.wrap(errors)
                      case _                         => Some(value)
                    }
                }
              } else {
                val constructed = recordSchema.construct(decoded)(Unsafe.unsafe)
                constructed match {
                  case Left(value)  =>
                    throw HttpCodecError.MalformedCustomHeader(
                      s"${recordSchema.id}",
                      DecodeError.ReadError(Cause.empty, value),
                    )
                  case Right(value) =>
                    recordSchema.validate(value)(recordSchema) match {
                      case errors if errors.nonEmpty => throw HttpCodecError.InvalidEntity.wrap(errors)
                      case _                         => value
                    }
                }
              }
            }
          }
        },
      )

    private def validateDecoded(codec: HttpCodec.SchemaCodec[Any], decoded: Any) = {
      val validationErrors = codec.schema.validate(decoded)(codec.schema)
      if (validationErrors.nonEmpty) throw HttpCodecError.InvalidEntity.wrap(validationErrors)
      decoded
    }

    private def decodeAndUnwrap(
      field: Schema.Field[_, _],
      codec: HttpCodec.SchemaCodec[Any],
      value: String,
      ex: (String, DecodeError) => HttpCodecError,
    ) = {
      codec.stringCodec.decode(value) match {
        case Left(error)  => throw ex(codec.name.get, error)
        case Right(value) => value
      }
    }

    private def decodeHeaders(headers: Headers, inputs: Array[Any]): Unit =
      genericDecode[Headers, HttpCodec.Header[_]](
        headers,
        flattened.header,
        inputs,
        (codec, headers) =>
          headers.get(codec.headerType.name) match {
            case Some(value) =>
              codec.erase.headerType
                .parse(value)
                .getOrElse(throw HttpCodecError.MalformedTypedHeader(codec.headerType.name))

            case None =>
              throw HttpCodecError.MissingHeader(codec.headerType.name)
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
          val query       = codec.erase
          val optional    = query.isOptionalSchema
          val stringCodec = codec.codec.stringCodec.asInstanceOf[StringCodec[Any]]

          if (query.isPrimitive) {
            val schema = codec.codec.schema
            val name   = query.nameUnsafe
            if (schema.isInstanceOf[Schema.Primitive[_]]) {
              if (schema.asInstanceOf[Schema.Primitive[_]].standardType.isInstanceOf[StandardType.UnitType.type]) {
                queryParams.addQueryParams(name, Chunk.empty[String])
              } else {
                val encoded = stringCodec.encode(input)
                queryParams.addQueryParams(name, Chunk(encoded))
              }
            } else if (schema.isInstanceOf[Schema.Optional[_]]) {
              val encoded = stringCodec.encode(input)
              if (encoded.nonEmpty) queryParams.addQueryParams(name, Chunk(encoded)) else queryParams
            } else {
              throw new IllegalStateException(
                "Only primitive schema is supported for query parameters of type Primitive",
              )
            }
          } else if (query.isCollection) {
            val name    = query.nameUnsafe
            var in: Any = input
            if (optional) {
              in = input.asInstanceOf[Option[Any]].getOrElse(Chunk.empty)
            }
            val values  = input.asInstanceOf[Iterable[Any]]
            if (values.nonEmpty) {
              queryParams.addQueryParams(
                name,
                Chunk.fromIterable(values.map { value => stringCodec.encode(value) }),
              )
            } else queryParams
          } else if (query.isRecord) {
            val value = input match {
              case None        => null
              case Some(value) => value
              case value       => value
            }
            if (value == null) queryParams
            else {
              val innerSchema   = query.codec.recordSchema
              val fieldValues   = innerSchema.deconstruct(value)(Unsafe.unsafe)
              var qp            = queryParams
              val fieldIt       = query.codec.recordFields.iterator
              val fieldValuesIt = fieldValues.iterator
              while (fieldIt.hasNext) {
                val (field, codec) = fieldIt.next()
                val name           = field.fieldName
                val value          = fieldValuesIt.next() match {
                  case Some(value) => value
                  case None        => field.defaultValue
                }
                value match {
                  case values: Iterable[_] =>
                    qp = qp.addQueryParams(
                      name,
                      Chunk.fromIterable(values.map { v =>
                        codec.stringCodec.encode(v)
                      }),
                    )
                  case _                   =>
                    val encoded = codec.stringCodec.encode(value)
                    qp = qp.addQueryParam(name, encoded)
                }
              }
              qp
            }
          } else {
            queryParams
          }
        },
      )

    private def encodeCustomHeaders(inputs: Array[Any]): Headers = {
      genericEncode[Headers, HttpCodec.HeaderCustom[_]](
        flattened.headerCustom,
        inputs,
        Headers.empty,
        (codec, input, headers) => {
          val optional    = codec.codec.isOptionalSchema
          val stringCodec = codec.erase.codec.stringCodec
          if (codec.codec.isPrimitive) {
            val name  = codec.codec.name.get
            val value = input
            if (optional && value == None) headers
            else {
              val encoded = stringCodec.encode(value)
              headers ++ Headers(name, encoded)
            }
          } else if (codec.codec.isCollection) {
            val name   = codec.codec.name.get
            val values = input.asInstanceOf[Iterable[Any]]
            if (values.nonEmpty) {
              headers ++ Headers.FromIterable(
                values.map { value =>
                  Header.Custom(name, stringCodec.encode(value))
                },
              )
            } else headers
          } else {
            val recordSchema = codec.codec.recordSchema
            val fields       = codec.codec.recordFields
            val value        = input match {
              case None        => null
              case Some(value) => value
              case value       => value
            }
            if (value == null) headers
            else {
              val fieldValues   = recordSchema.deconstruct(value)(Unsafe.unsafe)
              var hs            = headers
              val fieldIt       = fields.iterator
              val fieldValuesIt = fieldValues.iterator
              while (fieldIt.hasNext) {
                val (field, codec) = fieldIt.next()
                val name           = field.fieldName
                val value          = fieldValuesIt.next() match {
                  case Some(value) => value
                  case None        => field.defaultValue
                }
                value match {
                  case values: Iterable[_] =>
                    hs = hs ++ Headers.FromIterable(
                      values.map { v =>
                        Header.Custom(name, codec.stringCodec.encode(v))
                      },
                    )
                  case _                   =>
                    val encoded = codec.stringCodec.encode(value)
                    hs = hs ++ Headers(name, encoded)
                }
              }
              hs
            }
          }
        },
      )

    }
    private def encodeHeaders(inputs: Array[Any]): Headers =
      genericEncode[Headers, HttpCodec.Header[_]](
        flattened.header,
        inputs,
        Headers.empty,
        (codec, input, headers) => headers ++ Headers(codec.headerType.name, codec.erase.headerType.render(input)),
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
