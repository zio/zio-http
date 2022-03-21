package zhttp.service.server.content.handlers
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{ChannelHandlerContext, DefaultFileRegion}
import io.netty.handler.codec.http.{
  DefaultHttpContent,
  DefaultHttpRequest,
  HttpHeaderNames,
  HttpRequest,
  LastHttpContent,
}
import zhttp.http.{HttpData, Request}
import zhttp.service.{ChannelFuture, Client, HttpRuntime}
import zio.stream.ZStream
import zio.{UIO, ZIO}

import java.io.RandomAccessFile

private[zhttp] trait ClientRequestHandler[R] {
  type Ctx = ChannelHandlerContext
  val rt: HttpRuntime[R]
  val config: Client.Config

  def writeRequest(msg: Request)(implicit ctx: Ctx): Unit = {
    ctx.write(encodeRequest(msg))
    writeData(msg.data.asInstanceOf[HttpData.Outgoing])
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

    val h = headers.add(HttpHeaderNames.TRANSFER_ENCODING, "chunked")

    // TODO: we should also add a default user-agent req header as some APIs might reject requests without it.
    new DefaultHttpRequest(jVersion, method, path, h)

  }

  /**
   * Writes file content to the Channel. Does not use Chunked transfer encoding
   */
  private def unsafeWriteFileContent(raf: RandomAccessFile)(implicit ctx: ChannelHandlerContext): Unit = {
    val fileLength = raf.length()
    // Write the content.
    ctx.write(new DefaultFileRegion(raf.getChannel, 0, fileLength))
    // Write the end marker.
    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
  }

  /**
   * Writes data on the channel
   */
  private def writeData(data: HttpData.Outgoing)(implicit ctx: Ctx): Unit = {
    data match {
      case HttpData.BinaryStream(stream)   =>
        rt.unsafeRun(ctx) {
          writeStreamContent(stream)
        }
      case HttpData.RandomAccessFile(raf)  =>
        unsafeWriteFileContent(raf())
      case HttpData.Text(content, charset) =>
        ctx.write(new DefaultHttpContent(Unpooled.copiedBuffer(content, charset)))
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
      case HttpData.BinaryChunk(data)      =>
        ctx.write(Unpooled.copiedBuffer(data.toArray)): Unit
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
      case HttpData.BinaryByteBuf(data)    =>
        ctx.writeAndFlush(data): Unit
      case HttpData.Empty => ctx.writeAndFlush(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)): Unit
      case _              =>
        ctx.flush(): Unit
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

}
