package zhttp.experiment

import io.netty.buffer.ByteBuf
import zhttp.http.HTTP_CHARSET
import zio.stream.ZStream
import zio.{Chunk, NeedsEnv}

import java.nio.charset.Charset

/**
 * Holds content that needs to be written on the HttpChannel
 */
sealed trait HttpData[-R, +E] { self =>
  def provide[R1 <: R](env: R)(implicit ev: NeedsEnv[R]): HttpData[Any, E] =
    self match {
      case HttpData.BinaryStream(data) => HttpData.BinaryStream(data.provide(env))
      case m                           => m.asInstanceOf[HttpData[Any, E]]
    }
}

object HttpData {
  private[zhttp] case object Empty                                                extends HttpData[Any, Nothing]
  private[zhttp] final case class Text(text: String, charset: Charset)            extends HttpData[Any, Nothing]
  private[zhttp] final case class Binary(data: Chunk[Byte])                       extends HttpData[Any, Nothing]
  private[zhttp] final case class BinaryN(data: ByteBuf)                          extends HttpData[Any, Nothing]
  private[zhttp] final case class BinaryStream[R, E](stream: ZStream[R, E, Byte]) extends HttpData[R, E]

  /**
   * Helper to create content from ByteBuf
   */
  private[zhttp] def fromByteBuf(byteBuf: ByteBuf): HttpData[Any, Nothing] = HttpData.BinaryN(byteBuf)

  /**
   * Helper to create content from Stream of Chunks
   */
  def fromStream[R, E](stream: ZStream[R, E, Byte]): HttpData[R, E] = HttpData.BinaryStream(stream)

  /**
   * Helper to create empty content
   */
  def empty: HttpData[Any, Nothing] = Empty

  /**
   * Helper to create content from String
   */
  def fromText(text: String, charset: Charset = HTTP_CHARSET): HttpData[Any, Nothing] = Text(text, charset)

  /**
   * Helper to create content from chunk of bytes
   */
  def fromChunk(data: Chunk[Byte]): HttpData[Any, Nothing] = Binary(data)
}
