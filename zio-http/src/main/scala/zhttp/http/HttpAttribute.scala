package zhttp.http

import io.netty.buffer.ByteBuf
import zhttp.socket.SocketApp
import zio.Chunk
import zio.stream.ZStream

import java.nio.charset.Charset

/**
 * Content holder for and Responses
 */
private[zhttp] sealed trait HttpAttribute[-R, +E] extends Product with Serializable

object HttpAttribute {
  private[zhttp] case object Empty                                           extends HttpAttribute[Any, Nothing]
  private[zhttp] final case class Socket[R, E](app: SocketApp[R, E])         extends HttpAttribute[R, E]
  private[zhttp] final case class HttpContent[R, E](content: HttpData[R, E]) extends HttpAttribute[R, E]

  /**
   * Helper to create Attribute from ByteBuf
   */
  private[zhttp] def fromByteBuf(byteBuf: ByteBuf): HttpAttribute[Any, Nothing] =
    HttpAttribute.HttpContent(HttpData.fromByteBuf(byteBuf))

  /**
   * Helper to Attribute from a stream of bytes
   */
  def fromStream[R, E](data: ZStream[R, E, Byte]): HttpAttribute[R, E] =
    HttpAttribute.HttpContent(HttpData.fromStream(data))

  /**
   * Helper to create an empty HttpData
   */
  def empty: HttpAttribute[Any, Nothing] = Empty

  /**
   * Helper to create Attribute from String
   */
  def fromText(data: String, charset: Charset = HTTP_CHARSET): HttpAttribute[Any, Nothing] =
    HttpAttribute.HttpContent(HttpData.fromText(data, charset))

  /**
   * Helper to create Attribute from a Chunk of Bytes
   */
  def fromChunk(data: Chunk[Byte]): HttpAttribute[Any, Nothing] =
    HttpAttribute.HttpContent(HttpData.fromChunk(data))

  /**
   * Helper to create Attribute from a SocketApp
   */
  def fromSocket[R, E](socketApp: SocketApp[R, E]): HttpAttribute[R, E] = Socket(socketApp)

  /**
   * Helper to create Attribute from Content
   */
  private[zhttp] def fromContent[R, E](content: HttpData[R, E]): HttpAttribute[R, E] = HttpContent(content)
}
