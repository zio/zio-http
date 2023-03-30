/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.netty.client

import zio._

import zio.http.Response
import zio.http.netty.{NettyFutureExecutor, NettyResponse, NettyRuntime}

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{FullHttpRequest, FullHttpResponse, HttpUtil}

/**
 * Handles HTTP response
 */
final class ClientInboundHandler(
  zExec: NettyRuntime,
  jReq: FullHttpRequest,
  onResponse: Promise[Throwable, Response],
  onComplete: Promise[Throwable, ChannelState],
  isWebSocket: Boolean,
  enableKeepAlive: Boolean,
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
      onResponse.succeed(NettyResponse.make(ctx, msg))
    }(unsafeClass, trace)

    if (isWebSocket) {
      ctx.fireChannelRead(msg.retain())
      ctx.pipeline().remove(ctx.name()): Unit
    }

    val shouldKeepAlive = enableKeepAlive && HttpUtil.isKeepAlive(msg) || isWebSocket

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
