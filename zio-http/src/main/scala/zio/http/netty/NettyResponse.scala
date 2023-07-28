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

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{FullHttpResponse, HttpResponse}
import zio.http.netty.client.{ChannelState, ClientResponseStreamHandler}
import zio.http.netty.model.Conversions
import zio.http.{Body, Header, Response}
import zio.{Promise, Trace, Unsafe, ZIO}
import zio.stacktracer.TracingImplicits.disableAutoTrace

object NettyResponse {

  final def apply(jRes: FullHttpResponse): Response = {
    val status       = Conversions.statusFromNetty(jRes.status())
    val headers      = Conversions.headersFromNetty(jRes.headers())
    val copiedBuffer = Unpooled.copiedBuffer(jRes.content())
    val data         = NettyBody.fromByteBuf(copiedBuffer, headers.header(Header.ContentType))

    // NativeResponse(Response(status, headers, Body.empty), () => NettyFutureExecutor.executed(ctx.close())),
    Response(status, headers, data)
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
  ): ZIO[Any, Nothing, Response] = {
    val status  = Conversions.statusFromNetty(jRes.status())
    val headers = Conversions.headersFromNetty(jRes.headers())

    if (headers.get(Header.ContentLength).map(_.length).contains(0L)) {
      onComplete
        .succeed(ChannelState.forStatus(status))
        .as(
          // NativeResponse(Response(status, headers, Body.empty), () => NettyFutureExecutor.executed(ctx.close())),
          Response(status, headers, Body.empty),
        )
    } else {
      val responseHandler = new ClientResponseStreamHandler(zExec, onComplete, keepAlive, status)
      ctx
        .pipeline()
        .addAfter(
          Names.ClientInboundHandler,
          Names.ClientStreamingBodyHandler,
          responseHandler,
        ): Unit

      val data = NettyBody.fromAsync { callback =>
        responseHandler.connect(callback)
      }
      // ZIO.succeed(NativeResponse(Response(status, headers, data), () => NettyFutureExecutor.executed(ctx.close())))
      ZIO.succeed(Response(status, headers, data))
    }
  }
}
