package zhttp.service.client.content.handlers

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.service.HttpRuntime
import zhttp.service.client.model.{Resp, ZConnectionState}

/**
 * Handles HTTP response
 */
@Sharable
final case class NewClientInboundHandler[R](
  zExec: HttpRuntime[R],
  zConnectionState: ZConnectionState,
) extends SimpleChannelInboundHandler[Resp](false) {

  override def channelRead0(ctx: ChannelHandlerContext, clientResponse: Resp): Unit = {
    //    println(s"SimpleChannelInboundHandler SimpleChannelInboundHandler CHANNEL READ CONTEXT ID: ${ctx.channel().id()}")
    val connectionRuntime = zConnectionState.currentAllocatedChannels(ctx.channel())
    zExec.unsafeRun(ctx) {
      connectionRuntime.callback.succeed(clientResponse)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    val connectionRuntime = zConnectionState.currentAllocatedChannels(ctx.channel())
    zExec.unsafeRun(ctx)(zio.Task.fail(error))
    releaseRequest(connectionRuntime.currReq)
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    //    println(s"CHANNEL ACTIVE CONTEXT ID: ${ctx.channel().id()}")
    val connectionRuntime = zConnectionState.currentAllocatedChannels(ctx.channel())
    val jReq              = connectionRuntime.currReq
    ctx.writeAndFlush(jReq): Unit
    releaseRequest(jReq)
    ()
  }

  private def releaseRequest(jReq: FullHttpRequest): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }

}
