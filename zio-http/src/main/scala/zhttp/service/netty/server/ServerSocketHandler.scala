package zhttp.service.netty.server

import zhttp.core.netty.{JChannelHandlerContext, JSimpleChannelInboundHandler, JWebSocketFrame}
import zhttp.domain.socket.WebSocketFrame
import zhttp.service.netty.{ChannelFuture, UnsafeChannelExecutor}
import zio.stream.ZStream

/**
 * Creates a new websocket handler
 */
final case class ServerSocketHandler[R](
  zExec: UnsafeChannelExecutor[R],
  socket: WebSocketFrame => ZStream[Any, Nothing, WebSocketFrame],
) extends JSimpleChannelInboundHandler[JWebSocketFrame] {

  /**
   * Unsafe channel reader for WSFrame
   */

  override def channelRead0(ctx: JChannelHandlerContext, msg: JWebSocketFrame): Unit = {
    WebSocketFrame.fromJFrame(msg) match {
      case Some(frame) =>
        zExec.unsafeExecute_(ctx) {
          socket(frame)
            .mapM(frame => ChannelFuture.unit(ctx.writeAndFlush(frame.toJWebSocketFrame)))
            .runDrain
        }

      case _ => ()
    }
  }
}
