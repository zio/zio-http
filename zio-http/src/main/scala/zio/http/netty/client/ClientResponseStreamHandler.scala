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

// import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.Promise

import zio.http.Status
import zio.http.netty.{AsyncBodyReader, NettyFutureExecutor, NettyRuntime}

import io.netty.channel._
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
final class ClientResponseStreamHandler(
  rtm: NettyRuntime,
  onComplete: Promise[Throwable, ChannelState],
  keepAlive: Boolean,
  status: Status,
)(implicit trace: zio.http.Trace)
    extends AsyncBodyReader { self =>

  override def channelRead0(
    ctx: ChannelHandlerContext,
    msg: HttpContent,
  ): Unit = {
    val isLast = msg.isInstanceOf[LastHttpContent]
    super.channelRead0(ctx, msg)

    if (isLast) {
      if (keepAlive)
        rtm.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(onComplete.succeed(ChannelState.forStatus(status)))(
          unsafeClass,
          trace,
        )
      else {
        rtm.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(
          NettyFutureExecutor
            .executed(ctx.close())
            .as(ChannelState.Invalid)
            .exit
            .flatMap(onComplete.done(_)),
        )(unsafeClass, trace)
      }
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    rtm.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(onComplete.fail(cause))(unsafeClass, trace)
  }
}
