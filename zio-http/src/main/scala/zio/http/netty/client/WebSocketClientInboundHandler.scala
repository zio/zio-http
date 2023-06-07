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

import zio.{Promise, Trace, Unsafe}

import zio.http.Response
import zio.http.netty.{NettyResponse, NettyRuntime}

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpResponse

final class WebSocketClientInboundHandler(
  rtm: NettyRuntime,
  onResponse: Promise[Throwable, Response],
  onComplete: Promise[Throwable, ChannelState],
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[FullHttpResponse](true) {
  implicit private val unsafeClass: Unsafe = Unsafe.unsafe

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    ctx.fireChannelActive()
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    rtm.runUninterruptible(ctx, NettyRuntime.noopEnsuring) {
      onResponse.succeed(NettyResponse.make(ctx, msg))
    }(unsafeClass, trace)

    ctx.fireChannelRead(msg.retain())
    ctx.pipeline().remove(ctx.name()): Unit
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    rtm.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(
      onResponse.fail(error) *> onComplete.fail(error),
    )(unsafeClass, trace)
  }
}
