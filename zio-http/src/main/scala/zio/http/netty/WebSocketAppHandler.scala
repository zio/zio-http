package zio.http.netty

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.ServerHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.{WebSocketServerProtocolHandler, WebSocketFrame => JWebSocketFrame}
import zio._
import zio.http.ChannelEvent
import zio.http.ChannelEvent.UserEvent
import zio.http.service.Log
import zio.http.socket.{SocketApp, SocketAppEvent, SocketAppChannel, WebSocketFrame}
import zio.logging.Logger
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * A generic SocketApp handler that can be used on both - the client and the
 * server.
 */
private[zio] final class WebSocketAppHandler(
  zExec: NettyRuntime,
  app: SocketApp[Any],
  isClient: Boolean,
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[JWebSocketFrame] {

  private[zio] val log = if (isClient) WebSocketAppHandler.clientLog else WebSocketAppHandler.serverLog
  implicit private val unsafeClass: Unsafe = Unsafe.unsafe

  private def dispatch(
    ctx: ChannelHandlerContext,
    event: ChannelEvent[JWebSocketFrame, JWebSocketFrame],
  ): Unit = {
    log.debug(s"ChannelEvent: [${ event.event }]")
    val webSocketFrameEvent = event.map(WebSocketFrame.unsafe.fromJFrame).contramap[WebSocketFrame](_.toWebSocketFrame)
    val appChannel = SocketAppChannel(webSocketFrameEvent.channel)
    toSocketAppEvent(appChannel, webSocketFrameEvent.event).foreach { appEvent =>
      log.debug(s"SocketAppEvent: [$appEvent]")
      app.message(appEvent).foreach { effect =>
        zExec.runUninterruptible(ctx) {
          for {
            action <- effect
            _ = log.debug(s"SocketAppAction: [$action]")
            _ <- appChannel.action(action)
          } yield ()
        }
      }
    }
  }

  private def toSocketAppEvent(socketChannel: SocketAppChannel, event: ChannelEvent.Event[WebSocketFrame]): Option[SocketAppEvent] = event match {
      case ChannelEvent.ChannelRead(msg) => Some(SocketAppEvent.FrameReceived(msg))
      case ChannelEvent.UserEventTriggered(UserEvent.HandshakeComplete) => Some(SocketAppEvent.Connected(socketChannel))
      case ChannelEvent.ChannelUnregistered => Some(SocketAppEvent.Disconnected)
      case ChannelEvent.ExceptionCaught(cause) => Some(SocketAppEvent.Error(cause))
      case ChannelEvent.ChannelRegistered => Some(SocketAppEvent.Disconnected)
      case _ => None
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
  val clientLog: Logger = Log.withTags("Client", "WebSocket")
  val serverLog: Logger = Log.withTags("Server", "WebSocket")
}
