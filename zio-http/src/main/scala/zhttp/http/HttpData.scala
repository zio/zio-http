package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import zio.Chunk
import zio.stream.ZStream

/**
 * Content holder for Requests and Responses
 */
/*sealed trait HttpData[-R, +E] extends Product with Serializable

object HttpData {
  case object Empty                                            extends HttpData[Any, Nothing]
  final case class CompleteData(data: Chunk[Byte])             extends HttpData[Any, Nothing]
  final case class StreamData[R, E](data: ZStream[R, E, Byte]) extends HttpData[R, E]

  /**
 * Helper to create CompleteData from ByteBuf
 */
  def fromByteBuf(byteBuf: ByteBuf): HttpData[Any, Nothing] = {
    HttpData.CompleteData(Chunk.fromArray(ByteBufUtil.getBytes(byteBuf)))
  }

  /**
 * Helper to create StreamData from Stream of Chunks
 */
  def fromStream[R, E](data: ZStream[R, E, Byte]): HttpData[R, E] = HttpData.StreamData(data)

  /**
 * Helper to create Empty HttpData
 */
  def empty: HttpData[Any, Nothing] = Empty
}*/

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
