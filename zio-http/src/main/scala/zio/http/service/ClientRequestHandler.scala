package zio.http.service

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{ChannelHandlerContext, DefaultFileRegion}
import io.netty.handler.codec.http._
import zhttp.http.Request
import zhttp.service.{ChannelFuture, HttpRuntime}
import zio.stream.ZStream
import zio.{UIO, ZIO}

import java.io.File

private[zhttp] trait ClientRequestHandler[R] {
  type Ctx = ChannelHandlerContext
  val zExec: HttpRuntime[R]

  def writeRequest(msg: Request)(implicit ctx: Ctx): Unit = {
    ctx.write(encodeRequest(msg))
    writeData(msg.data.asInstanceOf[HttpData.Complete])
    ()
  }

  private def encodeRequest(req: Request): HttpRequest = {
    val method   = req.method.toJava
    val jVersion = req.version.toJava

    // As per the spec, the path should contain only the relative path.
    // Host and port information should be in the headers.
    val path = req.url.relative.encode

    val encodedReqHeaders = req.headers.encode

    val headers = req.url.host match {
      case Some(value) => encodedReqHeaders.set(HttpHeaderNames.HOST, value)
      case None        => encodedReqHeaders
    }

    val h = headers
      .add(HttpHeaderNames.TRANSFER_ENCODING, "chunked")
      .add(HttpHeaderNames.USER_AGENT, "zhttp-client")

    new DefaultHttpRequest(jVersion, method, path, h)

  }

  /**
   * Writes file content to the Channel. Does not use Chunked transfer encoding
   */
  private def unsafeWriteFileContent(file: File)(implicit ctx: ChannelHandlerContext): Unit = {
    // Write the content.
    ctx.write(new DefaultFileRegion(file, 0, file.length()))
    // Write the end marker.
    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
  }

  /**
   * Writes data on the channel
   */
  private def writeData(data: HttpData.Complete)(implicit ctx: Ctx): Unit = {
    data match {
      case HttpData.BinaryStream(stream)         =>
        zExec.unsafeRun(ctx) {
          writeStreamContent(stream)
        }
      case HttpData.FromAsciiString(asciiString) =>
        ctx.write(new DefaultHttpContent(Unpooled.wrappedBuffer(asciiString.array())))
        writeAndFlushLastHttpContent
      case HttpData.JavaFile(raf)                =>
        unsafeWriteFileContent(raf())
      case HttpData.BinaryChunk(data)            =>
        ctx.write(Unpooled.copiedBuffer(data.toArray)): Unit
        writeAndFlushLastHttpContent
      case HttpData.BinaryByteBuf(data)          =>
        ctx.writeAndFlush(data): Unit
        writeAndFlushLastHttpContent
      case HttpData.Empty                        => writeAndFlushLastHttpContent
    }
  }

  /**
   * Writes Binary Stream data to the Channel
   */
  private def writeStreamContent[A](
    stream: ZStream[R, Throwable, ByteBuf],
  )(implicit ctx: Ctx): ZIO[R, Throwable, Unit] = {
    for {
      _ <- stream.foreach(c => UIO(ctx.writeAndFlush(c)))
      _ <- ChannelFuture.unit(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT))
    } yield ()
  }

  private def writeAndFlushLastHttpContent(implicit ctx: Ctx): Unit =
    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit

}
