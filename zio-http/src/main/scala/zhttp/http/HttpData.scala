package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import io.netty.util.AsciiString
import zhttp.service.HTTP_CONTENT_HANDLER
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

  private[zhttp] final def toByteBuf: Task[ByteBuf] = {
    self match {
      case HttpData.Incoming(unsafeRun) =>
        for {
          buffer <- UIO(Unpooled.compositeBuffer())
          body   <- ZIO.effectAsync[Any, Throwable, ByteBuf](cb =>
            unsafeRun(ch =>
              msg => {
                buffer.writeBytes(msg.content)
                if (msg.isLast) {
                  cb(UIO(buffer) ensuring UIO(ch.ctx.pipeline().remove(HTTP_CONTENT_HANDLER)))
                } else {
                  ch.read()
                  ()
                }
                msg.content.release(msg.content.refCnt()): Unit
              },
            ),
          )
        } yield body
      case outgoing: HttpData.Outgoing  =>
        outgoing match {
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
  }

  private[zhttp] final def toByteBufStream: ZStream[Any, Throwable, ByteBuf] = self match {
    case HttpData.Incoming(unsafeRun) =>
      ZStream
        .effectAsync[Any, Throwable, ByteBuf](cb =>
          unsafeRun(ch =>
            msg => {
              cb(ZIO.succeed(Chunk(msg.content)))
              if (msg.isLast) {
                ch.ctx.pipeline().remove(HTTP_CONTENT_HANDLER)
                cb(ZIO.fail(None))
              } else {
                ch.read()
              }
            },
          ),
        )
    case outgoing: HttpData.Outgoing  => ZStream.fromEffect(outgoing.toByteBuf)
  }
  final def toCharSequenceStream: ZStream[Any, Throwable, CharSequence]      = toByteBufStream.map(buf => {
    val data = AsciiString.cached(buf.toString(HTTP_CHARSET))
    buf.release(buf.refCnt())
    data
  })

  final def toStreamBytes: ZStream[Any, Throwable, Byte] =
    toByteBufStream
      .map(buf => {
        val data = Chunk.fromArray(ByteBufUtil.getBytes(buf))
        buf.release(buf.refCnt())
        data
      })
      .flattenChunks

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
   * Helper to create HttpData from Stream of bytes
   */
  def fromStream(stream: ZStream[Any, Throwable, Byte]): HttpData =
    HttpData.BinaryStream(stream.mapChunks(chunks => Chunk(Unpooled.wrappedBuffer(chunks.toArray))))

  /**
   * Helper to create HttpData from Stream of string
   */
  def fromStream(stream: ZStream[Any, Throwable, CharSequence], charset: Charset = HTTP_CHARSET): HttpData =
    HttpData.BinaryStream(stream.map(str => Unpooled.wrappedBuffer(str.toString.getBytes(charset))))

  /**
   * Helper to create HttpData from String
   */
  def fromString(text: String, charset: Charset = HTTP_CHARSET): HttpData = Text(text, charset)

  /**
   * Helper to create HttpData from contents of a file
   */
  def fromFile(file: => java.io.File): HttpData = {
    RandomAccessFile(() => new java.io.RandomAccessFile(file, "r"))
  }

  private[zhttp] final case class UnsafeContent(private val httpContent: HttpContent) extends AnyVal {
    def isLast: Boolean  = httpContent.isInstanceOf[LastHttpContent]
    def content: ByteBuf = httpContent.content()
  }

  private[zhttp] final case class UnsafeChannel(ctx: ChannelHandlerContext) extends AnyVal {
    def read(): Unit = ctx.read(): Unit
  }

  private[zhttp] final case class Incoming(unsafeRun: (UnsafeChannel => UnsafeContent => Unit) => Unit) extends HttpData
  private[zhttp] sealed trait Outgoing                                                                  extends HttpData
  private[zhttp] final case class Text(text: String, charset: Charset)                                  extends Outgoing
  private[zhttp] final case class BinaryChunk(data: Chunk[Byte])                                        extends Outgoing
  private[zhttp] final case class BinaryByteBuf(data: ByteBuf)                                          extends Outgoing
  private[zhttp] final case class BinaryStream(stream: ZStream[Any, Throwable, ByteBuf])                extends Outgoing
  private[zhttp] final case class RandomAccessFile(unsafeGet: () => java.io.RandomAccessFile)           extends Outgoing
  private[zhttp] case object Empty                                                                      extends Outgoing
}
