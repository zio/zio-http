package zhttp.http

import io.netty.buffer.ByteBuf
import zhttp.experiment.Content
import zhttp.socket.SocketApp
import zio.Chunk
import zio.stream.ZStream

import java.nio.charset.Charset

/**
 * Content holder for and Responses
 */
sealed trait HttpData[-R, +E] extends Product with Serializable

object HttpData {
  private[zhttp] case object Empty                                          extends HttpData[Any, Nothing]
  private[zhttp] final case class Socket[R, E](app: SocketApp[R, E])        extends HttpData[R, E]
  private[zhttp] final case class HttpContent[R, E](content: Content[R, E]) extends HttpData[R, E]

  /**
   * Helper to create HttpData from ByteBuf
   */
  private[zhttp] def fromByteBuf(byteBuf: ByteBuf): HttpData[Any, Nothing] =
    HttpData.HttpContent(Content.fromByteBuf(byteBuf))

  /**
   * Helper to HttpData from a stream of bytes
   */
  def fromStream[R, E](data: ZStream[R, E, Byte]): HttpData[R, E] =
    HttpData.HttpContent(Content.fromStream(data))

  /**
   * Helper to create an empty HttpData
   */
  def empty: HttpData[Any, Nothing] = Empty

  /**
   * Helper to create HttpData from String
   */
  def fromText(data: String, charset: Charset = HTTP_CHARSET): HttpData[Any, Nothing] =
    HttpData.HttpContent(Content.fromText(data, charset))

  /**
   * Helper to create HttpData from a Chunk of Bytes
   */
  def fromChunk(data: Chunk[Byte]): HttpData[Any, Nothing] =
    HttpData.HttpContent(Content.fromChunk(data))

  /**
   * Helper to create HttpData from a SocketApp
   */
  def fromSocket[R, E](socketApp: SocketApp[R, E]): HttpData[R, E] = Socket(socketApp)

  /**
   * Helper to create HttpData from Content
   */
  private[zhttp] def fromContent[R, E](content: Content[R, E]): HttpData[R, E] = HttpContent(content)
}
