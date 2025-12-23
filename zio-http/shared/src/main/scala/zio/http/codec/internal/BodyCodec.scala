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

import zio._

import zio.stream.{ZPipeline, ZStream}

import zio.schema._

import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http.codec.{BinaryCodecWithSchema, CodecConfig, HttpCodecError, HttpContentCodec}
import zio.http.{Body, FormField, MediaType, Status}

/**
 * A BodyCodec encapsulates the logic necessary to both encode and decode bodies
 * for a media type, using ZIO Schema codecs and schemas.
 */
private[http] sealed trait BodyCodec[A] { self =>

  /**
   * The element type, described by the schema. This could be the type of the
   * whole request, if it's an RPC-style request with a single, relatively small
   * body, or it could be the type of a single element in a stream.
   */
  type Element

  /**
   * Attempts to decode the `A` from a FormField using the given codec.
   */
  def decodeFromField(field: FormField, config: CodecConfig)(implicit trace: Trace): IO[Throwable, A]

  /**
   * Attempts to decode the `A` from a body using the given codec.
   */
def decodeFromBody(body: Body, status: Status, config: CodecConfig)(implicit trace: Trace): IO[Throwable, A]

  /**
   * Encodes the `A` to a FormField in the given codec.
   */
  def encodeToField(value: A, mediaTypes: Chunk[MediaTypeWithQFactor], name: String, config: CodecConfig)(implicit
    trace: Trace,
  ): FormField

  /**
   * Encodes the `A` to a body in the given codec.
   */
  def encodeToBody(value: A, mediaTypes: Chunk[MediaTypeWithQFactor], config: CodecConfig)(implicit trace: Trace): Body

  /**
   * Erases the type for easier use in the internal implementation.
   */
  final def erase: BodyCodec[Any] = self.asInstanceOf[BodyCodec[Any]]

  /**
   * Allows customizing the media type.
   *
   * The default is application/json for arbitrary types and
   * application/octet-stream for byte streams
   */
  def mediaType(accepted: Chunk[MediaTypeWithQFactor]): Option[MediaType]

  /**
   * Name of the body part
   *
   * In case of multipart/form-data encoding one request or response can consist
   * multiple named bodies
   */
  def name: Option[String]
}
private[http] object BodyCodec {
  case object Empty extends BodyCodec[Unit] {
    type Element = Unit

    override def decodeFromField(field: FormField, config: CodecConfig)(implicit trace: Trace): IO[Throwable, Unit] =
      ZIO.unit

    override def decodeFromBody(body: Body, status: Status, config: CodecConfig)(implicit trace: Trace): IO[Nothing, Unit] = ZIO.unit

    override def encodeToBody(value: Unit, mediaTypes: Chunk[MediaTypeWithQFactor], config: CodecConfig)(implicit
      trace: Trace,
    ): Body = Body.empty

    override def encodeToField(value: Unit, mediaTypes: Chunk[MediaTypeWithQFactor], name: String, config: CodecConfig)(
      implicit trace: Trace,
    ): FormField =
      throw HttpCodecError.CustomError("UnsupportedEncodingType", s"Unit can't be encoded to a FormField")

    def schema: Schema[Unit] = Schema[Unit]

    def mediaType(accepted: Chunk[MediaTypeWithQFactor]): Option[MediaType] = None

    def name: Option[String] = None
  }

  final case class Single[A](codec: HttpContentCodec[A], name: Option[String]) extends BodyCodec[A] {

    def mediaType(accepted: Chunk[MediaTypeWithQFactor]): Option[MediaType] =
      Some(codec.chooseFirstOrDefault(accepted)._1)

    def decodeFromField(field: FormField, config: CodecConfig)(implicit trace: Trace): IO[Throwable, A] = {
      val codec0 = codec
        .lookup(field.contentType)
        .toRight(HttpCodecError.CustomError("UnsupportedMediaType", s"MediaType: ${field.contentType}"))
      codec0 match {
        case Left(error)                                                            =>
          Exit.fail(error)
        case Right((_, BinaryCodecWithSchema(_, schema))) if schema == Schema[Unit] =>
          Exit.unit.asInstanceOf[IO[Throwable, A]]
        case Right((_, bc @ BinaryCodecWithSchema(_, schema)))                      =>
          field.asChunk.flatMap { chunk => ZIO.fromEither(bc.codec(config).decode(chunk)) }.flatMap(validateZIO(schema))
      }
    }

    override def decodeFromBody(body: Body, status: Status, config: CodecConfig)(implicit trace: Trace): IO[Throwable, A] = {
      val codec0 = codecForBody(codec, body)
      codec0 match {
        case Left(error)                                                            =>
          Exit.fail(error)
        case Right((_, BinaryCodecWithSchema(_, schema))) if schema == Schema[Unit] =>
          // For NoContent status, treat body as empty even if it's not physically empty
          if (body.isEmpty || status == Status.NoContent) Exit.unit.asInstanceOf[IO[Throwable, A]]
          else ZIO.fail(HttpCodecError.CustomError("InvalidBody", "Non-empty body cannot be decoded as Unit"))
        case Right((_, bc @ BinaryCodecWithSchema(_, schema)))                      =>
          body.asChunk.flatMap { chunk => ZIO.fromEither(bc.codec(config).decode(chunk)) }.flatMap(validateZIO(schema))
      }
    }

    def encodeToField(value: A, mediaTypes: Chunk[MediaTypeWithQFactor], name: String, config: CodecConfig)(implicit
      trace: Trace,
    ): FormField = {
      val selected  = codec.chooseFirstOrDefault(mediaTypes)
      val mediaType = selected._1
      val bc        = selected._2
      if (mediaType.binary) {
        FormField.binaryField(
          name,
          bc.codec(config).encode(value),
          mediaType,
        )
      } else {
        FormField.textField(
          name,
          bc.codec(config).encode(value).asString,
          mediaType,
        )
      }
    }

    def encodeToBody(value: A, mediaTypes: Chunk[MediaTypeWithQFactor], config: CodecConfig)(implicit
      trace: Trace,
    ): Body = {
      val selected  = codec.chooseFirstOrDefault(mediaTypes)
      val mediaType = selected._1
      val bc        = selected._2
      if (bc.schema == Schema[Unit]) Body.empty.contentType(mediaType)
      else Body.fromChunk(bc.codec(config).encode(value), mediaType)
    }

    type Element = A
  }

  final case class Multiple[E](codec: HttpContentCodec[E], name: Option[String])
      extends BodyCodec[ZStream[Any, Nothing, E]] {

    def mediaType(accepted: Chunk[MediaTypeWithQFactor]): Option[MediaType] =
      Some(codec.chooseFirstOrDefault(accepted)._1)

    override def decodeFromField(field: FormField, config: CodecConfig)(implicit
      trace: Trace,
    ): IO[Throwable, ZStream[Any, Nothing, E]] =
      ZIO.fromEither {
        codec
          .lookup(field.contentType)
          .toRight(HttpCodecError.CustomError("UnsupportedMediaType", s"MediaType: ${field.contentType}"))
          .map { case (_, bc) =>
            (field.asStream >>> bc.codec(config).streamDecoder >>> validateStream(bc.schema)).orDie
          }
      }

    override def decodeFromBody(body: Body, status: Status, config: CodecConfig)(implicit
      trace: Trace,
    ): IO[Throwable, ZStream[Any, Nothing, E]] =
      ZIO.fromEither {
        codecForBody(codec, body).map { case (_, bc @ BinaryCodecWithSchema(_, schema)) =>
          (body.asStream >>> bc.codec(config).streamDecoder >>> validateStream(schema)).orDie
        }
      }

    override def encodeToField(
      value: ZStream[Any, Nothing, E],
      mediaTypes: Chunk[MediaTypeWithQFactor],
      name: String,
      config: CodecConfig,
    )(implicit
      trace: Trace,
    ): FormField = {
      val selected  = codec.chooseFirstOrDefault(mediaTypes)
      val mediaType = selected._1
      val bc        = selected._2
      FormField.streamingBinaryField(
        name,
        value >>> bc.codec(config).streamEncoder,
        mediaType,
      )
    }

    override def encodeToBody(
      value: ZStream[Any, Nothing, E],
      mediaTypes: Chunk[MediaTypeWithQFactor],
      config: CodecConfig,
    )(implicit
      trace: Trace,
    ): Body = {
      val selected  = codec.chooseFirstOrDefault(mediaTypes)
      val mediaType = selected._1
      val bc        = selected._2
      Body.fromStreamChunked(value >>> bc.codec(config).streamEncoder).contentType(mediaType)
    }

    type Element = E
  }

  private def codecForBody[E](codec: HttpContentCodec[E], body: Body) = {
    val mediaType = body.mediaType.getOrElse(codec.defaultMediaType)
    val codec0    = codec
      .lookup(mediaType)
      .toRight(HttpCodecError.CustomError("UnsupportedMediaType", s"MediaType: ${body.mediaType}"))
    codec0
  }

  private[internal] def validateZIO[A](schema: Schema[A])(e: A)(implicit trace: Trace): ZIO[Any, HttpCodecError, A] = {
    val errors = Schema.validate(e)(schema)
    if (errors.isEmpty) Exit.succeed(e)
    else Exit.fail(HttpCodecError.InvalidEntity.wrap(errors))
  }

  private[internal] def validateStream[E](schema: Schema[E])(implicit
    trace: Trace,
  ): ZPipeline[Any, HttpCodecError, E, E] =
    ZPipeline.mapZIO(validateZIO(schema))

}
