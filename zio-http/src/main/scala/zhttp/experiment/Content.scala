package zhttp.experiment

import io.netty.buffer.ByteBuf
import zhttp.http.HTTP_CHARSET
import zio.stream.ZStream
import zio.{Chunk, NeedsEnv}

import java.nio.charset.Charset

/**
 * Holds content that needs to be written on the HttpChannel
 */
sealed trait Content[-R, +E] { self =>
  def provide[R1 <: R](env: R)(implicit ev: NeedsEnv[R]): Content[Any, E] =
    self match {
      case Content.BinaryStream(data) => Content.BinaryStream(data.provide(env))
      case m                          => m.asInstanceOf[Content[Any, E]]
    }
}

object Content {
  private[zhttp] case object Empty                                              extends Content[Any, Nothing]
  private[zhttp] final case class Text(text: String, charset: Charset)          extends Content[Any, Nothing]
  private[zhttp] final case class Binary(data: Chunk[Byte])                     extends Content[Any, Nothing]
  private[zhttp] final case class BinaryN(data: ByteBuf)                        extends Content[Any, Nothing]
  private[zhttp] final case class BinaryStream[R, E](data: ZStream[R, E, Byte]) extends Content[R, E]

  /**
   * Helper to create content from ByteBuf
   */
  private[zhttp] def fromByteBuf(byteBuf: ByteBuf): Content[Any, Nothing] = Content.BinaryN(byteBuf)

  /**
   * Helper to create content from Stream of Chunks
   */
  def fromStream[R, E](data: ZStream[R, E, Byte]): Content[R, E] = Content.BinaryStream(data)

  /**
   * Helper to create empty content
   */
  def empty: Content[Any, Nothing] = Empty

  /**
   * Helper to create content from String
   */
  def fromText(data: String, charset: Charset = HTTP_CHARSET): Content[Any, Nothing] = Text(data, charset)

  /**
   * Helper to create content from chunk of bytes
   */
  def fromChunk(data: Chunk[Byte]): Content[Any, Nothing] = Binary(data)
}
