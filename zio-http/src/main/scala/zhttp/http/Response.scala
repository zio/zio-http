package zhttp.http

import zhttp.socket.SocketApp
import zio.stream.ZStream
import zio.{Chunk, ZIO}

sealed trait Response[-R, +E, +A] {
  self =>
  def status(implicit ev: HasContent[A]): Status                 = ev.status(self)
  def headers(implicit ev: HasContent[A]): List[Header]          = ev.headers(self)
  def content(implicit ev: HasContent[A]): Content[R, E, ev.Out] = ev.content(self)
}

object Response extends ResponseHelpers {
  final case class Default[R, E, A](status: Status, headers: List[Header], content: Content[R, E, A])
      extends Response[R, E, A]
  final case class Socket[R, E](socket: SocketApp[R, E])                                 extends Response[R, E, Opaque]
  final case class Decode[R, E, A, B, C](d: DecodeMap[A, B], cb: B => Response[R, E, C]) extends Response[R, E, Nothing]
  final case class DecodeM[R, E, A, B, C](d: DecodeMap[A, B], cb: B => ZIO[R, Option[E], Response[R, E, C]])
      extends Response[R, E, Nothing]

  /**
   * Used to decode request body
   */
  sealed trait DecodeMap[-A, B]
  object DecodeMap {
    implicit object DecodeComplete extends DecodeMap[Complete, Chunk[Byte]]
    implicit object DecodeBuffered extends DecodeMap[Buffered, ZStream[Any, Nothing, Byte]]
  }
  def decode[R, E, A, B, C: HasContent](decoder: DecodeMap[A, B])(cb: B => Response[R, E, C]): Response[R, E, Nothing] =
    Decode(decoder, cb)
  def decodeComplete[R, E, C: HasContent](cb: Chunk[Byte] => Response[R, E, C]): Response[R, E, Nothing] =
    Decode(DecodeMap.DecodeComplete, cb)
  def decodeBuffered[R, E, C: HasContent](
    cb: ZStream[Any, Nothing, Byte] => Response[R, E, C],
  ): Response[R, E, Nothing]                                                                             = Decode(DecodeMap.DecodeBuffered, cb)
  def decodeCompleteM[R, E, C: HasContent](
    cb: Chunk[Byte] => ZIO[R, Option[E], Response[R, E, C]],
  ): Response[R, E, Nothing]                                                                             = DecodeM(DecodeMap.DecodeComplete, cb)
  def decodeBufferedM[R, E, C: HasContent](
    cb: ZStream[Any, Nothing, Byte] => ZIO[R, Option[E], Response[R, E, C]],
  ): Response[R, E, Nothing]                                                                             =
    DecodeM(DecodeMap.DecodeBuffered, cb)

  def apply[R, E, A](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    content: Content[R, E, A] = Content.empty,
  ): Response[R, E, A] =
    Default(status, headers, content)
}
