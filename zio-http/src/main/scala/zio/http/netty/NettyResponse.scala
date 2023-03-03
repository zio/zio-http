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
