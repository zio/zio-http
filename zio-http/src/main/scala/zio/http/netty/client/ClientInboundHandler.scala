package zio.http.netty.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{FullHttpRequest, FullHttpResponse}
import zio._
import zio.http.Response
import zio.http.netty.NettyRuntime
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Handles HTTP response
 */
final class ClientInboundHandler(
  zExec: NettyRuntime,
  jReq: FullHttpRequest,
  promise: Promise[Throwable, Response],
  isWebSocket: Boolean,
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[FullHttpResponse](true) {
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
    zExec.runUninterruptible(ctx)(promise.succeed(Response.unsafe.fromJResponse(ctx, msg)))(unsafeClass, trace)

    if (isWebSocket) {
      ctx.fireChannelRead(msg.retain())
      ctx.pipeline().remove(ctx.name()): Unit
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    zExec.runUninterruptible(ctx)(promise.fail(error))
    releaseRequest()
  }

  private def releaseRequest(): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }
}
