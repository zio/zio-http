package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil, Unpooled}
import io.netty.channel.{ChannelHandlerContext, DefaultFileRegion, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import io.netty.util.AsciiString
import zhttp.service.{Ctx, HTTP_CONTENT_HANDLER, HTTP_REQUEST_HANDLER}
import zio._
import zio.stream.ZStream

import java.io.FileInputStream
import java.nio.charset.Charset

/**
 * Holds Body that needs to be written on the HttpChannel
 */
trait Body { self =>
  final def asArray: Task[Array[Byte]] = asChunk.map(_.toArray)

  /**
   * Decodes the content of request as CharSequence
   */
  final def asCharSeq: ZIO[Any, Throwable, CharSequence] =
    asArray.map { buf => new AsciiString(buf, false) }

  def asChunk: Task[Chunk[Byte]]

  def asStream: ZStream[Any, Throwable, Byte]

  /**
   * Decodes the content of request as string with the provided charset.
   */
  final def asString(charset: Charset): Task[String] =
    asArray.map(new String(_, charset))

  /**
   * Decodes the content of request as string with the default charset.
   */
  final def asString: Task[String] =
    asArray.map(new String(_, HTTP_CHARSET))

  def isComplete: Boolean

  def write(ctx: Ctx): Task[Boolean]
}

object Body {

  /**
   * Helper to create empty Body
   */
  def empty: Body = new Body {
    override def asChunk: Task[Chunk[Byte]]              = ZIO.succeed(Chunk.empty[Byte])
    override def asStream: ZStream[Any, Throwable, Byte] = ZStream.empty
    override def write(ctx: Ctx): Task[Boolean]          = ZIO.succeed(false)
    override def isComplete: Boolean                     = true
  }

  /**
   * Helper to create Body from AsciiString
   */
  def fromAsciiString(asciiString: AsciiString): Body = new Body {
    override def isComplete: Boolean = true

    override def asChunk: Task[Chunk[Byte]] =
      ZIO.succeed(Chunk.fromArray(asciiString.array()))

    override def asStream: ZStream[Any, Throwable, Byte] =
      ZStream.unwrap(asChunk.map(ZStream.fromChunk(_)))

    override def write(ctx: Ctx): Task[Boolean] =
      ZIO.attempt(ctx.write(Unpooled.wrappedBuffer(asciiString.array())): Unit).as(false)
  }

  /**
   * Helper to create Body from ByteBuf
   */
  def fromByteBuf(byteBuf: => ByteBuf): Body = new Body {
    override def isComplete: Boolean = true

    override def asChunk: Task[Chunk[Byte]] = ZIO.attempt {
      Chunk.fromArray(ByteBufUtil.getBytes(byteBuf))
    }

    override def asStream: ZStream[Any, Throwable, Byte] =
      ZStream.unwrap(asChunk.map(ZStream.fromChunk(_)))

    override def write(ctx: Ctx): Task[Boolean] =
      ZIO.attempt(ctx.write(byteBuf): Unit).as(false)
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

    override def asChunk: Task[Chunk[Byte]] = ZIO.succeed(data)

    override def asStream: ZStream[Any, Throwable, Byte] =
      ZStream.unwrap(asChunk.map(ZStream.fromChunk(_)))

    override def write(ctx: Ctx): Task[Boolean] =
      ZIO.attempt(ctx.write(Unpooled.wrappedBuffer(data.toArray))).as(false)
  }

  /**
   * Helper to create Body from contents of a file
   */
  def fromFile(file: => java.io.File, chunkSize: Long = 1024 * 4): Body = new Body {
    override def isComplete: Boolean = false

    override def asChunk: Task[Chunk[Byte]] =
      asStream.runCollect

    override def asStream: ZStream[Any, Throwable, Byte] =
      ZStream.unwrap {
        for {
          file <- ZIO.attempt(file)
          fs   <- ZIO.attempt(new FileInputStream(file))
          size   = Math.min(chunkSize, file.length()).toInt
          buffer = new Array[Byte](size)
        } yield ZStream
          .repeatZIOOption[Any, Throwable, Chunk[Byte]] {
            for {
              len   <- ZIO.attempt(fs.read(buffer)).mapError(Some(_))
              bytes <- if (len > 0) ZIO.succeed(Chunk.fromArray(buffer)) else ZIO.fail(Option.empty[Throwable])
            } yield bytes
          }
          .ensuring(ZIO.succeed(fs.close()))
      }.flattenChunks

    override def write(ctx: Ctx): Task[Boolean] = ZIO.attempt {
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
  def fromStream(stream: ZStream[Any, Throwable, CharSequence], charset: Charset = HTTP_CHARSET): Body =
    fromStream(stream.map(seq => Chunk.fromArray(seq.toString.getBytes(charset))).flattenChunks)

  /**
   * Helper to create Body from Stream of bytes
   */
  def fromStream(stream: ZStream[Any, Throwable, Byte]): Body = {
    new Body {
      override def isComplete: Boolean = false

      override def asChunk: Task[Chunk[Byte]] = stream.runCollect

      override def asStream: ZStream[Any, Throwable, Byte] = stream

      override def write(ctx: Ctx): Task[Boolean] =
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

  private[zhttp] def async(ctx: Ctx, hasBody: Boolean): Body = new Body {
    override def asChunk: Task[Chunk[Byte]] = asStream.runCollect

    override def asStream: ZStream[Any, Throwable, Byte] =
      ZStream.unwrap {
        ZIO.attempt {
          if (!hasBody) ZStream.empty
          else {
            ZStream.async[Any, Throwable, Byte](
              { emit =>
                ctx
                  .channel()
                  .pipeline
                  .addAfter(
                    HTTP_REQUEST_HANDLER,
                    HTTP_CONTENT_HANDLER,
                    new SimpleChannelInboundHandler[HttpContent](true) {
                      self =>
                      override def channelRead0(ctx: ChannelHandlerContext, msg: HttpContent): Unit = {
                        val isLast = msg.isInstanceOf[LastHttpContent]
                        val value  = Chunk.fromArray(ByteBufUtil.getBytes(msg.content()))
                        emit(ZIO.succeed(value))
                        if (isLast) {
                          emit(ZIO.fail(None))
                          ctx.channel().pipeline().remove(self): Unit
                        } else {
                          ctx.read(): Unit
                        }
                      }

                      override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
                        ctx.read(): Unit
                      }
                    },
                  ): Unit
              },
              1,
            )
          }
        }
      }

    override def write(ctx: Ctx): Task[Boolean] =
      ZIO.attempt {
        ctx
          .channel()
          .pipeline
          .addAfter(
            HTTP_REQUEST_HANDLER,
            HTTP_CONTENT_HANDLER,
            new SimpleChannelInboundHandler[HttpContent](true) {
              self =>
              override def channelRead0(ctx: ChannelHandlerContext, msg: HttpContent): Unit = {
                ctx.writeAndFlush(msg)
                if (msg.isInstanceOf[LastHttpContent]) ctx.channel().pipeline().remove(self): Unit
              }

              override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
                ctx.channel().config().setAutoRead(true)
                ctx.read(): Unit
              }
            },
          )
      }.as(true)

    override def isComplete: Boolean = false
  }
}
