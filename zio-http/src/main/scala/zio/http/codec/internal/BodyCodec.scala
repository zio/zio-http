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
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.stream.ZStream

import zio.schema._
import zio.schema.codec.BinaryCodec

import zio.http.{Body, FormField, MediaType}

/**
 * A BodyCodec encapsulates the logic necessary to both encode and decode bodies
 * for a media type, using ZIO Schema codecs and schemas.
 */
private[internal] sealed trait BodyCodec[A] { self =>

  /**
   * The element type, described by the schema. This could be the type of the
   * whole request, if it's an RPC-style request with a single, relatively small
   * body, or it could be the type of a single element in a stream.
   */
  type Element

  /**
   * Attempts to decode the `A` from a body using the given codec.
   */
  def decodeFromBody(body: Body, codec: BinaryCodec[Element])(implicit trace: Trace): IO[Throwable, A]

  /**
   * Encodes the `A` to a body in the given codec.
   */
  def encodeToBody(value: A, codec: BinaryCodec[Element])(implicit trace: Trace): Body

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
private[internal] object BodyCodec {
  case object Empty extends BodyCodec[Unit] {
    type Element = Unit

    def decodeFromBody(body: Body, codec: BinaryCodec[Unit])(implicit trace: Trace): IO[Nothing, Unit] = ZIO.unit

    def encodeToBody(value: Unit, codec: BinaryCodec[Unit])(implicit trace: Trace): Body = Body.empty

    def schema: Schema[Unit] = Schema[Unit]

    def mediaType: Option[MediaType] = None

    def name: Option[String] = None
  }

  final case class Single[A](schema: Schema[A], mediaType: Option[MediaType], name: Option[String])
      extends BodyCodec[A] {
    def decodeFromBody(body: Body, codec: BinaryCodec[A])(implicit trace: Trace): IO[Throwable, A] = {
      if (schema == Schema[Unit]) ZIO.unit.asInstanceOf[IO[Throwable, A]]
      else
        body.asChunk.flatMap { chunk =>
          ZIO.fromEither(codec.decode(chunk))
        }
    }

    def encodeToBody(value: A, codec: BinaryCodec[A])(implicit trace: Trace): Body =
      Body.fromChunk(codec.encode(value))

    type Element = A
  }

  final case class Multiple[E](schema: Schema[E], mediaType: Option[MediaType], name: Option[String])
      extends BodyCodec[ZStream[Any, Nothing, E]] {
    def decodeFromBody(body: Body, codec: BinaryCodec[E])(implicit
      trace: Trace,
    ): IO[Throwable, ZStream[Any, Nothing, E]] =
      ZIO.succeed((body.asStream >>> codec.streamDecoder).orDie)

    def encodeToBody(value: ZStream[Any, Nothing, E], codec: BinaryCodec[E])(implicit trace: Trace): Body =
      Body.fromStream(value >>> codec.streamEncoder)

    type Element = E
  }
}
