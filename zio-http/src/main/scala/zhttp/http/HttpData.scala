package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil, Unpooled}
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
  def asByteBuf(config: ByteBufConfig): Task[ByteBuf]

  /**
   * Encodes the HttpData into a Stream of ByteBufs. Takes in ByteBufConfig to
   * have a more fine grained control over the encoding.
   */
  def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf]

  /**
   * Encodes the HttpData into a Http of ByeBuf. This could be more performant
   * in certain cases. Takes in ByteBufConfig to have a more fine grained
   * control over the encoding.
   */
  def asHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf]

  final def asByteArray: Task[Array[Byte]] =
    asByteBuf.flatMap(buf => ZIO.attempt(ByteBufUtil.getBytes(buf)).ensuring(ZIO.succeed(buf.release(buf.refCnt()))))

  /**
   * Encodes the HttpData into a ByteBuf.
   */
  final def asByteBuf: Task[ByteBuf] =
    asByteBuf(ByteBufConfig.default)

  /**
   * Encodes the HttpData into a Stream of ByteBufs
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
   * A bit more efficient version of toByteBuf in certain cases
   */
  final def asHttp: Http[Any, Throwable, Any, ByteBuf] = asHttp(ByteBufConfig.default)

  /**
   * Decodes the content of request as stream of bytes
   */
  final def asStream: ZStream[Any, Throwable, Byte] = self.asByteBufStream
    .mapZIO[Any, Throwable, Chunk[Byte]] { buf =>
      ZIO.attempt {
        val bytes = Chunk.fromArray(ByteBufUtil.getBytes(buf))
        buf.release(buf.refCnt())
        bytes
      }
    }
    .flattenChunks

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
   * Returns true if HttpData is empty
   */
  final def isEmpty: Boolean = self match {
    case HttpData.Empty => true
    case _              => false
  }
}

object HttpData {

  private def collectStream[R, E](stream: ZStream[R, E, ByteBuf]): ZIO[R, E, ByteBuf] =
    stream.runFold(Unpooled.compositeBuffer()) { case (cmp, buf) => cmp.addComponent(true, buf) }

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

  private[zhttp] final case class UnsafeAsync(unsafeRun: (ChannelHandlerContext => HttpContent => Any) => Unit)
      extends HttpData {

    private def isLast(msg: HttpContent): Boolean = msg.isInstanceOf[LastHttpContent]

    private def toQueue: ZIO[Any, Nothing, (ChannelHandlerContext, Queue[HttpContent])] = {
      for {
        queue      <- Queue.bounded[HttpContent](1)
        ctxPromise <- Promise.make[Nothing, ChannelHandlerContext]
        runtime    <- ZIO.runtime[Any]
        _          <- ZIO.succeed {
          unsafeRun { ch =>
            Unsafe.unsafeCompat { implicit u =>
              // TODO: passing unsafe explicitly is a bit of a hack, but it works for now
              runtime.unsafe.run(ctxPromise.succeed(ch))(Trace.empty, u)
              (msg: HttpContent) => runtime.unsafe.run(queue.offer(msg))(Trace.empty, u)
            }
          }
        }
        ctx        <- ctxPromise.await
      } yield (ctx, queue)
    }

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def asByteBuf(config: ByteBufConfig): Task[ByteBuf] = for {
      body <- ZIO.async[Any, Nothing, ByteBuf](cb =>
        unsafeRun(ch => {
          val buffer = Unpooled.compositeBuffer()
          msg => {
            buffer.addComponent(true, msg.content)
            if (isLast(msg)) cb(ZIO.succeed(buffer)) else ch.read(): Unit
          }
        }),
      )
    } yield body

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] = {
      ZStream.unwrap {
        for {
          reader <- toQueue
          stream = ZStream
            .fromQueueWithShutdown(reader._2)
            .tap(_ => ZIO.succeed(reader._1.read()))
            .takeUntil(isLast)
            .map(_.content())
        } yield stream
      }
    }

    override def asHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf] =
      Http.fromZIO(asByteBuf(config))
  }

  private[zhttp] case class FromAsciiString(asciiString: AsciiString) extends Complete {

    private def encode: ByteBuf = Unpooled.wrappedBuffer(asciiString.array())

    /**
     * Encodes the HttpData into a ByteBuf. Takes in ByteBufConfig to have a
     * more fine grained control over the encoding.
     */
    override def asByteBuf(config: ByteBufConfig): Task[ByteBuf] = ZIO.attempt(encode)

    /**
     * Encodes the HttpData into a Stream of ByteBufs. Takes in ByteBufConfig to
     * have a more fine grained control over the encoding.
     */
    override def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.fromZIO(asByteBuf(config))

    /**
     * Encodes the HttpData into a Http of ByeBuf. This could be more performant
     * in certain cases. Takes in ByteBufConfig to have a more fine grained
     * control over the encoding.
     */
    override def asHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf] = Http.attempt(encode)
  }

  private[zhttp] final case class BinaryChunk(data: Chunk[Byte]) extends Complete {

    private def encode = Unpooled.wrappedBuffer(data.toArray)

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def asByteBuf(config: ByteBufConfig): Task[ByteBuf] = ZIO.succeed(encode)

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.fromZIO(asByteBuf(config))

    override def asHttp(config: ByteBufConfig): UHttp[Any, ByteBuf] = Http.succeed(encode)
  }

  private[zhttp] final case class BinaryByteBuf(data: ByteBuf) extends Complete {

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def asByteBuf(config: ByteBufConfig): Task[ByteBuf] = ZIO.attempt(data)

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.fromZIO(asByteBuf(config))

    override def asHttp(config: ByteBufConfig): UHttp[Any, ByteBuf] = Http.succeed(data)
  }

  private[zhttp] final case class BinaryStream(stream: ZStream[Any, Throwable, ByteBuf]) extends Complete {

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def asByteBuf(config: ByteBufConfig): Task[ByteBuf] =
      collectStream(asByteBufStream(config))

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      stream

    override def asHttp(config: ByteBufConfig): Http[Any, Throwable, Any, ByteBuf] =
      Http.fromZIO(asByteBuf(config))
  }

  private[zhttp] final case class JavaFile(unsafeFile: () => java.io.File) extends Complete {

    /**
     * Encodes the HttpData into a ByteBuf.
     */
    override def asByteBuf(config: ByteBufConfig): Task[ByteBuf] =
      collectStream(asByteBufStream(config))

    /**
     * Encodes the HttpData into a Stream of ByteBufs
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
     * Encodes the HttpData into a ByteBuf.
     */
    override def asByteBuf(config: ByteBufConfig): Task[ByteBuf] = ZIO.succeed(Unpooled.EMPTY_BUFFER)

    /**
     * Encodes the HttpData into a Stream of ByteBufs
     */
    override def asByteBufStream(config: ByteBufConfig): ZStream[Any, Throwable, ByteBuf] =
      ZStream.fromZIO(asByteBuf(config))

    override def asHttp(config: ByteBufConfig): UHttp[Any, ByteBuf] = Http.empty
  }

}
