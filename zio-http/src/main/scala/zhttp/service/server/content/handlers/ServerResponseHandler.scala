package zhttp.service.server.content.handlers

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.core.Util
import zhttp.http.{HTTP_CHARSET, HttpData, Response, Status}
import zhttp.service.server.ServerTimeGenerator
import zhttp.service.{ChannelFuture, HttpRuntime, Server}
import zio.stream.ZStream
import zio.{UIO, ZIO}

@Sharable
private[zhttp] case class ServerResponseHandler[R](
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  serverTime: ServerTimeGenerator,
) extends SimpleChannelInboundHandler[Response[R, Throwable]](false) {

  type Ctx = ChannelHandlerContext

  override def channelRead0(ctx: Ctx, response: Response[R, Throwable]): Unit = {
    response.status match {
      case Status.NOT_FOUND =>
        ctx.writeAndFlush(notFoundResponse)
      case Status.OK        =>
        ctx.write(encodeResponse(response))
        response.data match {
          case HttpData.BinaryStream(stream) =>
            runtime.unsafeRun(ctx) {
              writeStreamContent(stream)(ctx)
            }
          case _                             => ctx.flush()
        }

      case _ =>
        ctx.writeAndFlush(encodeResponse(response))
    }
    ()
  }

  override def exceptionCaught(ctx: Ctx, cause: Throwable): Unit = {
    if (ctx.channel().isWritable) {
      ctx.writeAndFlush(serverErrorResponse(cause)): Unit
    }
    config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(ctx)(f(cause)))
  }

  /**
   * Checks if an encoded version of the response exists, uses it if it does. Otherwise, it will return a fresh
   * response. It will also set the server time if requested by the client.
   */
  private def encodeResponse(res: Response[_, _]): HttpResponse = {

    val jResponse = res.attribute.encoded match {

      // Check if the encoded response exists and/or was modified.
      case Some((oRes, jResponse)) if oRes eq res =>
        jResponse match {

          // Duplicate the response without allocating much memory
          case response: FullHttpResponse => response.retainedDuplicate()
          case response                   => response
        }

      case _ => res.unsafeEncode()
    }

    // Identify if the server time should be set and update if required.
    if (res.attribute.serverTime) jResponse.headers().set(HttpHeaderNames.DATE, serverTime.refreshAndGet())
    jResponse
  }

  private def notFoundResponse: HttpResponse = {
    val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, false)
    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0)
    response
  }

  private def serverErrorResponse(cause: Throwable): HttpResponse = {
    val content  = Util.prettyPrintHtml(cause)
    val response = new DefaultFullHttpResponse(
      HttpVersion.HTTP_1_1,
      HttpResponseStatus.INTERNAL_SERVER_ERROR,
      Unpooled.copiedBuffer(content, HTTP_CHARSET),
    )
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length)
    response
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
