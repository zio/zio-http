package zio.http
package netty

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.ServerHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, WebSocketServerProtocolHandler}
import zio.http.ChannelEvent
import zio.http.ChannelEvent.UserEvent
import zio.http.socket.{SocketApp, WebSocketFrame}
import zio.logging.Logger

/**
 * A generic SocketApp handler that can be used on both - the client and the
 * server.
 */
private[zio] final class WebSocketAppHandler(
  zExec: NettyRuntime,
  app: SocketApp[Any],
  isClient: Boolean,
) extends SimpleChannelInboundHandler[JWebSocketFrame] {

  private[zio] val log = if (isClient) WebSocketAppHandler.clientLog else WebSocketAppHandler.serverLog

  private def dispatch(
    ctx: ChannelHandlerContext,
    event: ChannelEvent[JWebSocketFrame, JWebSocketFrame],
  ): Unit = {
    log.debug(s"ChannelEvent: [${event.event}]")
    app.message match {
      case Some(f) =>
        zExec.unsafeRunUninterruptible(ctx)(
          f(event.map(WebSocketFrame.unsafeFromJFrame).contramap[WebSocketFrame](_.toWebSocketFrame)),
        )
      case None    => ()
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: JWebSocketFrame): Unit =
    dispatch(ctx, ChannelEvent.channelRead(ctx, msg))

  override def channelRegistered(ctx: ChannelHandlerContext): Unit =
    dispatch(ctx, ChannelEvent.channelRegistered(ctx))

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit =
    dispatch(ctx, ChannelEvent.channelUnregistered(ctx))

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    dispatch(ctx, ChannelEvent.exceptionCaught(ctx, cause))

  override def userEventTriggered(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    msg match {
      case _: WebSocketServerProtocolHandler.HandshakeComplete | ClientHandshakeStateEvent.HANDSHAKE_COMPLETE =>
        dispatch(ctx, ChannelEvent.userEventTriggered(ctx, UserEvent.HandshakeComplete))
      case ServerHandshakeStateEvent.HANDSHAKE_TIMEOUT | ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT          =>
        dispatch(ctx, ChannelEvent.userEventTriggered(ctx, UserEvent.HandshakeTimeout))
      case _ => super.userEventTriggered(ctx, msg)
    }
  }
}

private[zio] object WebSocketAppHandler {
  val clientLog: Logger = service.Log.withTags("Client", "WebSocket")
  val serverLog: Logger = service.Log.withTags("Server", "WebSocket")
}
