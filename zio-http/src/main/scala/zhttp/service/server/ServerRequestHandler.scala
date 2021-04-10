package zhttp.service.server

import io.netty.handler.codec.http.websocketx.{
  WebSocketServerProtocolConfig => JWebSocketServerProtocolConfig,
  WebSocketServerProtocolHandler => JWebSocketServerProtocolHandler,
}
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
      case res @ Response.HttpResponse(_, _, _) =>
        ctx.writeAndFlush(encodeResponse(jReq.protocolVersion(), res), ctx.channel().voidPromise())
        releaseOrIgnore(jReq)
        ()
      case res @ Response.SocketResponse(_)     =>
        val settings = res.socket.settings
        val config   = JWebSocketServerProtocolConfig.newBuilder().websocketPath(jReq.uri())
        if (settings.subProtocol.isDefined) config.subprotocols(settings.subProtocol.get)
        ctx
          .channel()
          .pipeline()
          .addLast(new JWebSocketServerProtocolHandler(config.build()))
          .addLast(WEB_SOCKET_HANDLER, ServerSocketHandler(zExec, res.socket.settings))
        ctx.fireChannelRead(jReq)
        ()
    }
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
