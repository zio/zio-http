package zio.http.service

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{FullHttpRequest, FullHttpResponse}
import zio.Promise
import zio.http.Response

/**
 * Handles HTTP response
 */
final class ClientInboundHandler[R](
  zExec: HttpRuntime[R],
  jReq: FullHttpRequest,
  promise: Promise[Throwable, Response],
  isWebSocket: Boolean,
) extends SimpleChannelInboundHandler[FullHttpResponse](true) {

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    if (isWebSocket) {
      ctx.fireChannelActive(): Unit
    } else {
      ctx.writeAndFlush(jReq)
      ()
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    msg.touch("handlers.ClientInboundHandler-channelRead0")
    // NOTE: The promise is made uninterruptible to be able to complete the promise in a error situation.
    // It allows to avoid loosing the message from pipeline in case the channel pipeline is closed due to an error.
    zExec.unsafeRunUninterruptible(promise.succeed(Response.unsafeFromJResponse(ctx, msg)))(ctx)

    if (isWebSocket) {
      ctx.fireChannelRead(msg.retain())
      ctx.pipeline().remove(ctx.name()): Unit
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    zExec.unsafeRunUninterruptible(promise.fail(error))(ctx)
    releaseRequest()
  }

  private def releaseRequest(): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }
}
