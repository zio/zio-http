package zhttp.service.server

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.{
  HandshakeComplete,
  ServerHandshakeStateEvent,
}
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame}
import zhttp.service.{ChannelFuture, HttpRuntime}
import zhttp.socket.SocketApp.Handle
import zhttp.socket.{SocketApp, WebSocketFrame}
import zio.stream.ZStream

/**
 * Creates a new websocket handler
 */
final case class ServerSocketHandler[R](
  zExec: HttpRuntime[R],
  ss: SocketApp[R],
) extends SimpleChannelInboundHandler[JWebSocketFrame] {

  /**
   * Unsafe channel reader for WSFrame
   */

  private def writeAndFlush(ctx: ChannelHandlerContext, stream: ZStream[R, Throwable, WebSocketFrame]): Unit =
    zExec.unsafeRun(ctx)(
      stream
        .mapM(frame => ChannelFuture.unit(ctx.writeAndFlush(frame.toWebSocketFrame)))
        .runDrain,
    )

  override def channelRead0(ctx: ChannelHandlerContext, msg: JWebSocketFrame): Unit =
    ss.message match {
      case Some(v) =>
        WebSocketFrame.fromJFrame(msg) match {
          case Some(frame) => writeAndFlush(ctx, v(frame))
          case _           => ()
        }
      case None    => ()
    }

  override def exceptionCaught(ctx: ChannelHandlerContext, x: Throwable): Unit = {
    ss.error match {
      case Some(v) => zExec.unsafeRun(ctx)(v(x).uninterruptible)
      case None    => ctx.fireExceptionCaught(x)
    }
    ()
  }

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit = {
    ss.close match {
      case Some(v) => zExec.unsafeRun(ctx)(v(ctx.channel().remoteAddress()).uninterruptible)
      case None    => ctx.fireChannelUnregistered()
    }
    ()
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, event: AnyRef): Unit = {

    event match {
      case _: HandshakeComplete                                                             =>
        ss.open match {
          case Some(v) =>
            v match {
              case Handle.WithEffect(f) => zExec.unsafeRun(ctx)(f(ctx.channel().remoteAddress()))
              case Handle.WithSocket(s) => writeAndFlush(ctx, s(ctx.channel().remoteAddress()))
            }
          case None    => ctx.fireUserEventTriggered(event)
        }
      case m: ServerHandshakeStateEvent if m == ServerHandshakeStateEvent.HANDSHAKE_TIMEOUT =>
        ss.timeout match {
          case Some(v) => zExec.unsafeRun(ctx)(v)
          case None    => ctx.fireUserEventTriggered(event)
        }
      case event => ctx.fireUserEventTriggered(event)
    }
    ()
  }
}
