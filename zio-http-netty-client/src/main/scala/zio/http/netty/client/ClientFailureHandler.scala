package zio.http.netty.client

import zio.{Exit, Promise, Unsafe}

import zio.http.ClientDriver.ChannelState
import zio.http.Response

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}

/** Handles failures happening in ClientInboundHandler */
private[netty] final class ClientFailureHandler(
  onResponse: Promise[Throwable, Response],
  onComplete: Promise[Throwable, ChannelState],
) extends ChannelInboundHandlerAdapter {
  implicit private val unsafeClass: Unsafe = Unsafe.unsafe

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    val exit = Exit.fail(cause)
    onResponse.unsafe.done(exit)
    onComplete.unsafe.done(exit)
  }
}
