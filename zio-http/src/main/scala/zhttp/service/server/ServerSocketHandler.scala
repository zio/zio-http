package zhttp.service.server

import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete
import zhttp.core.{JChannelHandlerContext, JSimpleChannelInboundHandler, JWebSocketFrame}
import zhttp.service.{ChannelFuture, UnsafeChannelExecutor}
import zhttp.socket.{Socket, WebSocketFrame}
import zio.stream.ZStream
import zio.{Exit, ZIO}

/**
 * Creates a new websocket handler
 */
final case class ServerSocketHandler[R](
  zExec: UnsafeChannelExecutor[R],
  ss: Socket.Settings[R, Throwable],
) extends JSimpleChannelInboundHandler[JWebSocketFrame] {

  /**
   * Unsafe channel reader for WSFrame
   */

  private def writeAndFlush(ctx: JChannelHandlerContext, stream: ZStream[R, Throwable, WebSocketFrame]): Unit =
    executeAsync(
      ctx,
      stream
        .mapM(frame => ChannelFuture.unit(ctx.writeAndFlush(frame.toJWebSocketFrame)))
        .runDrain,
    )

  override def channelRead0(ctx: JChannelHandlerContext, msg: JWebSocketFrame): Unit = {
    WebSocketFrame.fromJFrame(msg) match {
      case Some(frame) => writeAndFlush(ctx, ss.onMessage(frame))
      case _           => ()
    }
  }

  def executeAsync(ctx: JChannelHandlerContext, program: ZIO[R, Throwable, Unit]): Unit = {
    zExec.unsafeExecute(ctx, program) {
      case Exit.Success(_)     => ()
      case Exit.Failure(cause) =>
        cause.failureOption match {
          case Some(error: Throwable) => ctx.fireExceptionCaught(error)
          case _                      => ()
        }
        ctx.close()
        ()
    }
  }

  override def exceptionCaught(ctx: JChannelHandlerContext, x: Throwable): Unit =
    executeAsync(ctx, ss.onError(x).uninterruptible)

  override def channelUnregistered(ctx: JChannelHandlerContext): Unit =
    executeAsync(ctx, ss.onClose(ctx.channel().remoteAddress()).uninterruptible)

  override def userEventTriggered(ctx: JChannelHandlerContext, event: AnyRef): Unit = {
    event match {
      case _: HandshakeComplete => writeAndFlush(ctx, ss.onOpen(ctx.channel().remoteAddress()))
      case event                => ctx.fireUserEventTriggered(event)
    }
    ()
  }
}
