package zhttp.service

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.ServerHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, WebSocketServerProtocolHandler}
import zhttp.socket.SocketApp.Handle
import zhttp.socket.{SocketApp, WebSocketFrame}
import zio.stream.ZStream

/**
 * A generic SocketApp handler that can be used on both - the client and the
 * server.
 */
final class WebSocketAppHandler[R](
  zExec: HttpRuntime[R],
  app: SocketApp[R],
) extends SimpleChannelInboundHandler[JWebSocketFrame] {

  override def channelRead0(ctx: ChannelHandlerContext, msg: JWebSocketFrame): Unit = {
    app.message match {
      case Some(v) =>
        WebSocketFrame.fromJFrame(msg) match {
          case Some(frame) => writeAndFlush(ctx, v(frame))
          case _           => ()
        }
      case None    => ()
    }
  }

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit = {
    app.close match {
      case Some(v) => zExec.unsafeRunUninterruptible(ctx)(v(ctx.channel().remoteAddress()))
      case None    => ctx.fireChannelUnregistered()
    }
    ()
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, x: Throwable): Unit = {
    app.error match {
      case Some(v) => zExec.unsafeRunUninterruptible(ctx)(v(x))
      case None    => ctx.fireExceptionCaught(x)
    }
    ()
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, event: AnyRef): Unit = {
    event match {
      case _: WebSocketServerProtocolHandler.HandshakeComplete | ClientHandshakeStateEvent.HANDSHAKE_COMPLETE =>
        app.open match {
          case Some(v) =>
            v match {
              case Handle.WithEffect(f) => zExec.unsafeRun(ctx)(f(ctx.channel().remoteAddress()))
              case Handle.WithSocket(s) => writeAndFlush(ctx, s(ctx.channel().remoteAddress()))
            }
          case None    => ctx.fireUserEventTriggered(event)
        }
      case ServerHandshakeStateEvent.HANDSHAKE_TIMEOUT | ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT          =>
        app.timeout match {
          case Some(v) => zExec.unsafeRun(ctx)(v)
          case None    => ctx.fireUserEventTriggered(event)
        }
      case event => ctx.fireUserEventTriggered(event)
    }
    ()
  }

  /**
   * Unsafe channel reader for WSFrame
   */
  private def writeAndFlush(ctx: ChannelHandlerContext, stream: ZStream[R, Throwable, WebSocketFrame]): Unit = {
    zExec.unsafeRun(ctx) {
      stream.foreach(frame => ChannelFuture.unit(ctx.writeAndFlush(frame.toWebSocketFrame)))
    }
  }
}
