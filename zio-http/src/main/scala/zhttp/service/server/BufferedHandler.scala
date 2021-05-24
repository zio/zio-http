package zhttp.service.server

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler => JSimpleChannelInboundHandler}
import io.netty.handler.codec.http.{DefaultHttpContent => JDefaultHttpContent}
import zhttp.service.UnsafeChannelExecutor
import zio._
import zhttp.core._

/**
 * Handler that puts incoming messages into a ZQueue. Reading automatically stops once the queue is full.
 */
final case class BufferedHandler[R](
  zExec: UnsafeChannelExecutor[R],
  q: Queue[HBuf[Nat.One, Direction.Out]],
) extends JSimpleChannelInboundHandler[JDefaultHttpContent](true) {

  @volatile var readCompleted = false
  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.channel.config.setAutoRead(false)
    ctx.read()
    ()
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: JDefaultHttpContent): Unit = {
    val byteArray = HBuf.one[Direction.Out](msg.content())
    zExec.unsafeExecute_(ctx) {
      q.offer(byteArray) *> UIO(if (readCompleted) ctx.read())
    }
  }

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    readCompleted = true
  }
}
