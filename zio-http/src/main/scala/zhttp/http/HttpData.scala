package zhttp.http

import io.netty.buffer.ByteBuf
import zio.stream.ZStream
import zio.{Chunk, NeedsEnv}

import java.nio.charset.Charset

/**
 * Holds HttpData that needs to be written on the HttpChannel
 */
sealed trait HttpData[-R, +E] { self =>
  def provide[R1 <: R](env: R)(implicit ev: NeedsEnv[R]): HttpData[Any, E] =
    self match {
      case HttpData.BinaryStream(data) => HttpData.BinaryStream(data.provide(env))
      case m                           => m.asInstanceOf[HttpData[Any, E]]
    }

  /**
   * Returns the size of HttpData if available
   */
  def size: Option[Long] = {
    val s = self.unsafeSize
    if (s < 0) None else Some(s)
  }

  /**
   * Returns the size of HttpData if available and -1 if not
   */
  private[zhttp] def unsafeSize: Long = self match {
    case HttpData.Empty           => 0L
    case HttpData.Text(text, _)   => text.length.toLong
    case HttpData.Binary(data)    => data.size.toLong
    case HttpData.BinaryN(data)   => data.readableBytes().toLong
    case HttpData.BinaryStream(_) => -1L
  }

  /**
   * Returns true if HttpData is empty
   */
  def isEmpty: Boolean = self match {
    case HttpData.Empty => true
    case _              => false
  }

  /**
   * Returns true if HttpData is a stream
   */
  def isChunked: Boolean = self match {
    case HttpData.BinaryStream(_) => true
    case _                        => false
  }
}

object HttpData {
  private[zhttp] case object Empty                                                extends HttpData[Any, Nothing]
  private[zhttp] final case class Text(text: String, charset: Charset)            extends HttpData[Any, Nothing]
  private[zhttp] final case class Binary(data: Chunk[Byte])                       extends HttpData[Any, Nothing]
  private[zhttp] final case class BinaryN(data: ByteBuf)                          extends HttpData[Any, Nothing]
  private[zhttp] final case class BinaryStream[R, E](stream: ZStream[R, E, Byte]) extends HttpData[R, E]

  /**
   * Helper to create HttpData from ByteBuf
   */
  def fromByteBuf(byteBuf: ByteBuf): HttpData[Any, Nothing] = HttpData.BinaryN(byteBuf)

  /**
   * Helper to create HttpData from Stream of Chunks
   */
  def fromStream[R, E](stream: ZStream[R, E, Byte]): HttpData[R, E] = HttpData.BinaryStream(stream)

  /**
   * Helper to create empty HttpData
   */
  def empty: HttpData[Any, Nothing] = Empty

  /**
   * Helper to create HttpData from String
   */
  def fromText(text: String, charset: Charset = HTTP_CHARSET): HttpData[Any, Nothing] = Text(text, charset)

  /**
   * Helper to create HttpData from chunk of bytes
   */
  def fromChunk(data: Chunk[Byte]): HttpData[Any, Nothing] = Binary(data)
}
