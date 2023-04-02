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

import zio.http.netty._
import zio.http.netty.model.Conversions
import zio.http.{Request, Response}

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._

final class ClientInboundStreamingHandler(
  val rtm: NettyRuntime,
  req: Request,
  onResponse: Promise[Throwable, Response],
  onComplete: Promise[Throwable, ChannelState],
  enableKeepAlive: Boolean,
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[HttpObject](false) {

  private implicit val unsafeClass: Unsafe = Unsafe.unsafe

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    writeRequest(req, ctx): Unit
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {
    msg match {
      case response: HttpResponse =>
        ctx.channel().config().setAutoRead(false)
        rtm.runUninterruptible(ctx, NettyRuntime.noopEnsuring) {
          onResponse
            .succeed(
              NettyResponse.make(
                ctx,
                response,
                rtm,
                onComplete,
                enableKeepAlive && HttpUtil.isKeepAlive(response),
              ),
            )
        }(unsafeClass, trace)
      case content: HttpContent   =>
        ctx.fireChannelRead(content): Unit

      case err => throw new IllegalStateException(s"Client unexpected message type: ${err}")
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    rtm.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(
      onResponse.fail(error) *> onComplete.fail(error),
    )(unsafeClass, trace)
  }

  private def encodeRequest(req: Request): HttpRequest = {
    val method   = Conversions.methodToNetty(req.method)
    val jVersion = Versions.convertToZIOToNetty(req.version)

    // As per the spec, the path should contain only the relative path.
    // Host and port information should be in the headers.
    val path = req.url.relative.encode

    val encodedReqHeaders = Conversions.headersToNetty(req.headers)

    val headers = req.url.hostPort match {
      case Some(value) => encodedReqHeaders.set(HttpHeaderNames.HOST, value)
      case None        => encodedReqHeaders
    }

    val h = headers
      .add(HttpHeaderNames.TRANSFER_ENCODING, "chunked")
      .add(HttpHeaderNames.USER_AGENT, "zhttp-client")

    new DefaultHttpRequest(jVersion, method, path, h)

  }

  private def writeRequest(msg: Request, ctx: ChannelHandlerContext): Unit = {
    ctx.write(encodeRequest(msg))
    rtm.run(ctx, NettyRuntime.noopEnsuring) {
      NettyBodyWriter.write(msg.body, ctx).unit
    }(Unsafe.unsafe, trace)
    ctx.flush(): Unit
  }
}
