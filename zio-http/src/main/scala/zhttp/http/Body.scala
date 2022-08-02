package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import io.netty.util.AsciiString
import zhttp.http.Body.ByteBufConfig
import zhttp.service.Ctx
import zio._
import zio.stream.ZStream

import java.io.FileInputStream
import java.nio.charset.Charset

/**
 * Holds Body that needs to be written on the HttpChannel
 */
sealed trait Body { self =>

  final def asByteArray: Task[Array[Byte]] =
    asByteBuf.flatMap(buf => ZIO.attempt(ByteBufUtil.getBytes(buf)))

  /**
   * Encodes the Body into a ByteBuf. Takes in ByteBufConfig to have a more fine
   * grained control over the encoding.
   */
  def asByteBuf(config: ByteBufConfig): Task[ByteBuf]

  /**
   * Encodes the Body into a ByteBuf.
   */
  final def asByteBuf: Task[ByteBuf] =
    asByteBuf(ByteBufConfig.default)

  /**
   * Encodes the Body into a Stream of ByteBufs. Takes in ByteBufConfig to have
   * a more fine grained control over the encoding.
   */
  def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf]

  /**
   * Encodes the Body into a Stream of ByteBufs
   */
  final def asByteBufStream: ZStream[Any, Throwable, ByteBuf] = asByteBufStream(ByteBufConfig.default)

  /**
   * Decodes the content of request as a Chunk of Bytes
   */
  final def asByteChunk: Task[Chunk[Byte]] =
    asByteArray.map(Chunk.fromArray)

  /**
   * Decodes the content of request as CharSequence
   */
  final def asCharSeq: ZIO[Any, Throwable, CharSequence] =
    asByteArray.map { buf => new AsciiString(buf, false) }

  /**
   * Encodes the Body into a Http of ByeBuf. This could be more performant in
   * certain cases. Takes in ByteBufConfig to have a more fine grained control
   * over the encoding.
   */
  def asHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf]

  /**
   * A bit more efficient version of toByteBuf in certain cases
   */
  final def asHttp: Http[Any, Throwable, Any, ByteBuf] = asHttp(ByteBufConfig.default)

  /**
   * Decodes the content of request as stream of bytes
   */
  final def asStream: ZStream[Any, Throwable, Byte] = {
    self.asByteBufStream
      .mapZIO[Any, Throwable, Chunk[Byte]] { buf =>
        ZIO.attempt {
          val bytes = Chunk.fromArray(ByteBufUtil.getBytes(buf))
          buf.release(buf.refCnt())
          bytes
        }
      }
      .flattenChunks
  }

  /**
   * Decodes the content of request as string with the provided charset.
   */
  final def asString(charset: Charset): Task[String] =
    asByteArray.map(new String(_, charset))

  /**
   * Decodes the content of request as string with the default charset.
   */
  final def asString: Task[String] =
    asByteArray.map(new String(_, HTTP_CHARSET))

  /**
   * Returns true if Body is empty
   */
  final def isEmpty: Boolean = self match {
    case Body.Empty => true
    case _          => false
  }
}

object Body {

  /**
   * Helper to create empty Body
   */
  def empty: Body = Empty

  /**
   * Helper to create Body from AsciiString
   */
  def fromAsciiString(asciiString: AsciiString): Body = FromAsciiString(asciiString)

  /**
   * Helper to create Body from ByteBuf
   */
  def fromByteBuf(byteBuf: => ByteBuf): Body = Body.BinaryByteBuf { () =>
    val count = byteBuf.refCnt()
    if (count != 1) {
      throw new RuntimeException(s"Body can only be read once")
    } else
      byteBuf
  }

  /**
   * Helper to create Body from CharSequence
   */
  def fromCharSequence(charSequence: CharSequence, charset: Charset = HTTP_CHARSET): Body =
    fromAsciiString(new AsciiString(charSequence, charset))

  /**
   * Helper to create Body from chunk of bytes
   */
  def fromChunk(data: Chunk[Byte]): Body = BinaryChunk(data)

  /**
   * Helper to create Body from contents of a file
   */
  def fromFile(file: => java.io.File): Body = JavaFile(() => file)

  /**
   * Helper to create Body from Stream of string
   */
  def fromStream(stream: ZStream[Any, Throwable, CharSequence], charset: Charset = HTTP_CHARSET): Body =
    Body.BinaryStream(stream.map(str => Unpooled.wrappedBuffer(str.toString.getBytes(charset))))

  /**
   * Helper to create Body from Stream of bytes
   */
  def fromStream(stream: ZStream[Any, Throwable, Byte]): Body =
    Body.BinaryStream(stream.mapChunks(chunks => Chunk(Unpooled.wrappedBuffer(chunks.toArray))))

  /**
   * Helper to create Body from String
   */
  def fromString(text: String, charset: Charset = HTTP_CHARSET): Body = fromCharSequence(text, charset)

  private def collectStream[R, E](stream: ZStream[R, E, ByteBuf]): ZIO[R, E, ByteBuf] =
    stream.runFold(Unpooled.compositeBuffer()) { case (cmp, buf) => cmp.addComponent(true, buf) }

  private[zhttp] sealed trait Complete extends Body

  /**
   * Provides a more fine grained control while encoding Body into ByteBUfs
   */
  case class ByteBufConfig(chunkSize: Int = 1024 * 4) {
    def chunkSize(fileLength: Long): Int = {
      val actualInt = fileLength.toInt
      if (actualInt < 0) chunkSize
      else if (actualInt < chunkSize) actualInt
      else chunkSize
    }
  }

  private[zhttp] final case class UnsafeAsync(unsafeAdd: ((ChannelHandlerContext, HttpContent) => Unit) => Unit)
      extends Body {

    /**
     * Encodes the Body into a ByteBuf.
     */
    override def asByteBuf(config: ByteBufConfig): Task[ByteBuf] = for {
      done      <- Promise.make[Nothing, ByteBuf]
      rtm       <- ZIO.runtime[Any]
      composite <- ZIO.succeed(Unpooled.compositeBuffer())
      _         <- ZIO.attempt(unsafeAdd { (ctx, msg) =>
        composite.addComponent(true, msg.content())
        if (msg.isInstanceOf[LastHttpContent]) {
          Unsafe.unsafeCompat(implicit u => rtm.unsafe.run(done.succeed(composite))): Unit
        } else ctx.read(): Unit
      })
      buf       <- done.await
    } yield buf

    /**
     * Encodes the Body into a Stream of ByteBufs
     */
    override def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream
        .async[Any, Throwable, (Ctx, HttpContent)](
          { emit => unsafeAdd { (ctx, msg) => emit(ZIO.succeed(Chunk(ctx -> msg))) } },
          1,
        )
        .takeUntil { case (_, content) => content.isInstanceOf[LastHttpContent] }
        .mapZIO { case (ctx, msg) =>
          for {
            content <- ZIO.succeed(msg.content())
            _       <- ZIO.succeed(if (!msg.isInstanceOf[LastHttpContent]) ctx.read())
          } yield content
        }

    override def asHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf] =
      Http.fromZIO(asByteBuf(config))
  }

  private[zhttp] case class FromAsciiString(asciiString: AsciiString) extends Complete {

    /**
     * Encodes the Body into a ByteBuf. Takes in ByteBufConfig to have a more
     * fine grained control over the encoding.
     */
    override def asByteBuf(config: ByteBufConfig): Task[ByteBuf] = ZIO.attempt(encode)

    /**
     * Encodes the Body into a Stream of ByteBufs. Takes in ByteBufConfig to
     * have a more fine grained control over the encoding.
     */
    override def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.fromZIO(asByteBuf(config))

    /**
     * Encodes the Body into a Http of ByeBuf. This could be more performant in
     * certain cases. Takes in ByteBufConfig to have a more fine grained control
     * over the encoding.
     */
    override def asHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf] = Http.attempt(encode)

    private def encode: ByteBuf = Unpooled.wrappedBuffer(asciiString.array())
  }

  private[zhttp] final case class BinaryChunk(data: Chunk[Byte]) extends Complete {

    /**
     * Encodes the Body into a ByteBuf.
     */
    override def asByteBuf(config: ByteBufConfig): Task[ByteBuf] = ZIO.succeed(encode)

    /**
     * Encodes the Body into a Stream of ByteBufs
     */
    override def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.fromZIO(asByteBuf(config))

    override def asHttp(config: ByteBufConfig): UHttp[Any, ByteBuf] = Http.succeed(encode)

    private def encode = Unpooled.wrappedBuffer(data.toArray)
  }

  private[zhttp] final case class BinaryByteBuf(data: () => ByteBuf) extends Complete {

    /**
     * Encodes the Body into a ByteBuf.
     */
    override def asByteBuf(config: ByteBufConfig): Task[ByteBuf] = ZIO.attempt(data())

    /**
     * Encodes the Body into a Stream of ByteBufs
     */
    override def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.fromZIO(asByteBuf(config))

    override def asHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf] = Http.attempt(data())
  }

  private[zhttp] final case class BinaryStream(stream: ZStream[Any, Throwable, ByteBuf]) extends Complete {

    /**
     * Encodes the Body into a ByteBuf.
     */
    override def asByteBuf(config: ByteBufConfig): Task[ByteBuf] =
      collectStream(asByteBufStream(config))

    /**
     * Encodes the Body into a Stream of ByteBufs
     */
    override def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      stream

    override def asHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf] =
      Http.fromZIO(asByteBuf(config))
  }

  private[zhttp] final case class JavaFile(unsafeFile: () => java.io.File) extends Complete {

    /**
     * Encodes the Body into a ByteBuf.
     */
    override def asByteBuf(config: ByteBufConfig): Task[ByteBuf] =
      collectStream(asByteBufStream(config))

    /**
     * Encodes the Body into a Stream of ByteBufs
     */
    override def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.unwrap {
        for {
          file <- ZIO.attempt(unsafeFile())
          fs   <- ZIO.attempt(new FileInputStream(file))
          size   = config.chunkSize(file.length())
          buffer = new Array[Byte](size)
        } yield ZStream
          .repeatZIOOption[Any, Throwable, ByteBuf] {
            for {
              len   <- ZIO.attempt(fs.read(buffer)).mapError(Some(_))
              bytes <- if (len > 0) ZIO.succeed(Unpooled.copiedBuffer(buffer, 0, len)) else ZIO.fail(None)
            } yield bytes
          }
          .ensuring(ZIO.succeed(fs.close()))
      }

    override def asHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf] =
      Http.fromZIO(asByteBuf(config))
  }

  object ByteBufConfig {
    val default: ByteBufConfig = ByteBufConfig()
  }

  private[zhttp] case object Empty extends Complete {

    /**
     * Encodes the Body into a ByteBuf.
     */
    override def asByteBuf(config: ByteBufConfig): Task[ByteBuf] = ZIO.succeed(Unpooled.EMPTY_BUFFER)

    /**
     * Encodes the Body into a Stream of ByteBufs
     */
    override def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.fromZIO(asByteBuf(config))

    override def asHttp(config: ByteBufConfig): UHttp[Any, ByteBuf] = Http.empty
  }

}
