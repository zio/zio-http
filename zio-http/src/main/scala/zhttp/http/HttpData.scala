package zhttp.http

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import zio.stream.ZStream
import zio.{Chunk, Task, UIO, ZIO}

import java.io.FileInputStream
import java.nio.charset.Charset

/**
 * Holds HttpData that needs to be written on the HttpChannel
 */
sealed trait HttpData { self =>

  /**
   * Returns true if HttpData is empty
   */
  final def isEmpty: Boolean = self match {
    case HttpData.Empty => true
    case _              => false
  }

  /**
   * Encodes the HttpData into a ByteBuf.
   */
  def toByteBuf: Task[ByteBuf]

  /**
   * Encodes the HttpData into a Stream of ByteBufs
   */
  def toByteBufStream: ZStream[Any, Throwable, ByteBuf]
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
  def fromFile(file: => java.io.File): HttpData = JavaFile(() => file)

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

  private def collectStream[R, E](stream: ZStream[R, E, ByteBuf]): ZIO[R, E, ByteBuf] =
    stream.fold(Unpooled.compositeBuffer()) { case (cmp, buf) => cmp.addComponent(true, buf) }

  private[zhttp] sealed trait Outgoing extends HttpData

  private[zhttp] final class UnsafeContent(private val httpContent: HttpContent) extends AnyVal {
    def content: ByteBuf = httpContent.content()

    def isLast: Boolean = httpContent.isInstanceOf[LastHttpContent]
  }

  private[zhttp] final class UnsafeChannel(private val ctx: ChannelHandlerContext) extends AnyVal {
    def read(): Unit = ctx.read(): Unit
  }

  private[zhttp] final case class Incoming(unsafeRun: (UnsafeChannel => UnsafeContent => Unit) => Unit)
      extends HttpData {

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def toByteBuf: Task[ByteBuf] = for {
      body <- ZIO.effectAsync[Any, Nothing, ByteBuf](cb =>
        unsafeRun(ch => {
          val buffer = Unpooled.compositeBuffer()
          msg => {
            buffer.addComponent(true, msg.content)
            if (msg.isLast) cb(UIO(buffer)) else ch.read()
          }
        }),
      )
    } yield body

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def toByteBufStream: ZStream[Any, Throwable, ByteBuf] =
      ZStream
        .effectAsync[Any, Nothing, ByteBuf](cb =>
          unsafeRun(ch =>
            msg => {
              cb(ZIO.succeed(Chunk(msg.content)))
              if (msg.isLast) cb(ZIO.fail(None)) else ch.read()
            },
          ),
        )
  }

  private[zhttp] final case class Text(text: String, charset: Charset) extends Outgoing {

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def toByteBuf: Task[ByteBuf] = UIO(Unpooled.copiedBuffer(text, charset))

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def toByteBufStream: ZStream[Any, Throwable, ByteBuf] = ZStream.fromEffect(toByteBuf)
  }

  private[zhttp] final case class BinaryChunk(data: Chunk[Byte]) extends Outgoing {

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def toByteBuf: Task[ByteBuf] = UIO(Unpooled.wrappedBuffer(data.toArray))

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def toByteBufStream: ZStream[Any, Throwable, ByteBuf] = ZStream.fromEffect(toByteBuf)
  }

  private[zhttp] final case class BinaryByteBuf(data: ByteBuf) extends Outgoing {

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def toByteBuf: Task[ByteBuf] = Task(data)

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def toByteBufStream: ZStream[Any, Throwable, ByteBuf] = ZStream.fromEffect(toByteBuf)
  }

  private[zhttp] final case class BinaryStream(stream: ZStream[Any, Throwable, ByteBuf]) extends Outgoing {

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def toByteBuf: Task[ByteBuf] = collectStream(toByteBufStream)

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def toByteBufStream: ZStream[Any, Throwable, ByteBuf] = stream
  }

  private[zhttp] final case class JavaFile(unsafeFile: () => java.io.File) extends Outgoing {

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def toByteBuf: Task[ByteBuf] = collectStream(toByteBufStream)

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def toByteBufStream: ZStream[Any, Throwable, ByteBuf] = ZStream.unwrap {
      for {
        file <- Task(unsafeFile())
        fs   <- Task(new FileInputStream(file))
        chunkSize = 4096
      } yield ZStream
        .repeatEffectChunkOption[Any, Throwable, ByteBuf] {
          val buffer = Unpooled.buffer(chunkSize)
          val len    = fs.read(buffer.array)
          if (len > 0) UIO(Chunk(buffer)) else ZIO.fail(None)
        }
        .ensuring(UIO(fs.close()))
    }
  }

  private[zhttp] case object Empty extends Outgoing {

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def toByteBuf: Task[ByteBuf] = UIO(Unpooled.EMPTY_BUFFER)

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def toByteBufStream: ZStream[Any, Throwable, ByteBuf] = ZStream.fromEffect(toByteBuf)
  }
}
