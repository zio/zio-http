package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import zio.Chunk
import zio.stream.ZStream

/**
 * Content holder for Requests and Responses
 */

sealed trait HttpData[-R, +E, +A] { self =>
  def data[A1 >: A](implicit ev: HasData[A1]): ev.Out[R, E] = ev.data(self)
}
object HttpData                   {
  final case class CompleteContent(bytes: Chunk[Byte])               extends HttpData[Any, Nothing, Complete]
  final case class BufferedContent[R, E](bytes: ZStream[R, E, Byte]) extends HttpData[R, E, Buffered]
  case object EmptyContent                                           extends HttpData[Any, Nothing, Opaque]
  def fromBytes(bytes: Chunk[Byte]): HttpData[Any, Nothing, Complete]        = CompleteContent(bytes)
  def fromStream[R, E](bytes: ZStream[R, E, Byte]): HttpData[R, E, Buffered] = BufferedContent(bytes)
  def empty: HttpData[Any, Nothing, Opaque]                                  = EmptyContent
  def fromByteBuf(byteBuf: ByteBuf): HttpData[Any, Nothing, Complete] = {
    HttpData.CompleteContent(Chunk.fromArray(ByteBufUtil.getBytes(byteBuf)))
  }
}
