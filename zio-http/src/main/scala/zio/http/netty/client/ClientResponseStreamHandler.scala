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
import zio.{Chunk, Promise, Trace, Unsafe}

import zio.http.netty.NettyBody.UnsafeAsync
import zio.http.netty.{NettyFutureExecutor, NettyRuntime}

import io.netty.buffer.ByteBufUtil
import io.netty.channel._
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}

final class ClientResponseStreamHandler(
  val callback: UnsafeAsync,
  zExec: NettyRuntime,
  onComplete: Promise[Throwable, ChannelState],
  keepAlive: Boolean,
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[HttpContent](false) { self =>

  private val unsafeClass: Unsafe = Unsafe.unsafe

  override def channelRead0(
    ctx: ChannelHandlerContext,
    msg: HttpContent,
  ): Unit = {
    val isLast = msg.isInstanceOf[LastHttpContent]
    val chunk  = Chunk.fromArray(ByteBufUtil.getBytes(msg.content()))
    callback(ctx.channel(), chunk, isLast)
    if (isLast) {
      ctx.channel().pipeline().remove(self)

      if (keepAlive)
        zExec.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(onComplete.succeed(ChannelState.Reusable))(
          unsafeClass,
          trace,
        )
      else {
        zExec.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(
          NettyFutureExecutor
            .executed(ctx.close())
            .as(ChannelState.Invalid)
            .exit
            .flatMap(onComplete.done(_)),
        )(unsafeClass, trace)
      }
    }: Unit
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.read(): Unit
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    zExec.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(onComplete.fail(cause))(unsafeClass, trace)
  }
}
