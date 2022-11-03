package zio.http.netty.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{FullHttpRequest, FullHttpResponse, HttpUtil}
import zio._
import zio.http.Response
import zio.http.netty.{NettyFutureExecutor, NettyRuntime}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Handles HTTP response
 */
final class ClientInboundHandler(
  zExec: NettyRuntime,
  jReq: FullHttpRequest,
  onResponse: Promise[Throwable, Response],
  onComplete: Promise[Throwable, ChannelState],
  isWebSocket: Boolean,
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[FullHttpResponse](true) {
  implicit private val unsafeClass: Unsafe = Unsafe.unsafe

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    if (isWebSocket) {
      ctx.fireChannelActive()
      ()
    } else {
      sendRequest(ctx)
    }
  }

  private def sendRequest(ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(jReq)
    ()
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    msg.touch("handlers.ClientInboundHandler-channelRead0")
    // NOTE: The promise is made uninterruptible to be able to complete the promise in a error situation.
    // It allows to avoid loosing the message from pipeline in case the channel pipeline is closed due to an error.
    zExec.runUninterruptible(ctx, NettyRuntime.noopEnsuring) {
      onResponse.succeed(Response.unsafe.fromJResponse(ctx, msg))
    }(unsafeClass, trace)

    if (isWebSocket) {
      ctx.fireChannelRead(msg.retain())
      ctx.pipeline().remove(ctx.name()): Unit
    }

    val shouldKeepAlive = HttpUtil.isKeepAlive(msg) || isWebSocket

    if (!shouldKeepAlive) {
      zExec.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(
        NettyFutureExecutor
          .executed(ctx.close())
          .as(ChannelState.Invalid)
          .exit
          .flatMap(onComplete.done(_)),
      )(unsafeClass, trace)
    } else {
      zExec.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(onComplete.succeed(ChannelState.Reusable))(
        unsafeClass,
        trace,
      )
    }

  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    zExec.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(
      onResponse.fail(error) *> onComplete.fail(error),
    )(unsafeClass, trace)
    releaseRequest()
  }

  private def releaseRequest(): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }
}
