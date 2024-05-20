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
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.internal.ChannelState
import zio.http.netty.{NettyBodyWriter, NettyResponse, NettyRuntime}
import zio.http.{Request, Response}

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._

/**
 * Handles HTTP response
 */
final class ClientInboundHandler(
  rtm: NettyRuntime,
  req: Request,
  jReq: HttpRequest,
  onResponse: Promise[Throwable, Response],
  onComplete: Promise[Throwable, ChannelState],
  enableKeepAlive: Boolean,
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[HttpObject](false) {
  implicit private val unsafeClass: Unsafe = Unsafe.unsafe

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    super.handlerAdded(ctx)
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    sendRequest(ctx)
  }

  override def handlerRemoved(ctx: ChannelHandlerContext): Unit = super.handlerRemoved(ctx)

  private def sendRequest(ctx: ChannelHandlerContext): Unit = {
    jReq match {
      case fullRequest: FullHttpRequest =>
        ctx.writeAndFlush(fullRequest)
      case _: HttpRequest               =>
        ctx.write(jReq)
        NettyBodyWriter.writeAndFlush(req.body, None, ctx, compressionEnabled = false).foreach { effect =>
          rtm.run(ctx, NettyRuntime.noopEnsuring)(effect)(Unsafe.unsafe, trace)
        }
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {
    msg match {
      case response: HttpResponse =>
        val keepAlive = enableKeepAlive && HttpUtil.isKeepAlive(response)
        val resp      = NettyResponse.make(ctx, response, onComplete, keepAlive)
        onResponse.unsafe.done(Exit.succeed(resp))
      case content: HttpContent   =>
        ctx.fireChannelRead(content): Unit

      case err => throw new IllegalStateException(s"Client unexpected message type: $err")
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    ctx.fireExceptionCaught(error)
  }
}
