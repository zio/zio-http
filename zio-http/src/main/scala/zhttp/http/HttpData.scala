package zhttp.http

import io.netty.buffer.{ByteBuf, Unpooled}
import zio.blocking.Blocking.Service.live.effectBlocking
import zio.stream.ZStream
import zio.{Chunk, NeedsEnv, UIO, ZIO}

import java.nio.charset.Charset
import java.nio.file.Files

/**
 * Holds HttpData that needs to be written on the HttpChannel
 */
sealed trait HttpData[-R, +E] { self =>

  /**
   * Returns true if HttpData is a stream
   */
  def isChunked: Boolean = self match {
    case HttpData.BinaryStream(_) => true
    case _                        => false
  }

  /**
   * Returns true if HttpData is empty
   */
  def isEmpty: Boolean = self match {
    case HttpData.Empty => true
    case _              => false
  }

  def provide[R1 <: R](env: R)(implicit ev: NeedsEnv[R]): HttpData[Any, E] =
    self match {
      case HttpData.BinaryStream(data) => HttpData.BinaryStream(data.provide(env))
      case m                           => m.asInstanceOf[HttpData[Any, E]]
    }

  def toByteBuf: ZIO[R, Throwable, ByteBuf] = {
    self match {
      case HttpData.Text(text, charset)  => UIO(Unpooled.copiedBuffer(text, charset))
      case HttpData.BinaryChunk(data)    => UIO(Unpooled.copiedBuffer(data.toArray))
      case HttpData.BinaryByteBuf(data)  => UIO(data)
      case HttpData.Empty                => UIO(Unpooled.EMPTY_BUFFER)
      case HttpData.BinaryStream(stream) =>
        stream
          .asInstanceOf[ZStream[R, Throwable, ByteBuf]]
          .fold(Unpooled.compositeBuffer())((c, b) => c.addComponent(b))
      case HttpData.File(file)           =>
        effectBlocking {
          // The method ensures that the file is closed when all bytes have been read or an I/O error, or other runtime exception, is thrown.
          val fileContent = Files.readAllBytes(file.toPath)
          Unpooled.copiedBuffer(fileContent)
        }
    }
  }
}

object HttpData {

  /**
   * Helper to create empty HttpData
   */
  def empty: HttpData[Any, Nothing] = Empty

  /**
   * Helper to create HttpData from ByteBuf
   */
  def fromByteBuf(byteBuf: ByteBuf): HttpData[Any, Nothing] = HttpData.BinaryByteBuf(byteBuf)

  /**
   * Helper to create HttpData from chunk of bytes
   */
  def fromChunk(data: Chunk[Byte]): HttpData[Any, Nothing] = BinaryChunk(data)

  /**
   * Helper to create HttpData from Stream of bytes
   */
  def fromStream[R, E](stream: ZStream[R, E, Byte]): HttpData[R, E] =
    HttpData.BinaryStream(stream.mapChunks(chunks => Chunk(Unpooled.copiedBuffer(chunks.toArray))))

  /**
   * Helper to create HttpData from Stream of string
   */
  def fromStream[R, E](stream: ZStream[R, E, String], charset: Charset = HTTP_CHARSET): HttpData[R, E] =
    HttpData.BinaryStream(stream.map(str => Unpooled.copiedBuffer(str, charset)))

  /**
   * Helper to create HttpData from String
   */
  def fromString(text: String, charset: Charset = HTTP_CHARSET): HttpData[Any, Nothing] = Text(text, charset)

  /**
   * Helper to create HttpData from contents of a file
   */
  def fromFile(file: java.io.File): HttpData[Any, Nothing] = File(file)

  private[zhttp] final case class Text(text: String, charset: Charset)               extends HttpData[Any, Nothing]
  private[zhttp] final case class BinaryChunk(data: Chunk[Byte])                     extends HttpData[Any, Nothing]
  private[zhttp] final case class BinaryByteBuf(data: ByteBuf)                       extends HttpData[Any, Nothing]
  private[zhttp] final case class BinaryStream[R, E](stream: ZStream[R, E, ByteBuf]) extends HttpData[R, E]
  private[zhttp] final case class File(file: java.io.File)                           extends HttpData[Any, Nothing]
  private[zhttp] case object Empty                                                   extends HttpData[Any, Nothing]
}
