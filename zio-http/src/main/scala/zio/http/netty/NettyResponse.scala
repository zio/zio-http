package zio.http.netty

import zio.{Promise, Trace, Unsafe}

import zio.http.Response.NativeResponse
import zio.http.model.{Headers, Status}
import zio.http.netty.client.{ChannelState, ClientResponseStreamHandler}
import zio.http.netty.model.Conversions
import zio.http.{Body, Response}

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{FullHttpResponse, HttpResponse}

object NettyResponse {

  final def make(ctx: ChannelHandlerContext, jRes: FullHttpResponse)(implicit
    unsafe: Unsafe,
  ): NativeResponse = {
    val status       = Conversions.statusFromNetty(jRes.status())
    val headers      = Conversions.headersFromNetty(jRes.headers())
    val copiedBuffer = Unpooled.copiedBuffer(jRes.content())
    val data         = Body.fromByteBuf(copiedBuffer)

    new NativeResponse(data, headers, status, () => NettyFutureExecutor.executed(ctx.close()))
  }

  final def make(
    ctx: ChannelHandlerContext,
    jRes: HttpResponse,
    zExec: NettyRuntime,
    onComplete: Promise[Throwable, ChannelState],
    keepAlive: Boolean,
  )(implicit
    unsafe: Unsafe,
    trace: Trace,
  ): Response = {
    val status  = Conversions.statusFromNetty(jRes.status())
    val headers = Conversions.headersFromNetty(jRes.headers())
    val data    = Body.fromAsync { callback =>
      ctx
        .pipeline()
        .addAfter(
          Names.ClientInboundHandler,
          Names.ClientStreamingBodyHandler,
          new ClientResponseStreamHandler(callback, zExec, onComplete, keepAlive),
        ): Unit
    }
    new NativeResponse(data, headers, status, () => NettyFutureExecutor.executed(ctx.close()))
  }
}
