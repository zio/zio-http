package zhttp.service.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame}
import zhttp.service.{ChannelFuture, HttpRuntime}
import zhttp.socket.SocketApp.Handle
import zhttp.socket.{SocketApp, WebSocketFrame}
import zio.stream.ZStream

/**
 * Creates a new websocket client handler
 */
final case class ClientSocketHandler[R](
  zExec: HttpRuntime[R],
  ss: SocketApp[R],
) extends SimpleChannelInboundHandler[JWebSocketFrame] {

  /**
   * Unsafe write and flush for websocket frame
   */
  def writeAndFlush(ctx: ChannelHandlerContext, stream: ZStream[R, Throwable, WebSocketFrame]) =
    zExec.unsafeRun(ctx)(
      stream
        .mapM(frame => ChannelFuture.unit(ctx.writeAndFlush(frame.toWebSocketFrame)))
        .runDrain,
    )

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit = {
    ss.close match {
      case Some(v) => zExec.unsafeRun(ctx)(v(ctx.channel().remoteAddress()).uninterruptible)
      case None    => ctx.fireChannelUnregistered()
    }
    ()
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, event: AnyRef): Unit = {
    import ClientHandshakeStateEvent._

    event match {
      case HANDSHAKE_COMPLETE =>
        ss.open match {
          case Some(v) =>
            v match {
              case Handle.WithEffect(f) => zExec.unsafeRun(ctx)(f(ctx.channel().remoteAddress()))
              case Handle.WithSocket(s) => writeAndFlush(ctx, s(ctx.channel().remoteAddress()))
            }
          case None    => ctx.fireUserEventTriggered(event): Unit
        }
      case HANDSHAKE_TIMEOUT  =>
        ss.timeout match {
          case Some(v) => zExec.unsafeRun(ctx)(v)
          case None    => ctx.fireUserEventTriggered(event): Unit
        }
      case _                  => ctx.fireUserEventTriggered(event): Unit
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: JWebSocketFrame): Unit = {
    ss.message match {
      case Some(v) =>
        WebSocketFrame.fromJFrame(msg) match {
          case Some(frame) => writeAndFlush(ctx, v(frame))
          case None        => ()
        }
      case None    => ()
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, x: Throwable): Unit = {
    ss.error match {
      case Some(v) => zExec.unsafeRun(ctx)(v(x).uninterruptible)
      case None    => ctx.fireExceptionCaught(x)
    }
    ()
  }
}
