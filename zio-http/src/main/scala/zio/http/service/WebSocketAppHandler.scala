package zio.http.service

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.ServerHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, WebSocketServerProtocolHandler}
import zio.Unsafe
import zio.http.ChannelEvent
import zio.http.ChannelEvent.UserEvent
import zio.http.socket.{SocketApp, WebSocketFrame}
import zio.logging.Logger

/**
 * A generic SocketApp handler that can be used on both - the client and the
 * server.
 */
final class WebSocketAppHandler[R](
  zExec: HttpRuntime[R],
  app: SocketApp[R],
  isClient: Boolean,
) extends SimpleChannelInboundHandler[JWebSocketFrame] {

  private[zio] val log = if (isClient) WebSocketAppHandler.clientLog else WebSocketAppHandler.serverLog
  implicit private val unsafeClass: Unsafe = Unsafe.unsafe

  private def dispatch(
    event: ChannelEvent[JWebSocketFrame, JWebSocketFrame],
  )(implicit ctx: ChannelHandlerContext): Unit = {
    log.debug(s"ChannelEvent: [${event.event}]")
    app.message match {
      case Some(f) =>
        zExec.runUninterruptible(
          f(event.map(WebSocketFrame.unsafe.fromJFrame).contramap[WebSocketFrame](_.toWebSocketFrame)),
        )(ctx, unsafeClass)
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
