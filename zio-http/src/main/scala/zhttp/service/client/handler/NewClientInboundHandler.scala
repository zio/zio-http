package zhttp.service.client.handler

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.service.Client.ClientResponse
import zhttp.service.HttpRuntime
import zhttp.service.client.model.ClientConnectionState

/**
 * Handles HTTP response
 */
@Sharable
final case class NewClientInboundHandler[R](
  zExec: HttpRuntime[R],
  zConnectionState: ClientConnectionState,
) extends SimpleChannelInboundHandler[ClientResponse](false) {

  override def channelRead0(ctx: ChannelHandlerContext, clientResponse: ClientResponse): Unit = {
    val connectionRuntime = zConnectionState.currentAllocatedChannels(ctx.channel())
    zExec.unsafeRun(ctx) {
      connectionRuntime.callback.succeed(clientResponse)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    val connectionRuntime = zConnectionState.currentAllocatedChannels(ctx.channel())
    zExec.unsafeRun(ctx)(zio.Task.fail(error))
    releaseRequest(connectionRuntime.currReq): Unit
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    val connectionRuntime = zConnectionState.currentAllocatedChannels(ctx.channel())
    val jReq              = connectionRuntime.currReq
    ctx.writeAndFlush(jReq)
    releaseRequest(jReq): Unit
  }

  private def releaseRequest(jReq: FullHttpRequest): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }

}
