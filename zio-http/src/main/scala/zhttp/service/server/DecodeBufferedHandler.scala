package zhttp.service.server

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler => JSimpleChannelInboundHandler}
import io.netty.handler.codec.http.{DefaultHttpContent => JDefaultHttpContent}
import zhttp.core._
import zhttp.service.UnsafeChannelExecutor
import zio._
import zio.stm.TQueue

/**
 * Handler that puts incoming messages into a ZQueue. Reading automatically stops once the queue is full.
 */
private[zhttp] final case class DecodeBufferedHandler[R](
  zExec: UnsafeChannelExecutor[R],
  q: TQueue[JDefaultHttpContent],
) extends JSimpleChannelInboundHandler[JDefaultHttpContent](false) {
  var composite = List.empty[JDefaultHttpContent]
  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.channel.config.setAutoRead(false)
    ctx.read()
    ()
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: JDefaultHttpContent): Unit = {
    composite = msg :: composite
  }

  override def channelReadComplete(ctx: JChannelHandlerContext): Unit = {
    val toOffer = composite
    composite = Nil
    zExec.unsafeExecute_(ctx) {
      q.offerAll(toOffer.reverse).commit *> UIO(ctx.read())
    }
  }

}
