package zhttp.service.server

import io.netty.handler.codec.http.websocketx.{WebSocketServerHandshakerFactory => JWebSocketServerHandshakerFactory}
import zhttp.core._
import zhttp.http.{Response, _}
import zhttp.service._
import zio.Exit

/**
 * Helper class with channel methods
 */
@JSharable
final case class ServerRequestHandler[R](
  zExec: UnsafeChannelExecutor[R],
  app: HttpApp[R, Nothing],
) extends JSimpleChannelInboundHandler[JFullHttpRequest](AUTO_RELEASE_REQUEST)
    with ServerJHttpRequestDecoder
    with ServerHttpExceptionHandler {

  self =>

  /**
   * Tries to release the request byte buffer, ignores if it can not.
   */
  private def releaseOrIgnore(jReq: JFullHttpRequest): Boolean = jReq.release(jReq.content().refCnt())

  private def webSocketUpgrade(
    ctx: JChannelHandlerContext,
    jReq: JFullHttpRequest,
    res: Response.SocketResponse,
  ): Unit = {
    val hh = new JWebSocketServerHandshakerFactory(jReq.uri(), res.subProtocol.orNull, false).newHandshaker(jReq)
    if (hh == null) {
      JWebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel, ctx.channel().voidPromise())
    } else {
      try {
        // handshake can throw
        hh.handshake(ctx.channel(), jReq).addListener { (future: JChannelFuture) =>
          if (!future.isSuccess) {
            ctx.fireExceptionCaught(future.cause)
            ()
          } else {
            val pl = ctx.channel().pipeline()
            pl.addLast(WEB_SOCKET_HANDLER, ServerSocketHandler(zExec, res.socket))
            pl.remove(HTTP_REQUEST_HANDLER)
            ()
          }
        }
      } catch {
        case _: JWebSocketHandshakeException =>
          JWebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel, ctx.channel().voidPromise())
      }
    }
    ()
  }

  def writeAndFlush(ctx: JChannelHandlerContext, jReq: JFullHttpRequest, res: Response): Unit = {
    res match {
      case res @ Response.HttpResponse(_, _, _) =>
        ctx.writeAndFlush(res.asInstanceOf[Response.HttpResponse].toJFullHttpResponse, ctx.channel().voidPromise())
        releaseOrIgnore(jReq)
        ()
      case res @ Response.SocketResponse(_, _)  =>
        self.webSocketUpgrade(ctx, jReq, res)
        releaseOrIgnore(jReq)
        ()
    }
  }

  /**
   * Unsafe channel reader for HttpRequest
   */
  override def channelRead0(ctx: JChannelHandlerContext, jReq: JFullHttpRequest /* jReq.refCount = 1 */ ): Unit = {
    app.eval(unsafelyDecodeJFullHttpRequest(jReq)) match {
      case HttpResult.Success(a)    =>
        self.writeAndFlush(ctx, jReq, a)
        ()
      case HttpResult.Continue(zio) =>
        zExec.unsafeExecute(ctx, zio) {
          case Exit.Success(res) =>
            writeAndFlush(ctx, jReq, res)
            ()
          case _                 => ()
        }
      case _                        => ()
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
