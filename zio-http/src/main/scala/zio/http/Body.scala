package zio.http

import io.netty.buffer.{ByteBuf, ByteBufUtil, Unpooled}
import io.netty.channel.{Channel => JChannel, DefaultFileRegion}
import io.netty.handler.codec.http.LastHttpContent
import io.netty.util.AsciiString
import zio._
import zio.http.model.HTTP_CHARSET
import zio.http.service.Ctx
import zio.stream.ZStream

import java.io.FileInputStream
import java.nio.charset.Charset
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
import java.nio.file._

/**
 * Holds Body that needs to be written on the HttpChannel
 */
sealed trait Body { self =>

  // final def asArray(implicit trace: Trace): Task[Array[Byte]] = asChunk.map(_.toArray)
  def asArray(implicit trace: Trace): Task[Array[Byte]]

  /**
   * Decodes the content of request as CharSequence
   */
  final def asCharSeq(implicit trace: Trace): ZIO[Any, Throwable, CharSequence] =
    asArray.map { buf => new AsciiString(buf, false) }

  def asChunk(implicit trace: Trace): Task[Chunk[Byte]]

  def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte]

  /**
   * Decodes the content of request as string with the provided charset.
   */
  final def asString(charset: Charset)(implicit trace: Trace): Task[String] =
    asArray.map(new String(_, charset))

  /**
   * Decodes the content of request as string with the default charset.
   */
  final def asString(implicit trace: Trace): Task[String] =
    asArray.map(new String(_, HTTP_CHARSET))

  def isComplete: Boolean

}

object Body {

  private[zio] sealed trait UnsafeWriteable extends Body

  private[zio] sealed trait UnsafeBytes extends Body {
    private[zio] def unsafeAsArray(implicit unsafe: Unsafe): Array[Byte]
  }

  /**
   * Helper to create empty Body
   */
  val empty: Body = EmptyBody

  private[zio] object EmptyBody extends Body with UnsafeWriteable with UnsafeBytes {

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = ZIO.succeed(Array.empty)

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = ZIO.succeed(Chunk.empty[Byte])

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] = ZStream.empty
    override def isComplete: Boolean                                            = true

    override def toString(): String = "Body.empty"

    override private[zio] def unsafeAsArray(implicit unsafe: Unsafe): Array[Byte] = Array.empty[Byte]
  }

  /**
   * Helper to create Body from AsciiString
   */
  def fromAsciiString(asciiString: AsciiString): Body = AsciiStringBody(asciiString)

  private[zio] final case class AsciiStringBody(asciiString: AsciiString)
      extends Body
      with UnsafeWriteable
      with UnsafeBytes {

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = ZIO.succeed(asciiString.array())
    override def isComplete: Boolean                               = true

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] =
      ZIO.succeed(Chunk.fromArray(asciiString.array()))

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream.unwrap(asChunk.map(ZStream.fromChunk(_)))

    override def toString(): String = s"Body.fromAsciiString($asciiString)"

    private[zio] override def unsafeAsArray(implicit unsafe: Unsafe): Array[Byte] = asciiString.array()

  }

  /**
   * Helper to create Body from ByteBuf
   */
  def fromByteBuf(byteBuf: => ByteBuf): Body = new ByteBufBody(byteBuf)

  private[zio] final class ByteBufBody(byteBuf: => ByteBuf) extends Body with UnsafeWriteable with UnsafeBytes {

    def byteBufData = byteBuf

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = ZIO.succeed(ByteBufUtil.getBytes(byteBuf))

    override def isComplete: Boolean = true

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = asArray.map(Chunk.fromArray)

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream.unwrap(asChunk.map(ZStream.fromChunk(_)))

    override def toString(): String = s"Body.fromByteBuf($byteBuf)"

    override private[zio] def unsafeAsArray(implicit unsafe: Unsafe): Array[Byte] =
      ByteBufUtil.getBytes(byteBuf)

  }

  /**
   * Helper to create Body from CharSequence
   */
  def fromCharSequence(charSequence: CharSequence, charset: Charset = HTTP_CHARSET): Body =
    fromAsciiString(new AsciiString(charSequence, charset))

  /**
   * Helper to create Body from chunk of bytes
   */
  def fromChunk(data: Chunk[Byte]): Body = new ChunkBody(data)

  private[zio] final case class ChunkBody(data: Chunk[Byte]) extends Body with UnsafeWriteable with UnsafeBytes {

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = ZIO.succeed(data.toArray)

    override def isComplete: Boolean = true

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = ZIO.succeed(data)

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream.unwrap(asChunk.map(ZStream.fromChunk(_)))

    override def toString(): String = s"Body.fromChunk($data)"

    override private[zio] def unsafeAsArray(implicit unsafe: Unsafe): Array[Byte] = data.toArray

  }

  /**
   * Helper to create Body from contents of a file
   */
  def fromFile(file: => java.io.File, chunkSize: Int = 1024 * 4): Body = new FileBody(file, chunkSize)

  private[zio] final class FileBody(file: => java.io.File, chunkSize: Int = 1024 * 4)
      extends Body
      with UnsafeWriteable
      with UnsafeBytes {

    def fileData = file

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = ZIO.attempt {
      Files.readAllBytes(file.toPath)
    }

    override def isComplete: Boolean = false

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] =
      asArray.map(Chunk.fromArray)

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream.unwrap {
        for {
          file <- ZIO.attempt(file)
          fs   <- ZIO.attempt(new FileInputStream(file))
          size = Math.min(chunkSize.toLong, file.length()).toInt
        } yield ZStream
          .repeatZIOOption[Any, Throwable, Chunk[Byte]] {
            for {
              buffer <- ZIO.succeed(new Array[Byte](size))
              len    <- ZIO.attemptBlocking(fs.read(buffer)).mapError(Some(_))
              bytes  <-
                if (len > 0) ZIO.succeed(Chunk.fromArray(buffer.slice(0, len)))
                else ZIO.fail(None)
            } yield bytes
          }
          .ensuring(ZIO.succeed(fs.close()))
      }.flattenChunks

    override private[zio] def unsafeAsArray(implicit unsafe: Unsafe): Array[Byte] =
      Files.readAllBytes(file.toPath)

  }

  /**
   * Helper to create Body from Stream of string
   */
  def fromStream(stream: ZStream[Any, Throwable, CharSequence], charset: Charset = HTTP_CHARSET)(implicit
    trace: Trace,
  ): Body =
    fromStream(stream.map(seq => Chunk.fromArray(seq.toString.getBytes(charset))).flattenChunks)

  /**
   * Helper to create Body from Stream of bytes
   */
  def fromStream(stream: ZStream[Any, Throwable, Byte]): Body = new StreamBody(stream)

  private[zio] final case class StreamBody(stream: ZStream[Any, Throwable, Byte]) extends Body {

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = asChunk.map(_.toArray)

    override def isComplete: Boolean = false

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = stream.runCollect

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] = stream

  }

  /**
   * Helper to create Body from String
   */
  def fromString(text: String, charset: Charset = HTTP_CHARSET): Body = fromCharSequence(text, charset)

  private[zio] def fromAsync(unsafeAsync: UnsafeAsync => Unit): Body = new AsyncBody(unsafeAsync)

  private[zio] final case class AsyncBody(unsafeAsync: UnsafeAsync => Unit) extends Body with UnsafeWriteable {

    def async = unsafeAsync

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = asChunk.map(_.toArray)

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = asStream.runCollect

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream
        .async[Any, Throwable, (JChannel, Chunk[Byte], Boolean)](emit =>
          try { unsafeAsync { (ctx, msg, isLast) => emit(ZIO.succeed(Chunk((ctx, msg, isLast)))) } }
          catch { case e: Throwable => emit(ZIO.fail(Option(e))) },
        )
        .tap { case (ctx, _, isLast) => ZIO.attempt(ctx.read()).unless(isLast) }
        .takeUntil { case (_, _, isLast) => isLast }
        .map { case (_, msg, _) => msg }
        .flattenChunks

    override def isComplete: Boolean = false

  }

  trait UnsafeAsync {
    def apply(ctx: JChannel, message: Chunk[Byte], isLast: Boolean): Unit
  }
}
