package zhttp.http

import io.netty.buffer.{ByteBuf, Unpooled}
import zio.stream.ZStream
import zio.{Chunk, NeedsEnv, UIO, ZIO}

import java.io.{File, RandomAccessFile}
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.{ByteBuffer, file => jfile}

/**
 * Holds HttpData that needs to be written on the HttpChannel
 */
sealed trait HttpData[-R, +E] {
  self =>

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

  /**
   * Returns the size of HttpData if available
   */
  def size: Option[Long] = {
    val s = self.unsafeSize
    if (s < 0) None else Some(s)
  }

  def toByteBuf: ZIO[R, E, ByteBuf] = self match {
    case HttpData.Text(text, charset)  => UIO(Unpooled.copiedBuffer(text, charset))
    case HttpData.BinaryChunk(data)    => UIO(Unpooled.copiedBuffer(data.toArray))
    case HttpData.BinaryByteBuf(data)  => UIO(data)
    case HttpData.Empty                => UIO(Unpooled.EMPTY_BUFFER)
    case HttpData.BinaryStream(stream) => stream.fold(Unpooled.compositeBuffer())((c, b) => c.addComponent(b))
    case HttpData.File(path)           =>
      UIO {
        val aFile: RandomAccessFile = new RandomAccessFile(path.toString, "r")
        val inChannel: FileChannel  = aFile.getChannel
        val fileSize: Long          = inChannel.size
        val buffer: ByteBuffer      = ByteBuffer.allocate(fileSize.toInt)
        inChannel.read(buffer)
        inChannel.close()
        aFile.close()
        Unpooled.wrappedBuffer(buffer.flip)
      }
  }

  /**
   * Returns the size of HttpData if available and -1 if not
   */
  private[zhttp] def unsafeSize: Long = self match {
    case HttpData.Empty               => 0L
    case HttpData.Text(text, _)       => text.length.toLong
    case HttpData.BinaryChunk(data)   => data.size.toLong
    case HttpData.BinaryByteBuf(data) => data.readableBytes().toLong
    case HttpData.BinaryStream(_)     => -1L
    case HttpData.File(path)          => new RandomAccessFile(new File(path.toString), "r").length()
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

  def fromFile(path: jfile.Path): HttpData[Any, Nothing] = File(path)

  private[zhttp] sealed trait Cached {
    self =>
    def encode: ByteBuf = self match {
      case Text(text, charset) => Unpooled.copiedBuffer(text, charset)
      case BinaryChunk(data)   => Unpooled.copiedBuffer(data.toArray)
    }

    // TODO: Add Unit Tests
    def encodeAndCache(cache: Boolean): ByteBuf = {
      if (cache) {
        if (self.cache == null) {
          val buf = Unpooled.unreleasableBuffer(encode)
          self.cache = buf
          buf
        } else self.cache
      } else
        encode
    }

    var cache: ByteBuf = null
  }

  private[zhttp] final case class Text(text: String, charset: Charset) extends HttpData[Any, Nothing] with Cached

  private[zhttp] final case class BinaryChunk(data: Chunk[Byte]) extends HttpData[Any, Nothing] with Cached

  private[zhttp] final case class BinaryByteBuf(data: ByteBuf) extends HttpData[Any, Nothing]

  private[zhttp] final case class BinaryStream[R, E](stream: ZStream[R, E, ByteBuf]) extends HttpData[R, E]

  private[zhttp] final case class File(path: java.nio.file.Path) extends HttpData[Any, Nothing]

  private[zhttp] case object Empty extends HttpData[Any, Nothing]

}
