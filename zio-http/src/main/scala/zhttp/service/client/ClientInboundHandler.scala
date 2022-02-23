package zhttp.service.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{FullHttpRequest, FullHttpResponse}
import zhttp.service.Client.ClientResponse
import zhttp.service.HttpRuntime
import zio.Promise

/**
 * Handles HTTP response
 */
final class ClientInboundHandler[R](
  zExec: HttpRuntime[R],
  jReq: FullHttpRequest,
  promise: Promise[Throwable, ClientResponse],
  isWebSocket: Boolean,
) extends SimpleChannelInboundHandler[FullHttpResponse](true) {

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    if (isWebSocket) {
      ctx.fireChannelActive(): Unit
    } else {
      ctx.writeAndFlush(jReq)
      releaseRequest()
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    msg.touch("handlers.ClientInboundHandler-channelRead0")
    // NOTE: The promise is made uninterruptible to be able to complete the promise in a error situation.
    // It allows to avoid loosing the message from pipeline in case the channel pipeline is closed due to an error.
    zExec.unsafeRun(ctx)(promise.succeed(ClientResponse.unsafeFromJResponse(msg)).uninterruptible)
    if (isWebSocket) {
      ctx.fireChannelRead(msg.retain())
      ctx.pipeline().remove(ctx.name()): Unit
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    zExec.unsafeRun(ctx)(promise.fail(error).uninterruptible)
    releaseRequest()
  }

  private def releaseRequest(): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }
}
