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
import zio.http.codec.{HttpCodecError, HttpContentCodec}
import zio.http.{Body, MediaType}

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
   * Attempts to decode the `A` from a body using the given codec.
   */
  def decodeFromBody(body: Body)(implicit trace: Trace): IO[Throwable, A]

  /**
   * Encodes the `A` to a body in the given codec.
   */
  def encodeToBody(value: A, mediaTypes: Chunk[MediaTypeWithQFactor])(implicit trace: Trace): Body

  /**
   * Erases the type for easier use in the internal implementation.
   */
  final def erase: BodyCodec[Any] = self.asInstanceOf[BodyCodec[Any]]

  /**
   * The schema associated with the element type.
   */
  def schema: Schema[Element]

  /**
   * Allows customizing the media type.
   *
   * The default is application/json for arbitrary types and
   * application/octet-stream for byte streams
   */
  def mediaType: Option[MediaType]

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

    def decodeFromBody(body: Body)(implicit trace: Trace): IO[Nothing, Unit] = ZIO.unit

    def encodeToBody(value: Unit, mediaTypes: Chunk[MediaTypeWithQFactor])(implicit trace: Trace): Body = Body.empty

    def schema: Schema[Unit] = Schema[Unit]

    def mediaType: Option[MediaType] = None

    def name: Option[String] = None
  }

  final case class Single[A](codec: HttpContentCodec[A], name: Option[String]) extends BodyCodec[A] {

    def schema: Schema[A] = codec.schema

    def mediaType: Option[MediaType] = Some(codec.defaultMediaType) // TODO: Is this correct?

    def decodeFromBody(body: Body)(implicit trace: Trace): IO[Throwable, A] =
      if (schema == Schema[Unit]) ZIO.unit.asInstanceOf[IO[Throwable, A]]
      else
        body.asChunk.flatMap { chunk =>
          ZIO.fromEither(codecForBody(codec, body).flatMap(_.decode(chunk)))
        }.flatMap(validateZIO(schema))

    def encodeToBody(value: A, mediaTypes: Chunk[MediaTypeWithQFactor])(implicit trace: Trace): Body = {
      val (mediaType, encoder) = codec.chooseFirst(mediaTypes)
      Body.fromChunk(encoder.encode(value)).contentType(mediaType)
    }

    type Element = A
  }

  final case class Multiple[E](codec: HttpContentCodec[E], name: Option[String])
      extends BodyCodec[ZStream[Any, Nothing, E]] {

    def schema: Schema[E] = codec.schema

    def mediaType: Option[MediaType] = Some(codec.defaultMediaType) // TODO: Is this correct?

    def decodeFromBody(body: Body)(implicit
      trace: Trace,
    ): IO[Throwable, ZStream[Any, Nothing, E]] = {
      ZIO.fromEither {
        codecForBody(codec, body).map { codec =>
          (body.asStream >>> codec.streamDecoder >>> validateStream(schema)).orDie
        }
      }
    }

    def encodeToBody(value: ZStream[Any, Nothing, E], mediaTypes: Chunk[MediaTypeWithQFactor])(implicit
      trace: Trace,
    ): Body = {
      val (mediaType, codec0) = codec.chooseFirst(mediaTypes)
      Body.fromStreamChunked(value >>> codec0.streamEncoder).contentType(mediaType)
    }

    type Element = E
  }

  private def codecForBody[E](codec: HttpContentCodec[E], body: Body) = {
    val mediaType = body.mediaType.getOrElse(codec.defaultMediaType)
    val codec0    = codec
      .lookup(mediaType)
      .toRight(HttpCodecError.CustomError(s"Unsupported MediaType ${body.mediaType}"))
    codec0
  }

  private[internal] def validateZIO[A](schema: Schema[A])(e: A)(implicit trace: Trace): ZIO[Any, HttpCodecError, A] = {
    val errors = Schema.validate(e)(schema)
    if (errors.isEmpty) ZIO.succeed(e)
    else ZIO.fail(HttpCodecError.InvalidEntity.wrap(errors))
  }

  private[internal] def validateStream[E](schema: Schema[E])(implicit
    trace: Trace,
  ): ZPipeline[Any, HttpCodecError, E, E] =
    ZPipeline.mapZIO(validateZIO(schema))

}
