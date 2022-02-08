package zhttp.service.server.content.handlers

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, DefaultFileRegion, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.http.HttpData.Outgoing
import zhttp.http.{HttpData, Response}
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
) extends SimpleChannelInboundHandler[Response](true) {

  type Ctx = ChannelHandlerContext

  override def channelRead0(ctx: Ctx, msg: Response): Unit = {
    implicit val iCtx: ChannelHandlerContext = ctx
    ctx.write(encodeResponse(msg))
    msg.data match {
      case HttpData.Incoming(_)        => ???
      case outgoing: HttpData.Outgoing =>
        outgoing match {
          case Outgoing.BinaryStream(stream) =>
            runtime.unsafeRun(ctx) {
              writeStreamContent(stream) ensuring
                UIO(ctx.read()) // read next request
            }
          case Outgoing.File(file)           =>
            unsafeWriteFileContent(file)
            ctx.read() // read next request
          case _                             =>
            ctx.flush()
            ctx.read() // read next request
        }
    }
    ()
  }

  override def exceptionCaught(ctx: Ctx, cause: Throwable): Unit = {
    config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(ctx)(f(cause)))
  }

  /**
   * Releases the FullHttpRequest safely.
   */
  def releaseRequest(jReq: FullHttpRequest): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }

  /**
   * Checks if an encoded version of the response exists, uses it if it does.
   * Otherwise, it will return a fresh response. It will also set the server
   * time if requested by the client.
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
  private def unsafeWriteFileContent(file: File)(implicit ctx: ChannelHandlerContext): Unit = {
    import java.io.RandomAccessFile

    val raf        = new RandomAccessFile(file, "r")
    val fileLength = raf.length()
    // Write the content.
    ctx.write(new DefaultFileRegion(raf.getChannel, 0, fileLength))
    // Write the end marker.
    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
  }
}
