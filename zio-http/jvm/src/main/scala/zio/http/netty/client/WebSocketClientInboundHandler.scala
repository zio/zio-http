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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Exit, Promise, Unsafe}

import zio.http.Response
import zio.http.internal.ChannelState
import zio.http.netty.NettyResponse

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.FullHttpResponse

final class WebSocketClientInboundHandler(
  onResponse: Promise[Throwable, Response],
  onComplete: Promise[Throwable, ChannelState],
) extends SimpleChannelInboundHandler[FullHttpResponse](true) {
  implicit private val unsafeClass: Unsafe = Unsafe.unsafe

  override def channelActive(ctx: ChannelHandlerContext): Unit =
    ctx.fireChannelActive(): Unit

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    onResponse.unsafe.done(Exit.succeed(NettyResponse(msg)))
    ctx.fireChannelRead(msg.retain())
    ctx.pipeline().remove(ctx.name()): Unit
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    val exit = Exit.fail(error)
    onResponse.unsafe.done(exit)
    onComplete.unsafe.done(exit)
  }
}
