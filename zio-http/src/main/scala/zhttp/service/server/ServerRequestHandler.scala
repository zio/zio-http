package zhttp.service.server

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.websocketx.{WebSocketServerHandshakerFactory => JWebSocketServerHandshakerFactory}
import io.netty.handler.codec.http.{HttpHeaderNames, HttpResponseStatus, HttpVersion}
import zhttp.core.{JFullHttpRequest, _}
import zhttp.http.{Response, _}
import zhttp.service._

/**
 * Helper class with channel methods
 */
@JSharable
final case class ServerRequestHandler[R](
  zExec: UnsafeChannelExecutor[R],
  app: HttpApp[R, Nothing],
) extends JChannelInboundHandlerAdapter
    with ServerJHttpRequestDecoder
    with ServerHttpExceptionHandler {

  self =>

  /**
   * Tries to release the request byte buffer, ignores if it can not.
   */
  def releaseOrIgnore(jReq: JFullHttpRequest): Boolean = jReq.release(jReq.content().refCnt())

  def webSocketUpgrade(
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

  def writeAndFlush(ctx: JChannelHandlerContext): Unit = {
    val headers = new JDefaultHttpHeaders()
    headers.set(HttpHeaderNames.CONTENT_LENGTH, "Hello world".length())
    ctx.writeAndFlush(
      new JDefaultFullHttpResponse(
        HttpVersion.HTTP_1_1,
        HttpResponseStatus.OK,
        Unpooled.copiedBuffer("Hello world", HTTP_CHARSET),
        headers,
        new JDefaultHttpHeaders(false),
      ),
      ctx.channel().voidPromise(),
    )
    ()
  }

  /**
   * Unsafe channel reader for HttpRequest
   */
  override def channelRead(ctx: JChannelHandlerContext, msg: Any): Unit = {
    val headers = new JDefaultHttpHeaders()
    headers.set(HttpHeaderNames.CONTENT_LENGTH, "Hello world".length())
    ctx.writeAndFlush(
      new JDefaultFullHttpResponse(
        HttpVersion.HTTP_1_1,
        HttpResponseStatus.OK,
        Unpooled.copiedBuffer("Hello world", HTTP_CHARSET),
        headers,
        new JDefaultHttpHeaders(false),
      ),
      ctx.channel().voidPromise(),
    )
    ()
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
