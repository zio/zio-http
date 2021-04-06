package zhttp.service.server

import zhttp.core.{JChannelHandlerContext, JSimpleChannelInboundHandler, JWebSocketFrame}
import zhttp.service.{ChannelFuture, UnsafeChannelExecutor}
import zhttp.socket.WebSocketFrame
import zhttp.http.SocketServer

/**
 * Creates a new websocket handler
 */
final case class ServerSocketHandler[R, E](
  zExec: UnsafeChannelExecutor[R],
  ss: SocketServer.Settings[R, E],
) extends JSimpleChannelInboundHandler[JWebSocketFrame] {

  /**
   * Unsafe channel reader for WSFrame
   */

  override def channelRead0(ctx: JChannelHandlerContext, msg: JWebSocketFrame): Unit = {
    WebSocketFrame.fromJFrame(msg) match {
      case Some(frame) =>
        zExec.unsafeExecute_(ctx) {
          ss.onMessage(frame)
            .mapM(frame => ChannelFuture.unit(ctx.writeAndFlush(frame.toJWebSocketFrame)))
            .runDrain
        }

      case _ => ()
    }
  }

  override def exceptionCaught(ctx: JChannelHandlerContext, x: Throwable): Unit =
    zExec.unsafeExecute_(ctx)(ss.onError(x))

  override def channelUnregistered(ctx: JChannelHandlerContext): Unit =
    zExec.unsafeExecute_(ctx)(ss.onClose(ctx.channel().remoteAddress(), None))
}
