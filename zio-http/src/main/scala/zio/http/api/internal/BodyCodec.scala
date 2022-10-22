package zio.http.api.internal

import zio._
import zio.http.Body
import zio.schema._
import zio.schema.codec.Codec
import zio.stream.ZStream

import java.io.IOException

/**
 * A BodyCodec encapsulates the logic necessary to both encode and decode bodies
 * for a media type, using ZIO Schema codecs and schemas.
 */
private[api] sealed trait BodyCodec[A] { self =>

  /**
   * The element type, described by the schema. This could be the type of the
   * whole request, if it's an RPC-style request with a single, relatively small
   * body, or it could be the type of a single element in a stream.
   */
  type Element

  /**
   * Attempts to decode the `A` from a body using the given codec.
   */
  def decodeFromBody(body: Body, codec: Codec): IO[Throwable, A]

  /**
   * Encodes the `A` to a body in the given codec.
   */
  def encodeToBody(value: A, codec: Codec): Body

  /**
   * Erases the type for easier use in the internal implementation.
   */
  final def erase: BodyCodec[Any] = self.asInstanceOf[BodyCodec[Any]]

  /**
   * The schema associated with the element type.
   */
  def schema: Schema[Element]
}
object BodyCodec {
  case object Empty extends BodyCodec[Unit] {
    type Element = Unit

    def decodeFromBody(body: Body, codec: Codec): IO[Throwable, Unit] = ZIO.unit

    def encodeToBody(value: Unit, codec: Codec): Body = Body.empty

    def schema: Schema[Unit] = Schema[Unit]
  }

  final case class Single[A](schema: Schema[A]) extends BodyCodec[A] {
    def decodeFromBody(body: Body, codec: Codec): IO[Throwable, A] =
      body.asChunk.flatMap(chunk =>
        ZIO.fromEither(codec.decode(schema)(chunk)).mapError(message => new IOException(message)),
      )

    def encodeToBody(value: A, codec: Codec): Body = {
      val chunk = codec.encode(schema)(value)
      Body.fromChunk(chunk)
    }

    type Element = A
  }

  final case class Multiple[E](schema: Schema[E]) extends BodyCodec[ZStream[Any, Throwable, E]] {
    def decodeFromBody(body: Body, codec: Codec): IO[Throwable, ZStream[Any, Throwable, E]] =
      ZIO.succeed(body.asStream >>> codec.decoder(schema).mapError(message => new IOException(message)))

    def encodeToBody(value: ZStream[Any, Throwable, E], codec: Codec): Body =
      Body.fromStream(value >>> codec.encoder(schema))

    type Element = E
  }
}
