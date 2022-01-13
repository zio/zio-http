package zhttp.service.server.content.handlers

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, DefaultFileRegion, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.http.{HttpData, Response, Status}
import zhttp.service.server.ServerTimeGenerator
import zhttp.service.{ChannelFuture, HttpRuntime, Server}
import zio.stream.ZStream
import zio.{UIO, ZIO}

import java.io.File

@Sharable
private[zhttp] case class ServerResponseHandler[R](
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  serverTime: ServerTimeGenerator,
) extends SimpleChannelInboundHandler[Response](false) {

  type Ctx = ChannelHandlerContext

  override def channelRead0(ctx: Ctx, response: Response): Unit = {
    implicit val iCtx: ChannelHandlerContext = ctx
    response.data match {
      case HttpData.BinaryStream(stream) =>
        ctx.write(encodeResponse(response))
        runtime.unsafeRun(ctx) { writeStreamContent(stream) }
      case HttpData.File(file)           => unsafeWriteFileContent(file, response)
      case _                             =>
        ctx.write(encodeResponse(response))
        ctx.flush()
    }
    ()
  }

  override def exceptionCaught(ctx: Ctx, cause: Throwable): Unit = {
    config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(ctx)(f(cause)))
  }

  /**
   * Checks if an encoded version of the response exists, uses it if it does. Otherwise, it will return a fresh
   * response. It will also set the server time if requested by the client.
   */
  private def encodeResponse(res: Response): HttpResponse = {

    val jResponse = res.attribute.encoded match {

      // Check if the encoded response exists and/or was modified.
      case Some((oRes, jResponse)) if oRes eq res =>
        jResponse match {
          // Duplicate the response without allocating much memory
          case response: FullHttpResponse => response.retainedDuplicate()

          case response => response
        }

      case _ => res.unsafeEncode()
    }
    // Identify if the server time should be set and update if required.
    if (res.attribute.serverTime) jResponse.headers().set(HttpHeaderNames.DATE, serverTime.refreshAndGet())
    jResponse
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

  /**
   * Writes file content to the Channel. Does not use Chunked transfer encoding
   */
  private def unsafeWriteFileContent(file: File, response: Response)(implicit ctx: ChannelHandlerContext): Unit = {
    import java.io.RandomAccessFile
    try {
      val raf        = new RandomAccessFile(file, "r")
      val fileLength = raf.length()
      ctx.write(encodeResponse(response))
      // Write the content.
      ctx.write(new DefaultFileRegion(raf.getChannel, 0, fileLength))
      // Write the end marker.
      ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
    } catch {
      case _: Throwable =>
        ctx.writeAndFlush(encodeResponse(Response.status(Status.NOT_FOUND))): Unit
    }
  }
}
