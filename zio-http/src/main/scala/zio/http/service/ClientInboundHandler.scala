package zio.http.service

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{FullHttpRequest, FullHttpResponse}
import zio.http.Response
import zio.{Promise, Unsafe}

/**
 * Handles HTTP response
 */
final class ClientInboundHandler[R](
  zExec: HttpRuntime[R],
  jReq: FullHttpRequest,
  promise: Promise[Throwable, Response],
  isWebSocket: Boolean,
) extends SimpleChannelInboundHandler[FullHttpResponse](true) {
  implicit private val unsafeClass: Unsafe = Unsafe.unsafe

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
    zExec.runUninterruptible(promise.succeed(Response.unsafe.fromJResponse(ctx, msg)))(ctx, unsafeClass)

    if (isWebSocket) {
      ctx.fireChannelRead(msg.retain())
      ctx.pipeline().remove(ctx.name()): Unit
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    zExec.runUninterruptible(promise.fail(error))(ctx, unsafeClass)
    releaseRequest()
  }

  private def releaseRequest(): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }
}
