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

import zio.http.internal.ChannelState
import zio.http.netty.{NettyBodyWriter, NettyResponse, NettyRuntime}
import zio.http.{Request, Response}

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.http._
import io.netty.util.ReferenceCountUtil

/**
 * Handles HTTP response
 */
private[netty] final class ClientInboundHandler(
  rtm: NettyRuntime,
  req: Request,
  jReq: HttpRequest,
  onResponse: Promise[Throwable, Response],
  onComplete: Promise[Throwable, ChannelState],
  enableKeepAlive: Boolean,
  bodyReadTimeoutMillis: Option[Long] = None,
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[HttpObject](false) {
  implicit private val unsafeClass: Unsafe = Unsafe.unsafe

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit =
    evt match {
      case ClientInboundHandler.SendRequest => sendRequest(ctx)
      case _                                => ctx.fireUserEventTriggered(evt): Unit
    }

  private def sendRequest(ctx: ChannelHandlerContext): Unit = {
    jReq match {
      case fullRequest: FullHttpRequest =>
        ctx.writeAndFlush(fullRequest): Unit
      case request: HttpRequest         =>
        ctx.write(jReq)
        // Extract content length from headers (set by NettyRequestEncoder)
        val contentLength = request.headers().get(HttpHeaderNames.CONTENT_LENGTH) match {
          case null  => None
          case value => Some(value.toLong)
        }
        NettyBodyWriter.writeAndFlush(req.body, contentLength, ctx).foreach { effect =>
          rtm.run(ctx, NettyRuntime.noopEnsuring, preferOnCurrentThread = true)(effect)(Unsafe.unsafe, trace)
        }
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {
    msg match {
      case response: HttpResponse =>
        // Netty reports maxHeaderSize / maxInitialLineLength violations as a
        // message with a failed DecoderResult (BAD_MESSAGE) rather than by
        // throwing through the pipeline. Failing the promises here prevents
        // the hang from #4375; closing poisons the connection so the pool
        // does not reuse it. HttpResponse headers are not refcounted.
        if (response.decoderResult().isFailure) {
          val exit = Exit.fail(decoderFailure(response))
          onResponse.unsafe.done(exit)
          onComplete.unsafe.done(exit)
          ctx.close(): Unit
        } else {
          val keepAlive = enableKeepAlive && HttpUtil.isKeepAlive(response)
          val resp      = NettyResponse.make(ctx, response, onComplete, keepAlive, bodyReadTimeoutMillis, req.method)
          onResponse.unsafe.done(Exit.succeed(resp))
        }
      case content: HttpContent   =>
        // Covers maxChunkSize / trailer decode failures on the same path.
        // The message is consumed here (never forwarded), so release it to
        // avoid leaking the ByteBuf, then propagate the failure downstream
        // so streaming body readers fail fast instead of hanging.
        if (content.decoderResult().isFailure) {
          val failure = decoderFailure(content)
          onComplete.unsafe.done(Exit.fail(failure))
          ReferenceCountUtil.release(content)
          ctx.fireExceptionCaught(failure): Unit
          ctx.close(): Unit
        } else {
          ctx.fireChannelRead(content): Unit
        }

      case err => throw new IllegalStateException(s"Client unexpected message type: $err")
    }
  }

  private def decoderFailure(msg: HttpObject): Throwable = {
    val cause = msg.decoderResult().cause()
    if (cause ne null) cause else new DecoderException("HTTP decoding failed")
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    ctx.fireExceptionCaught(error)
    ()
  }
}

object ClientInboundHandler {
  case object SendRequest
}
