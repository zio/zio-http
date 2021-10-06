package zhttp.http

import io.netty.buffer.ByteBuf
import zhttp.socket.SocketApp
import zio.Chunk
import zio.stream.ZStream

import java.nio.charset.Charset

/**
 * Content holder for and Responses
 */
sealed trait HttpData[-R, +E] extends Product with Serializable

object HttpData {
  private[zhttp] case object Empty                                              extends HttpData[Any, Nothing]
  private[zhttp] final case class Text(text: String, charset: Charset)          extends HttpData[Any, Nothing]
  private[zhttp] final case class Binary(data: Chunk[Byte])                     extends HttpData[Any, Nothing]
  private[zhttp] final case class BinaryN(data: ByteBuf)                        extends HttpData[Any, Nothing]
  private[zhttp] final case class BinaryStream[R, E](data: ZStream[R, E, Byte]) extends HttpData[R, E]
  private[zhttp] final case class Socket[R, E](app: SocketApp[R, E])            extends HttpData[R, E]

  /**
   * Helper to create CompleteData from ByteBuf
   */
  private[zhttp] def fromByteBuf(byteBuf: ByteBuf): HttpData[Any, Nothing] = HttpData.BinaryN(byteBuf)

  /**
   * Helper to create StreamData from Stream of Chunks
   */
  def fromStream[R, E](data: ZStream[R, E, Byte]): HttpData[R, E] = HttpData.BinaryStream(data)

  /**
   * Helper to create Empty HttpData
   */
  def empty: HttpData[Any, Nothing] = Empty

  def fromText(data: String, charset: Charset = HTTP_CHARSET): HttpData[Any, Nothing] = Text(data, charset)

  def fromChunk(data: Chunk[Byte]): HttpData[Any, Nothing] = Binary(data)

  def fromSocket[R, E](socketApp: SocketApp[R, E]): HttpData.Socket[R, E] = Socket(socketApp)
}
