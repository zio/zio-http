package zhttp.service

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.ServerHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, WebSocketServerProtocolHandler}
import zhttp.logging.Logger
import zhttp.service.ChannelEvent.UserEvent
import zhttp.socket.{SocketApp, WebSocketFrame}

/**
 * A generic SocketApp handler that can be used on both - the client and the
 * server.
 */
final class WebSocketAppHandler[R](
  zExec: HttpRuntime[R],
  app: SocketApp[R],
  isClient: Boolean,
) extends SimpleChannelInboundHandler[JWebSocketFrame] {

  private[zhttp] val log = if (isClient) WebSocketAppHandler.clientLog else WebSocketAppHandler.serverLog

  private def dispatch(
    event: ChannelEvent[JWebSocketFrame, JWebSocketFrame],
  )(implicit ctx: ChannelHandlerContext): Unit = {
    log.debug(s"ChannelEvent: [${event.event}]")
    app.message match {
      case Some(f) =>
        zExec.unsafeRunUninterruptible(
          f(event.map(WebSocketFrame.unsafeFromJFrame).contramap[WebSocketFrame](_.toWebSocketFrame)),
        )
      case None    => ()
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: JWebSocketFrame): Unit =
    dispatch(ChannelEvent.channelRead(ctx, msg))(ctx)

  override def channelRegistered(ctx: Ctx): Unit =
    dispatch(ChannelEvent.channelRegistered(ctx))(ctx)

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit =
    dispatch(ChannelEvent.channelUnregistered(ctx))(ctx)

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    dispatch(ChannelEvent.exceptionCaught(ctx, cause))(ctx)

  override def userEventTriggered(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    msg match {
      case _: WebSocketServerProtocolHandler.HandshakeComplete | ClientHandshakeStateEvent.HANDSHAKE_COMPLETE =>
        dispatch(ChannelEvent.userEventTriggered(ctx, UserEvent.HandshakeComplete))(ctx)
      case ServerHandshakeStateEvent.HANDSHAKE_TIMEOUT | ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT          =>
        dispatch(ChannelEvent.userEventTriggered(ctx, UserEvent.HandshakeTimeout))(ctx)
      case _ => super.userEventTriggered(ctx, msg)
    }
  }
}

private object WebSocketAppHandler {
  val clientLog: Logger = Log.withTags("Client", "WebSocket")
  val serverLog: Logger = Log.withTags("Server", "WebSocket")
}
