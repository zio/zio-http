package zio.http.netty.client

import zio.{Promise, Trace, Unsafe}

import zio.http.Response
import zio.http.netty.NettyRuntime

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}

/** Handles failures happening in ClientInboundHandler */
final class ClientFailureHandler(
  rtm: NettyRuntime,
  onResponse: Promise[Throwable, Response],
  onComplete: Promise[Throwable, ChannelState],
)(implicit trace: Trace)
    extends ChannelInboundHandlerAdapter {
  implicit private val unsafeClass: Unsafe = Unsafe.unsafe

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    rtm.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(
      onResponse.fail(cause) *> onComplete.fail(cause),
    )(unsafeClass, trace)
  }
}
