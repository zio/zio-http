package zhttp.http

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import zio.blocking.Blocking.Service.live.effectBlocking
import zio.stream.ZStream
import zio.{Chunk, Task, UIO, ZIO}

import java.io.FileInputStream
import java.nio.charset.Charset

/**
 * Holds HttpData that needs to be written on the HttpChannel
 */
sealed trait HttpData { self =>

  /**
   * Returns true if HttpData is a stream
   */
  final def isChunked: Boolean = self match {
    case HttpData.BinaryStream(_) => true
    case _                        => false
  }

  /**
   * Returns true if HttpData is empty
   */
  final def isEmpty: Boolean = self match {
    case HttpData.Empty => true
    case _              => false
  }

  final def toByteBuf: Task[ByteBuf] = {
    self match {
      case self: HttpData.Incoming => self.encode
      case self: HttpData.Outgoing => self.encode
    }
  }

  final def toByteBufStream: ZStream[Any, Throwable, ByteBuf] = self match {
    case self: HttpData.Incoming => self.encodeAsStream
    case self: HttpData.Outgoing => ZStream.fromEffect(self.encode)
  }
}

object HttpData {

  /**
   * Helper to create empty HttpData
   */
  def empty: HttpData = Empty

  /**
   * Helper to create HttpData from ByteBuf
   */
  def fromByteBuf(byteBuf: ByteBuf): HttpData = HttpData.BinaryByteBuf(byteBuf)

  /**
   * Helper to create HttpData from chunk of bytes
   */
  def fromChunk(data: Chunk[Byte]): HttpData = BinaryChunk(data)

  /**
   * Helper to create HttpData from contents of a file
   */
  def fromFile(file: => java.io.File): HttpData = {
    RandomAccessFile(() => new java.io.RandomAccessFile(file, "r"))
  }

  /**
   * Helper to create HttpData from Stream of string
   */
  def fromStream(stream: ZStream[Any, Throwable, CharSequence], charset: Charset = HTTP_CHARSET): HttpData =
    HttpData.BinaryStream(stream.map(str => Unpooled.wrappedBuffer(str.toString.getBytes(charset))))

  /**
   * Helper to create HttpData from Stream of bytes
   */
  def fromStream(stream: ZStream[Any, Throwable, Byte]): HttpData =
    HttpData.BinaryStream(stream.mapChunks(chunks => Chunk(Unpooled.wrappedBuffer(chunks.toArray))))

  /**
   * Helper to create HttpData from String
   */
  def fromString(text: String, charset: Charset = HTTP_CHARSET): HttpData = Text(text, charset)

  private[zhttp] sealed trait Outgoing extends HttpData { self =>
    def encode: ZIO[Any, Throwable, ByteBuf] =
      self match {
        case HttpData.Text(text, charset)   => UIO(Unpooled.copiedBuffer(text, charset))
        case HttpData.BinaryChunk(data)     => UIO(Unpooled.copiedBuffer(data.toArray))
        case HttpData.BinaryByteBuf(data)   => UIO(data)
        case HttpData.Empty                 => UIO(Unpooled.EMPTY_BUFFER)
        case HttpData.BinaryStream(stream)  =>
          stream
            .asInstanceOf[ZStream[Any, Throwable, ByteBuf]]
            .fold(Unpooled.compositeBuffer())((c, b) => c.addComponent(b))
        case HttpData.RandomAccessFile(raf) =>
          effectBlocking {
            val fis                      = new FileInputStream(raf().getFD)
            val fileContent: Array[Byte] = new Array[Byte](raf().length().toInt)
            fis.read(fileContent)
            Unpooled.copiedBuffer(fileContent)
          }
      }
  }

  private[zhttp] final class UnsafeContent(private val httpContent: HttpContent) extends AnyVal {
    def content: ByteBuf = httpContent.content()

    def isLast: Boolean = httpContent.isInstanceOf[LastHttpContent]
  }

  private[zhttp] final class UnsafeChannel(private val ctx: ChannelHandlerContext) extends AnyVal {
    def read(): Unit = ctx.read(): Unit
  }

  private[zhttp] final case class Incoming(unsafeRun: (UnsafeChannel => UnsafeContent => Unit) => Unit)
      extends HttpData {
    def encode: ZIO[Any, Nothing, ByteBuf] = for {
      body <- ZIO.effectAsync[Any, Nothing, ByteBuf](cb =>
        unsafeRun(ch => {
          val buffer = Unpooled.compositeBuffer()
          msg => {
            buffer.writeBytes(msg.content)
            if (msg.isLast) cb(UIO(buffer)) else ch.read()
            msg.content.release(msg.content.refCnt()): Unit
          }
        }),
      )
    } yield body

    def encodeAsStream: ZStream[Any, Nothing, ByteBuf] = ZStream
      .effectAsync[Any, Nothing, ByteBuf](cb =>
        unsafeRun(ch =>
          msg => {
            cb(ZIO.succeed(Chunk(msg.content)))
            if (msg.isLast) cb(ZIO.fail(None)) else ch.read()
          },
        ),
      )
  }

  private[zhttp] final case class Text(text: String, charset: Charset)                        extends Outgoing
  private[zhttp] final case class BinaryChunk(data: Chunk[Byte])                              extends Outgoing
  private[zhttp] final case class BinaryByteBuf(data: ByteBuf)                                extends Outgoing
  private[zhttp] final case class BinaryStream(stream: ZStream[Any, Throwable, ByteBuf])      extends Outgoing
  private[zhttp] final case class RandomAccessFile(unsafeGet: () => java.io.RandomAccessFile) extends Outgoing
  private[zhttp] case object Empty                                                            extends Outgoing
}
