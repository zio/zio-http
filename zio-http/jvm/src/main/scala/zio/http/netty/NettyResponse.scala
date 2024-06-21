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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Exit, Promise, Trace, Unsafe, ZIO}

import zio.http.internal.ChannelState
import zio.http.netty.client.ClientResponseStreamHandler
import zio.http.netty.model.Conversions
import zio.http.{Body, Header, Response}

import io.netty.buffer.{ByteBufUtil, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{FullHttpResponse, HttpResponse}

object NettyResponse {

  def apply(jRes: FullHttpResponse)(implicit unsafe: Unsafe): Response = {
    val status  = Conversions.statusFromNetty(jRes.status())
    val headers = Conversions.headersFromNetty(jRes.headers())
    val data    = NettyBody.fromByteBuf(jRes.content(), headers.headers.get(Header.ContentType.name))

    Response(status, headers, data)
  }

  def make(
    ctx: ChannelHandlerContext,
    jRes: HttpResponse,
    onComplete: Promise[Throwable, ChannelState],
    keepAlive: Boolean,
  )(implicit
    unsafe: Unsafe,
    trace: Trace,
  ): Response = {
    val status             = Conversions.statusFromNetty(jRes.status())
    val headers            = Conversions.headersFromNetty(jRes.headers())
    val knownContentLength = headers.get(Header.ContentLength).map(_.length)

    if (knownContentLength.contains(0L)) {
      onComplete.unsafe.done(Exit.succeed(ChannelState.forStatus(status)))
      Response(status, headers, Body.empty)
    } else {
      val responseHandler = new ClientResponseStreamHandler(onComplete, keepAlive, status)
      ctx
        .pipeline()
        .addAfter(
          Names.ClientInboundHandler,
          Names.ClientStreamingBodyHandler,
          responseHandler,
        ): Unit

      val data = NettyBody.fromAsync(callback => responseHandler.connect(callback), knownContentLength)
      Response(status, headers, data)
    }
  }
}
