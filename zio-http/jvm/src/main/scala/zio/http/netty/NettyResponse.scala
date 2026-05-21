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

import zio._

import zio.http.internal.ChannelState
import zio.http.netty.client.ClientResponseStreamHandler
import zio.http.netty.model.Conversions
import zio.http.{Body, Header, Method, Response}

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{FullHttpResponse, HttpResponse}

private[netty] object NettyResponse {

  private def statusMustNotHaveBody(status: zio.http.Status): Boolean =
    status.isInformational || status.code == 204 || status.code == 304

  def apply(jRes: FullHttpResponse)(implicit unsafe: Unsafe): Response = {
    val status  = Conversions.statusFromNetty(jRes.status())
    val headers = Conversions.headersFromNetty(jRes.headers())
    val data    = NettyBody.fromByteBuf(jRes.content(), headers.get(Header.ContentType))

    Response(status, headers, data)
  }

  def make(
    ctx: ChannelHandlerContext,
    jRes: HttpResponse,
    onComplete: Promise[Throwable, ChannelState],
    keepAlive: Boolean,
    bodyReadTimeoutMillis: Option[Long] = None,
    requestMethod: Method = Method.GET,
  )(implicit
    unsafe: Unsafe,
    trace: Trace,
  ): Response = {
    val status             = Conversions.statusFromNetty(jRes.status())
    val headers            = Conversions.headersFromNetty(jRes.headers())
    val knownContentLength = headers.get(Header.ContentLength).map(_.length)

    // HEAD responses never carry a message body (RFC 9110 §9.3.2), even if the server
    // advertises a Content-Length matching the GET-equivalent payload. Attaching the
    // streaming body reader here would leave it waiting for bytes that never arrive.
    if (knownContentLength.contains(0L) || statusMustNotHaveBody(status) || requestMethod == Method.HEAD) {
      onComplete.unsafe.done(Exit.succeed(ChannelState.forStatus(status)))
      Response(status, headers, Body.empty)
    } else {
      val contentType     = headers.get(Header.ContentType)
      val responseHandler = new ClientResponseStreamHandler(onComplete, keepAlive, status, bodyReadTimeoutMillis)
      ctx
        .pipeline()
        .addAfter(
          Names.ClientInboundHandler,
          Names.ClientStreamingBodyHandler,
          responseHandler,
        ): Unit

      val data = NettyBody.fromAsync(
        callback => responseHandler.connect(callback),
        knownContentLength,
        contentType,
        () => ctx.read(): Unit,
      )
      Response(status, headers, data)
    }
  }
}
