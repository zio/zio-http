package zhttp.service

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.ServerHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, WebSocketServerProtocolHandler}
import zhttp.service.ChannelEvent.UserEvent
import zhttp.socket.{SocketApp, WebSocketFrame}

/**
 * A generic SocketApp handler that can be used on both - the client and the
 * server.
 */
final class WebSocketAppHandler[R](
  zExec: HttpRuntime[R],
  app: SocketApp[R],
) extends SimpleChannelInboundHandler[JWebSocketFrame] {

  private def dispatch(ctx: ChannelHandlerContext)(event: ChannelEvent[WebSocketFrame, WebSocketFrame]): Unit =
    app.message match {
      case Some(f) => zExec.unsafeRun(ctx)(f(event))
      case None    => ()
    }

  override def channelRead0(ctx: ChannelHandlerContext, msg: JWebSocketFrame): Unit =
    dispatch(ctx) {
      ChannelEvent.channelRead(ctx, WebSocketFrame.unsafeFromJFrame(msg))
    }

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit =
    dispatch(ctx) {
      ChannelEvent.channelUnregistered(ctx)
    }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    dispatch(ctx) {
      ChannelEvent.exceptionCaught(ctx, cause)
    }

  override def userEventTriggered(ctx: ChannelHandlerContext, event: AnyRef): Unit =
    dispatch(ctx) {
      event match {
        case _: WebSocketServerProtocolHandler.HandshakeComplete | ClientHandshakeStateEvent.HANDSHAKE_COMPLETE =>
          ChannelEvent.userEventTriggered(ctx, UserEvent.HandshakeComplete)
        case ServerHandshakeStateEvent.HANDSHAKE_TIMEOUT | ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT          =>
          ChannelEvent.userEventTriggered(ctx, UserEvent.HandshakeTimeout)
      }
    }
}
