package zio.http

import io.netty.buffer.{ByteBuf, ByteBufUtil, Unpooled}
import io.netty.channel.{DefaultFileRegion, Channel => JChannel}
import io.netty.handler.codec.http.{FullHttpRequest, LastHttpContent}
import io.netty.handler.codec.http.multipart.HttpPostMultipartRequestDecoder
import io.netty.util.AsciiString
import zio._
import zio.http.model.{HTTP_CHARSET, Multipart}
import zio.http.service.Ctx
import zio.stream.ZStream

import java.io.FileInputStream
import java.nio.charset.Charset
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.jdk.CollectionConverters._ // scalafix:ok;

/**
 * Holds Body that needs to be written on the HttpChannel
 */
trait Body { self =>
  final def asArray(implicit trace: Trace): Task[Array[Byte]] = asChunk.map(_.toArray)

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

  def write(ctx: Ctx)(implicit trace: Trace): Task[Boolean]

  // TODO It looks that multipart/form-data can not be used together with chunked transfer.
  // So I need to implement it only in fromFullHttpRequest. Is it correct?
  def multipart(implicit trace: Trace): RIO[Scope, List[Multipart]] = ZIO.succeed(List.empty)
}

object Body {

  /**
   * Helper to create empty Body
   */
  val empty: Body = new Body {
    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]]              = ZIO.succeed(Chunk.empty[Byte])
    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] = ZStream.empty
    override def write(ctx: Ctx)(implicit trace: Trace): Task[Boolean]          = ZIO.succeed(false)
    override def isComplete: Boolean                                            = true

    override def toString(): String = "Body.empty"
  }

  /**
   * Helper to create Body from AsciiString
   */
  def fromAsciiString(asciiString: AsciiString): Body = new Body {
    override def isComplete: Boolean = true

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] =
      ZIO.succeed(Chunk.fromArray(asciiString.array()))

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream.unwrap(asChunk.map(ZStream.fromChunk(_)))

    override def write(ctx: Ctx)(implicit trace: Trace): Task[Boolean] =
      ZIO.attempt(ctx.write(Unpooled.wrappedBuffer(asciiString.array())): Unit).as(false)

    override def toString(): String = s"Body.fromAsciiString($asciiString)"
  }

  /**
   * Helper to create Body from ByteBuf
   */
  def fromByteBuf(byteBuf: => ByteBuf): Body = new Body {
    override def isComplete: Boolean = true

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = ZIO.attempt {
      Chunk.fromArray(ByteBufUtil.getBytes(byteBuf))
    }

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream.unwrap(asChunk.map(ZStream.fromChunk(_)))

    override def write(ctx: Ctx)(implicit trace: Trace): Task[Boolean] =
      ZIO.attempt(ctx.write(byteBuf): Unit).as(false)

    override def toString(): String = s"Body.fromByteBuf($byteBuf)"
  }

  def fromFullHttpRequest(request: FullHttpRequest): Body = new Body {
    override def isComplete: Boolean = true

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = ZIO.attempt {
      Chunk.fromArray(ByteBufUtil.getBytes(request.content()))
    }

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream.unwrap(asChunk.map(ZStream.fromChunk(_)))

    override def write(ctx: Ctx)(implicit trace: Trace): Task[Boolean] =
      ZIO.attempt(ctx.write(request.content()): Unit).as(false)

    override def multipart(implicit trace: Trace): RIO[Scope, List[Multipart]] =
      ZIO
        .succeed(new HttpPostMultipartRequestDecoder(request))
        .withFinalizer(decoder => ZIO.attempt(decoder.destroy()).orDie)
        .map(_.getBodyHttpDatas.asScala.toList)
        .flatMap(ZIO.foreach(_)(MultipartConverter.convert))
  }

  /**
   * Helper to create Body from CharSequence
   */
  def fromCharSequence(charSequence: CharSequence, charset: Charset = HTTP_CHARSET): Body =
    fromAsciiString(new AsciiString(charSequence, charset))

  /**
   * Helper to create Body from chunk of bytes
   */
  def fromChunk(data: Chunk[Byte]): Body = new Body {
    override def isComplete: Boolean = true

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = ZIO.succeed(data)

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream.unwrap(asChunk.map(ZStream.fromChunk(_)))

    override def write(ctx: Ctx)(implicit trace: Trace): Task[Boolean] =
      ZIO.attempt(ctx.write(Unpooled.wrappedBuffer(data.toArray))).as(false)

    override def toString(): String = s"Body.fromChunk($data)"
  }

  /**
   * Helper to create Body from contents of a file
   */
  def fromFile(file: => java.io.File, chunkSize: Int = 1024 * 4): Body = new Body {
    override def isComplete: Boolean = false

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] =
      asStream.runCollect

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

    override def write(ctx: Ctx)(implicit trace: Trace): Task[Boolean] = ZIO.attempt {
      // Write the content.
      ctx.write(new DefaultFileRegion(file, 0, file.length()))

      // Write the end marker.
      ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit

      true
    }
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
  def fromStream(stream: ZStream[Any, Throwable, Byte]): Body = {
    new Body {
      override def isComplete: Boolean = false

      override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = stream.runCollect

      override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] = stream

      override def write(ctx: Ctx)(implicit trace: Trace): Task[Boolean] =
        for {
          _ <- stream.runForeachChunk(c => ZIO.succeed(ctx.writeAndFlush(Unpooled.wrappedBuffer(c.toArray))))
          _ <- ZIO.attempt(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT))
        } yield true
    }
  }

  /**
   * Helper to create Body from String
   */
  def fromString(text: String, charset: Charset = HTTP_CHARSET): Body = fromCharSequence(text, charset)

  private[zio] def fromAsync(unsafeAsync: UnsafeAsync => Unit): Body = new Body {
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

    override def write(ctx: Ctx)(implicit trace: Trace): Task[Boolean] =
      ZIO
        .attempt(unsafeAsync { (ctx, msg, isLast) =>
          ctx.writeAndFlush(msg)
          if (isLast) ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
          else ctx.read(): Unit
        })
        .as(true)
  }

  trait UnsafeAsync {
    def apply(ctx: JChannel, message: Chunk[Byte], isLast: Boolean): Unit
  }
}
