package zhttp.service.server

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.{
  HandshakeComplete,
  ServerHandshakeStateEvent,
}
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame}
import zhttp.service.{ChannelFuture, UnsafeChannelExecutor}
import zhttp.socket.SocketApp.Open
import zhttp.socket.{SocketApp, WebSocketFrame}
import zio.stream.ZStream

/**
 * Creates a new websocket handler
 */
final case class ServerSocketHandler[R](
  zExec: UnsafeChannelExecutor[R],
  ss: SocketApp.SocketConfig[R, Throwable],
) extends SimpleChannelInboundHandler[JWebSocketFrame] {

  /**
   * Unsafe channel reader for WSFrame
   */

  private def writeAndFlush(ctx: ChannelHandlerContext, stream: ZStream[R, Throwable, WebSocketFrame]): Unit =
    zExec.unsafeExecute_(ctx)(
      stream
        .mapM(frame => ChannelFuture.unit(ctx.writeAndFlush(frame.toWebSocketFrame)))
        .runDrain,
    )

  override def channelRead0(ctx: ChannelHandlerContext, msg: JWebSocketFrame): Unit =
    ss.onMessage match {
      case Some(v) =>
        WebSocketFrame.fromJFrame(msg) match {
          case Some(frame) => writeAndFlush(ctx, v(frame))
          case _           => ()
        }
      case None    => ()
    }

  override def exceptionCaught(ctx: ChannelHandlerContext, x: Throwable): Unit = {
    ss.onError match {
      case Some(v) => zExec.unsafeExecute_(ctx)(v(x).uninterruptible)
      case None    => ctx.fireExceptionCaught(x)
    }
    ()
  }

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit = {
    ss.onClose match {
      case Some(v) => zExec.unsafeExecute_(ctx)(v(ctx.channel().remoteAddress()).uninterruptible)
      case None    => ctx.fireChannelUnregistered()
    }
    ()
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, event: AnyRef): Unit = {

    event match {
      case _: HandshakeComplete                                                             =>
        ss.onOpen match {
          case Some(v) =>
            v match {
              case Open.WithEffect(f) => zExec.unsafeExecute_(ctx)(f(ctx.channel().remoteAddress()))
              case Open.WithSocket(s) => writeAndFlush(ctx, s(ctx.channel().remoteAddress()))
            }
          case None    => ctx.fireUserEventTriggered(event)
        }
      case m: ServerHandshakeStateEvent if m == ServerHandshakeStateEvent.HANDSHAKE_TIMEOUT =>
        ss.onTimeout match {
          case Some(v) => zExec.unsafeExecute_(ctx)(v)
          case None    => ctx.fireUserEventTriggered(event)
        }
      case event                                                                            => ctx.fireUserEventTriggered(event)
    }
    ()
  }
}
