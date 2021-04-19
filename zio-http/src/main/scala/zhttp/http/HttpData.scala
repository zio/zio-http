package zhttp.http

import io.netty.buffer.ByteBuf
import zio.Chunk
import zio.stream.ZStream

/**
 * Content holder for Requests and Responses
 */
sealed trait HttpData[-R, +E] extends Product with Serializable

object HttpData {
  case object Empty                                            extends HttpData[Any, Nothing]
  final case class CompleteData(data: Chunk[Byte])             extends HttpData[Any, Nothing]
  final case class StreamData[R, E](data: ZStream[R, E, Byte]) extends HttpData[R, E]

  /**
   * Helper to create CompleteData from ByteBuf
   */
  def fromByteBuf(byteBuf: ByteBuf): HttpData[Any, Nothing] = {
    val bytes = new Array[Byte](byteBuf.readableBytes)
    byteBuf.readBytes(bytes)
    HttpData.CompleteData(Chunk.fromArray(bytes))
  }

  /**
   * Helper to create StreamData from Stream of Chunks
   */
  def fromStream[R, E](data: ZStream[R, E, Byte]): HttpData[R, E] = HttpData.StreamData(data)

  /**
   * Helper to create Empty HttpData
   */
  def empty: HttpData[Any, Nothing] = Empty
}
