package zhttp.http

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import io.netty.util.AsciiString
import zhttp.http.HttpData.ByteBufConfig
import zio._
import zio.stream.ZStream

import java.io.FileInputStream
import java.nio.charset.Charset

/**
 * Holds HttpData that needs to be written on the HttpChannel
 */
sealed trait HttpData { self =>

  /**
   * Encodes the HttpData into a ByteBuf. Takes in ByteBufConfig to have a more
   * fine grained control over the encoding.
   */
  def toByteBuf(config: ByteBufConfig): Task[ByteBuf]

  /**
   * Encodes the HttpData into a Stream of ByteBufs. Takes in ByteBufConfig to
   * have a more fine grained control over the encoding.
   */
  def toByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf]

  /**
   * Encodes the HttpData into a Http of ByeBuf. This could be more performant
   * in certain cases. Takes in ByteBufConfig to have a more fine grained
   * control over the encoding.
   */
  def toHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf]

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
  final def toByteBuf: Task[ByteBuf] = toByteBuf(ByteBufConfig.default)

  /**
   * Encodes the HttpData into a Stream of ByteBufs
   */
  final def toByteBufStream: ZStream[Any, Throwable, ByteBuf] = toByteBufStream(ByteBufConfig.default)

  /**
   * A bit more efficient version of toByteBuf in certain cases
   */
  final def toHttp: Http[Any, Throwable, Any, ByteBuf] = toHttp(ByteBufConfig.default)
}

object HttpData {

  private def collectStream[R, E](stream: ZStream[R, E, ByteBuf]): ZIO[R, E, ByteBuf] =
    stream.fold(Unpooled.compositeBuffer()) { case (cmp, buf) => cmp.addComponent(true, buf) }

  /**
   * Helper to create empty HttpData
   */
  def empty: HttpData = Empty

  /**
   * Helper to create HttpData from AsciiString
   */
  def fromAsciiString(asciiString: AsciiString): HttpData = FromAsciiString(asciiString)

  /**
   * Helper to create HttpData from ByteBuf
   */
  def fromByteBuf(byteBuf: ByteBuf): HttpData = HttpData.BinaryByteBuf(byteBuf)

  /**
   * Helper to create HttpData from CharSequence
   */
  def fromCharSequence(charSequence: CharSequence, charset: Charset = HTTP_CHARSET): HttpData =
    fromAsciiString(new AsciiString(charSequence, charset))

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
  def fromString(text: String, charset: Charset = HTTP_CHARSET): HttpData = fromCharSequence(text, charset)

  private[zhttp] sealed trait Complete extends HttpData

  /**
   * Provides a more fine grained control while encoding HttpData into ByteBUfs
   */
  case class ByteBufConfig(chunkSize: Int = 1024 * 4) {
    def chunkSize(fileLength: Long): Int = {
      val actualInt = fileLength.toInt
      if (actualInt < 0) chunkSize
      else if (actualInt < chunkSize) actualInt
      else chunkSize
    }
  }

  private[zhttp] final class UnsafeContent(private val httpContent: HttpContent) extends AnyVal {
    def content: ByteBuf = httpContent.content()

    def isLast: Boolean = httpContent.isInstanceOf[LastHttpContent]
  }

  private[zhttp] trait UnsafeReadableChannel {
    def read(): Unit
  }
  private[zhttp] final class UnsafeChannel(private val ctx: ChannelHandlerContext) extends UnsafeReadableChannel {
    def read(): Unit = ctx.read(): Unit
  }

  private[zhttp] final case class UnsafeAsync(unsafeRun: (UnsafeReadableChannel => UnsafeContent => Unit) => Unit)
      extends HttpData {

    private def toUnsafeContentQueue: ZIO[Any, Nothing, Queue[UnsafeContent]] = {
      for {
        queue   <- ZQueue.bounded[UnsafeContent](1)
        promise <- Promise.make[Nothing, UnsafeReadableChannel]
        runtime <- ZIO.runtime[Any]
        _       <- UIO(
          unsafeRun { ch =>
            runtime.unsafeRun(promise.succeed(ch))
            msg => {
              runtime.unsafeRun(queue.offer(msg)): Unit
            }
          },
        )
        ch      <- promise.await
      } yield queue.mapM { msg =>
        UIO(ch.read()).unless(msg.isLast).as(msg)
      }
    }

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def toByteBuf(config: ByteBufConfig): Task[ByteBuf] = for {
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
    override def toByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.unwrap(toUnsafeContentQueue.map(ZStream.fromQueue(_))).takeUntil(_.isLast).map(_.content)

    override def toHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf] =
      Http.fromZIO(toByteBuf(config))
  }

  private[zhttp] case class FromAsciiString(asciiString: AsciiString) extends Complete {

    private def encode: ByteBuf = Unpooled.wrappedBuffer(asciiString.array())

    /**
     * Encodes the HttpData into a ByteBuf. Takes in ByteBufConfig to have a
     * more fine grained control over the encoding.
     */
    override def toByteBuf(config: ByteBufConfig): Task[ByteBuf] = Task(encode)

    /**
     * Encodes the HttpData into a Stream of ByteBufs. Takes in ByteBufConfig to
     * have a more fine grained control over the encoding.
     */
    override def toByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.fromEffect(toByteBuf(config))

    /**
     * Encodes the HttpData into a Http of ByeBuf. This could be more performant
     * in certain cases. Takes in ByteBufConfig to have a more fine grained
     * control over the encoding.
     */
    override def toHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf] = Http.attempt(encode)
  }

  private[zhttp] final case class BinaryChunk(data: Chunk[Byte]) extends Complete {

    private def encode = Unpooled.wrappedBuffer(data.toArray)

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def toByteBuf(config: ByteBufConfig): Task[ByteBuf] = UIO(encode)

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def toByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.fromEffect(toByteBuf(config))

    override def toHttp(config: ByteBufConfig): UHttp[Any, ByteBuf] = Http.succeed(encode)
  }

  private[zhttp] final case class BinaryByteBuf(data: ByteBuf) extends Complete {

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def toByteBuf(config: ByteBufConfig): Task[ByteBuf] = Task(data)

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def toByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.fromEffect(toByteBuf(config))

    override def toHttp(config: ByteBufConfig): UHttp[Any, ByteBuf] = Http.succeed(data)
  }

  private[zhttp] final case class BinaryStream(stream: ZStream[Any, Throwable, ByteBuf]) extends Complete {

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def toByteBuf(config: ByteBufConfig): Task[ByteBuf] =
      collectStream(toByteBufStream(config))

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def toByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      stream

    override def toHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf] =
      Http.fromZIO(toByteBuf(config))
  }

  private[zhttp] final case class JavaFile(unsafeFile: () => java.io.File) extends Complete {

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def toByteBuf(config: ByteBufConfig): Task[ByteBuf] =
      collectStream(toByteBufStream(config))

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def toByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.unwrap {
        for {
          file <- Task(unsafeFile())
          fs   <- Task(new FileInputStream(file))
          size   = config.chunkSize(file.length())
          buffer = new Array[Byte](size)
        } yield ZStream
          .repeatEffectOption[Any, Throwable, ByteBuf] {
            for {
              len   <- Task(fs.read(buffer)).mapError(Some(_))
              bytes <- if (len > 0) UIO(Unpooled.copiedBuffer(buffer, 0, len)) else ZIO.fail(None)
            } yield bytes
          }
          .ensuring(UIO(fs.close()))
      }

    override def toHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf] =
      Http.fromZIO(toByteBuf(config))
  }

  object ByteBufConfig {
    val default: ByteBufConfig = ByteBufConfig()
  }

  private[zhttp] case object Empty extends Complete {

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def toByteBuf(config: ByteBufConfig): Task[ByteBuf] = UIO(Unpooled.EMPTY_BUFFER)

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def toByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.fromEffect(toByteBuf(config))

    override def toHttp(config: ByteBufConfig): UHttp[Any, ByteBuf] = Http.empty
  }

}
