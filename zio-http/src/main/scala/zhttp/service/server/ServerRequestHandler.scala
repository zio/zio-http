package zhttp.service.server

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.websocketx.{WebSocketServerProtocolHandler => JWebSocketServerProtocolHandler}
import io.netty.handler.codec.http.{LastHttpContent => JLastHttpContent}
import zhttp.core._
import zhttp.http._
import zhttp.service._
import zio.Exit

/**
 * Helper class with channel methods
 */
@JSharable
final case class ServerRequestHandler[R](
  zExec: UnsafeChannelExecutor[R],
  app: RHttp[R],
) extends JSimpleChannelInboundHandler[JFullHttpRequest](AUTO_RELEASE_REQUEST)
    with HttpMessageCodec
    with ServerHttpExceptionHandler {

  self =>

  /**
   * Tries to release the request byte buffer, ignores if it can not.
   */
  private def releaseOrIgnore(jReq: JFullHttpRequest): Boolean = jReq.release(jReq.content().refCnt())

  /**
   * Asynchronously executes the Http app and passes the response to the callback.
   */
  private def executeAsync(ctx: JChannelHandlerContext, jReq: JFullHttpRequest)(
    cb: Response[R, Throwable] => Unit,
  ): Unit =
    decodeJRequest(jReq) match {
      case Left(err)  => cb(err.toResponse)
      case Right(req) =>
        app.eval(req) match {
          case HttpResult.Success(a)  => cb(a)
          case HttpResult.Failure(e)  => cb(SilentResponse[Throwable].silent(e))
          case HttpResult.Continue(z) =>
            zExec.unsafeExecute(ctx, z) {
              case Exit.Success(res)   => cb(res)
              case Exit.Failure(cause) =>
                cause.failureOption match {
                  case Some(e) => cb(SilentResponse[Throwable].silent(e))
                  case None    => ()
                }
            }
        }
    }

  /**
   * Unsafe channel reader for HttpRequest
   */
  override def channelRead0(ctx: JChannelHandlerContext, jReq: JFullHttpRequest): Unit = {
    executeAsync(ctx, jReq) {
      case res @ Response.HttpResponse(_, _, content) =>
        content match {
          case HttpData.CompleteData(_)  =>
            writeAndFlush(ctx, jReq, res)
            ()
          case HttpData.StreamData(data) =>
            writeAndFlush(ctx, jReq, res)
            zExec.unsafeExecute_(ctx) {
              data
                .mapM(t => ChannelFuture.unit(ctx.writeAndFlush(Unpooled.copiedBuffer(t.toArray))))
                .runDrain *> ChannelFuture.unit(ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT))
            }
          case HttpData.Empty            =>
            writeAndFlush(ctx, jReq, res)
            ()

        }

      case res @ Response.SocketResponse(_) =>
        ctx
          .channel()
          .pipeline()
          .addLast(new JWebSocketServerProtocolHandler(res.socket.settings.protocolConfig))
          .addLast(WEB_SOCKET_HANDLER, ServerSocketHandler(zExec, res.socket.settings))
        ctx.fireChannelRead(jReq)
        ()
    }
  }

  private def writeAndFlush(
    ctx: JChannelHandlerContext,
    jReq: JFullHttpRequest,
    res: Response.HttpResponse[R, Throwable],
  ) = {
    ctx.writeAndFlush(encodeResponse(jReq.protocolVersion(), res), ctx.channel().voidPromise())
    releaseOrIgnore(jReq)
  }

  /**
   * Handles exceptions that throws
   */
  override def exceptionCaught(ctx: JChannelHandlerContext, cause: Throwable): Unit = {
    if (self.canThrowException(cause)) {
      super.exceptionCaught(ctx, cause)
    }
  }
}
