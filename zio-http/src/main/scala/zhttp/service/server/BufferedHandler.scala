package zhttp.service.server

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler => JSimpleChannelInboundHandler}
import io.netty.handler.codec.http.{DefaultHttpContent => JDefaultHttpContent, LastHttpContent => JLastHttpContent}
import zhttp.service.UnsafeChannelExecutor
import zio._
import zhttp.core._
import zio.stm.TQueue

/**
 * Handler that puts incoming messages into a ZQueue. Reading automatically stops once the queue is full.
 */
private[zhttp] final case class BufferedHandler[R](
  zExec: UnsafeChannelExecutor[R],
  q: TQueue[HBuf1[Direction.In]],
) extends JSimpleChannelInboundHandler[JDefaultHttpContent](false) {
//  private val SIZE     = 1024
  var composite        = List.empty[JByteBuf]
  @volatile var isLast = false
  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.channel.config.setAutoRead(false)
    ctx.read()
    ()
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: JDefaultHttpContent): Unit = {
    composite = msg.content() :: composite
    isLast = msg.isInstanceOf[JLastHttpContent]

//    println(s"Read: ${msg.content().readableBytes()}")
//    val byteArray = HBuf.one[Direction.In](msg.content())
//
//    Unpooled.wrappedBuffer()
//    val isLast    = msg.isInstanceOf[JLastHttpContent]
//    zExec.unsafeExecute_(ctx) {
//      q.offer(byteArray).commit *>
//        (if (isLast) q.offer(HBuf.empty).commit else UIO(ctx.read()))
//    }
  }

  override def channelReadComplete(ctx: JChannelHandlerContext): Unit = {
    val toOffer = composite
    composite = Nil

//    println("Complete")

    zExec.unsafeExecute_(ctx) {
      // ZIO Threads
      q.offerAll(toOffer.reverse.map(HBuf.one(_))).commit *>
        (if (isLast) q.offer(HBuf.empty).commit else UIO(ctx.read()))
    }
  }

}
