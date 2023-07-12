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

import java.time._
import java.util.UUID

import zio._

import zio.stream.ZStream

import zio.schema.codec._
import zio.schema.{Schema, StandardType}

import zio.http._
import zio.http.codec._

private[codec] trait EncoderDecoder[-AtomTypes, Value] {
  def decode(url: URL, status: Status, method: Method, headers: Headers, body: Body)(implicit
    trace: Trace,
  ): Task[Value]

  def encodeWith[Z](value: Value)(f: (URL, Option[Status], Option[Method], Headers, Body) => Z): Z
}
private[codec] object EncoderDecoder                   {
  def apply[AtomTypes, Value](httpCodec: HttpCodec[AtomTypes, Value]): EncoderDecoder[AtomTypes, Value] = {
    val flattened = httpCodec.alternatives

    flattened.length match {
      case 0 => Undefined()
      case 1 => Single(flattened.head)
      case _ => Multiple(flattened)
    }
  }

  private final case class Multiple[-AtomTypes, Value](httpCodecs: Chunk[HttpCodec[AtomTypes, Value]])
      extends EncoderDecoder[AtomTypes, Value] {
    val singles = httpCodecs.map(Single(_))
    val head    = singles.head
    val tail    = singles.tail

    def decode(url: URL, status: Status, method: Method, headers: Headers, body: Body)(implicit
      trace: Trace,
    ): Task[Value] = {
      def tryDecode(i: Int, lastError: Cause[Throwable]): Task[Value] = {
        if (i >= singles.length) ZIO.refailCause(lastError)
        else {
          val codec = singles(i)

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

    def encodeWith[Z](value: Value)(f: (URL, Option[Status], Option[Method], Headers, Body) => Z): Z = {
      var i         = 0
      var encoded   = null.asInstanceOf[Z]
      var lastError = null.asInstanceOf[Throwable]

      while (i < singles.length) {
        val current = singles(i)

        try {
          encoded = current.encodeWith(value)(f)

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
        |Trying to decode with Undefined codec. That means that encode was invoked for object of type Nothing - which cannot exist.
        |Verify that middleware and endpoint have proper types or submit bug report at https://github.com/zio/zio-http/issues
    """.stripMargin.trim()

    override def encodeWith[Z](
      value: Value,
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

  private final case class Single[-AtomTypes, Value](httpCodec: HttpCodec[AtomTypes, Value])
      extends EncoderDecoder[AtomTypes, Value] {
    private val constructor   = Mechanic.makeConstructor(httpCodec)
    private val deconstructor = Mechanic.makeDeconstructor(httpCodec)

    private val flattened: AtomizedCodecs = AtomizedCodecs.flatten(httpCodec)

    private val jsonEncoders: Chunk[Any => Body] =
      flattened.content.map { bodyCodec =>
        val erased    = bodyCodec.erase
        val jsonCodec = JsonCodec.schemaBasedBinaryCodec(erased.schema)
        erased.encodeToBody(_, jsonCodec)
      }

    private val formFieldEncoders: Chunk[(String, Any) => FormField]      =
      flattened.content.map { bodyCodec => (name: String, value: Any) =>
        {
          val erased = bodyCodec.erase
          erased.schema match {
            case Schema.Primitive(_, _) =>
              FormField.simpleField(name, value.toString)
            case _                      =>
              val jsonCodec = JsonCodec.schemaBasedBinaryCodec(erased.schema)
              FormField.textField(
                name,
                new String(jsonCodec.encode(value.asInstanceOf[erased.Element]).toArray, Charsets.Utf8),
                MediaType.application.json,
              )
          }
        }
      }
    private val jsonDecoders: Chunk[Body => IO[Throwable, _]]             =
      flattened.content.map { bodyCodec =>
        val jsonCodec = JsonCodec.schemaBasedBinaryCodec(bodyCodec.schema)
        bodyCodec.decodeFromBody(_, jsonCodec)
      }
    private val formFieldDecoders: Chunk[FormField => IO[Throwable, Any]] =
      flattened.content.map { bodyCodec => (field: FormField) =>
        {
          val erased = bodyCodec.erase
          erased.schema match {
            case Schema.Primitive(standardType, _) =>
              field.asText.flatMap { text =>
                standardType.asInstanceOf[StandardType[_]] match {
                  case StandardType.UnitType           => ZIO.succeed(())
                  case StandardType.StringType         => ZIO.succeed(text)
                  case StandardType.BoolType           => ZIO.attempt(text.toBoolean)
                  case StandardType.ByteType           => ZIO.attempt(text.toByte)
                  case StandardType.ShortType          => ZIO.attempt(text.toShort)
                  case StandardType.IntType            => ZIO.attempt(text.toInt)
                  case StandardType.LongType           => ZIO.attempt(text.toLong)
                  case StandardType.FloatType          => ZIO.attempt(text.toFloat)
                  case StandardType.DoubleType         => ZIO.attempt(text.toDouble)
                  case StandardType.BinaryType         => ZIO.die(new IllegalStateException("Binary is not supported"))
                  case StandardType.CharType           => ZIO.attempt(text.charAt(0))
                  case StandardType.UUIDType           => ZIO.attempt(UUID.fromString(text))
                  case StandardType.BigDecimalType     => ZIO.attempt(BigDecimal(text))
                  case StandardType.BigIntegerType     => ZIO.attempt(BigInt(text))
                  case StandardType.DayOfWeekType      => ZIO.attempt(DayOfWeek.valueOf(text))
                  case StandardType.MonthType          => ZIO.attempt(Month.valueOf(text))
                  case StandardType.MonthDayType       => ZIO.attempt(MonthDay.parse(text))
                  case StandardType.PeriodType         => ZIO.attempt(Period.parse(text))
                  case StandardType.YearType           => ZIO.attempt(Year.parse(text))
                  case StandardType.YearMonthType      => ZIO.attempt(YearMonth.parse(text))
                  case StandardType.ZoneIdType         => ZIO.attempt(ZoneId.of(text))
                  case StandardType.ZoneOffsetType     => ZIO.attempt(ZoneOffset.of(text))
                  case StandardType.DurationType       => ZIO.attempt(java.time.Duration.parse(text))
                  case StandardType.InstantType        => ZIO.attempt(Instant.parse(text))
                  case StandardType.LocalDateType      => ZIO.attempt(LocalDate.parse(text))
                  case StandardType.LocalTimeType      => ZIO.attempt(LocalTime.parse(text))
                  case StandardType.LocalDateTimeType  => ZIO.attempt(LocalDateTime.parse(text))
                  case StandardType.OffsetTimeType     => ZIO.attempt(OffsetTime.parse(text))
                  case StandardType.OffsetDateTimeType => ZIO.attempt(OffsetDateTime.parse(text))
                  case StandardType.ZonedDateTimeType  => ZIO.attempt(ZonedDateTime.parse(text))
                }
              }
            case _                                 =>
              val jsonCodec = JsonCodec.schemaBasedBinaryCodec(erased.schema)
              field.asChunk.flatMap { chunk =>
                ZIO.fromEither(jsonCodec.decode(chunk))
              }
          }
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
    private val isEventStream               = if (flattened.content.length == 1) {
      isEventStreamBody(flattened.content(0))
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

    final def encodeWith[Z](value: Value)(f: (URL, Option[Status], Option[Method], Headers, Body) => Z): Z = {
      val inputs = deconstructor(value)

      val path               = encodePath(inputs.path)
      val query              = encodeQuery(inputs.query)
      val status             = encodeStatus(inputs.status)
      val method             = encodeMethod(inputs.method)
      val headers            = encodeHeaders(inputs.header)
      val body               = encodeBody(inputs.content)
      val contentTypeHeaders = encodeContentType(inputs.content)

      f(URL(path, queryParams = query), status, method, headers ++ contentTypeHeaders, body)
    }

    private def decodePaths(path: Path, inputs: Array[Any]): Unit = {
      assert(flattened.path.length == inputs.length)

      var i        = 0
      var j        = 0
      val segments = path.segments
      while (i < inputs.length) {
        val textCodec = flattened.path(i).erase

        if (j >= segments.length) throw HttpCodecError.PathTooShort(path, textCodec)
        else {
          val segment = segments(j)

          if (segment.nonEmpty) {
            val textCodec = flattened.path(i).erase

            inputs(i) = textCodec
              .decode(segment)
              .getOrElse(throw HttpCodecError.MalformedPath(path, segment, textCodec))

            i = i + 1
          }
          j = j + 1
        }
      }
    }

    private def decodeQuery(queryParams: QueryParams, inputs: Array[Any]): Unit = {
      var i       = 0
      val queries = flattened.query
      while (i < queries.length) {
        val query = queries(i).erase

        val queryParamValue =
          queryParams
            .getOrElse(query.name, Nil)
            .collectFirst(query.textCodec)

        queryParamValue match {
          case Some(value) =>
            inputs(i) = value
          case None        =>
            throw HttpCodecError.MissingQueryParam(query.name)
        }

        i = i + 1
      }
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

    private def decodeBody(body: Body, inputs: Array[Any])(implicit trace: Trace): Task[Unit] = {
      if (isByteStream) {
        ZIO.attempt(inputs(0) = body.asStream.orDie)
      } else if (jsonDecoders.isEmpty) {
        ZIO.unit
      } else if (jsonDecoders.length == 1) {
        jsonDecoders(0)(body).map { result => inputs(0) = result }.mapError { err =>
          HttpCodecError.MalformedBody(err.getMessage(), Some(err))
        }
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
                  s"Missing multipart/form-data field (${nameByIndex(idx)}",
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
      Promise.make[HttpCodecError, Unit].flatMap { ready =>
        form.fields.mapZIO { field =>
          indexByName.get(field.name) match {
            case Some(idx) =>
              (flattened.content(idx) match {
                case BodyCodec.Multiple(schema, _, _) if schema == Schema[Byte] =>
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
                case _                                                          =>
                  formFieldDecoders(idx)(field).map { result => inputs(idx) = result }
              }) *>
                ready
                  .succeed(())
                  .unless(
                    inputs.exists(_ == null),
                  ) // Marking as ready so the handler can start consuming the streaming field before this stream ends
            case None      =>
              ready.fail(HttpCodecError.MalformedBody(s"Unexpected multipart/form-data field: ${field.name}"))
          }
        }.runDrain
          .zipRight(
            ready
              .succeed(()),
          )
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
                case BodyCodec.Multiple(schema, _, _) if schema == Schema[Byte] =>
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
                case _                                                          =>
                  formFieldDecoders(idx)(field).map { result => inputs(idx) = result }
              }
            case None      =>
              ZIO.fail(HttpCodecError.MalformedBody(s"Unexpected multipart/form-data field: ${field.name}"))
          }
        }
      }

    private def encodePath(inputs: Array[Any]): Path = {
      var path = Path.empty

      var i = 0
      while (i < inputs.length) {
        val textCodec = flattened.path(i).erase
        val input     = inputs(i)

        val segment = textCodec.encode(input)

        path = path / segment
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

        val value = query.textCodec.encode(input)

        queryParams = queryParams.add(query.name, value)

        i = i + 1
      }

      queryParams
    }

    private def encodeStatus(inputs: Array[Any]): Option[Status] = {
      if (flattened.status.isEmpty) {
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

    private def encodeMethod(inputs: Array[Any]): Option[zio.http.Method] =
      if (flattened.method.nonEmpty) {
        flattened.method.head match {
          case _: SimpleCodec.Unspecified[_] => Some(inputs(0).asInstanceOf[Method])
          case SimpleCodec.Specified(method) => Some(method)
        }
      } else None

    private def encodeBody(inputs: Array[Any]): Body = {
      if (isByteStream) {
        Body.fromStream(inputs(0).asInstanceOf[ZStream[Any, Nothing, Byte]])
      } else {
        if (inputs.length > 1) {
          Body.fromMultipartForm(encodeMultipartFormData(inputs), formBoundary)
        } else {
          if (isEventStream) {
            Body.fromStream(inputs(0).asInstanceOf[ZStream[Any, Nothing, ServerSentEvent]].map(_.encode))
          } else if (jsonEncoders.length < 1) Body.empty
          else {
            val encoder = jsonEncoders(0)
            encoder(inputs(0))
          }
        }
      }
    }

    private def encodeMultipartFormData(inputs: Array[Any]): Form = {
      Form(
        flattened.content.zipWithIndex.map { case (bodyCodec, idx) =>
          val input = inputs(idx)
          val name  = nameByIndex(idx)
          bodyCodec match {
            case BodyCodec.Multiple(schema, mediaType, _) if schema == Schema[Byte] =>
              FormField.streamingBinaryField(
                name,
                input.asInstanceOf[ZStream[Any, Nothing, Byte]],
                mediaType.getOrElse(MediaType.application.`octet-stream`),
              )
            case _                                                                  =>
              formFieldEncoders(idx)(name, input)
          }
        }: _*,
      )
    }

    private def encodeContentType(inputs: Array[Any]): Headers = {
      if (isByteStream) {
        val mediaType = flattened.content(0).mediaType.getOrElse(MediaType.application.`octet-stream`)
        Headers(Header.ContentType(mediaType))
      } else {
        if (inputs.length > 1) {
          Headers(Header.ContentType(MediaType.multipart.`form-data`))
        } else {
          if (isEventStream) Headers(Header.ContentType(MediaType.text.`event-stream`))
          else if (jsonEncoders.length < 1) Headers.empty
          else {
            val mediaType = flattened.content(0).mediaType.getOrElse(MediaType.application.json)
            Headers(Header.ContentType(mediaType))
          }
        }
      }
    }

    private def isByteStreamBody(codec: BodyCodec[_]): Boolean =
      codec match {
        case BodyCodec.Multiple(schema, _, _) if schema == Schema[Byte] => true
        case _                                                          => false
      }

    private def isEventStreamBody(codec: BodyCodec[_]): Boolean =
      codec match {
        case BodyCodec.Multiple(schema, _, _) if schema == Schema[ServerSentEvent] => true
        case _                                                                     => false
      }
  }
}
