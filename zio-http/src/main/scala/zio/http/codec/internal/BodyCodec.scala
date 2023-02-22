package zio.http.codec.internal

import java.io.IOException

import zio._

import zio.stream.ZStream

import zio.schema._
import zio.schema.codec.{BinaryCodec, Codec, DecodeError}

import zio.http.Body

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
  def decodeFromBody(body: Body, codec: BinaryCodec[Element]): IO[Throwable, A]

  /**
   * Encodes the `A` to a body in the given codec.
   */
  def encodeToBody(value: A, codec: BinaryCodec[Element]): Body

  /**
   * Erases the type for easier use in the internal implementation.
   */
  final def erase: BodyCodec[Any] = self.asInstanceOf[BodyCodec[Any]]

  /**
   * The schema associated with the element type.
   */
  def schema: Schema[Element]
}
private[internal] object BodyCodec {
  case object Empty extends BodyCodec[Unit] {
    type Element = Unit

    def decodeFromBody(body: Body, codec: BinaryCodec[Unit]): IO[Nothing, Unit] = ZIO.unit

    def encodeToBody(value: Unit, codec: BinaryCodec[Unit]): Body = Body.empty

    def schema: Schema[Unit] = Schema[Unit]
  }

  final case class Single[A](schema: Schema[A]) extends BodyCodec[A] {
    def decodeFromBody(body: Body, codec: BinaryCodec[A]): IO[Throwable, A] = {
      if (schema == Schema[Unit]) ZIO.unit.asInstanceOf[IO[Throwable, A]]
      else
        body.asChunk.flatMap { chunk =>
          ZIO.fromEither(codec.decode(chunk))
        }
    }

    def encodeToBody(value: A, codec: BinaryCodec[A]): Body =
      Body.fromChunk(codec.encode(value))

    type Element = A
  }

  final case class Multiple[E](schema: Schema[E]) extends BodyCodec[ZStream[Any, Nothing, E]] {
    def decodeFromBody(body: Body, codec: BinaryCodec[E]): IO[Throwable, ZStream[Any, Nothing, E]] =
      ZIO.succeed((body.asStream >>> codec.streamDecoder).orDie)

    def encodeToBody(value: ZStream[Any, Nothing, E], codec: BinaryCodec[E]): Body =
      Body.fromStream(value >>> codec.streamEncoder)

    type Element = E
  }
}
