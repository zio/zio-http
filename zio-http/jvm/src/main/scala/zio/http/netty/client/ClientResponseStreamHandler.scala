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

import zio.http.Status
import zio.http.internal.ChannelState
import zio.http.netty.AsyncBodyReader

import io.netty.channel._
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}

/**
 * Handles streaming HTTP response bodies and manages connection lifecycle.
 *
 * This handler extends AsyncBodyReader to provide body reading with timeout
 * support, while also ensuring proper connection pool management through the
 * onComplete promise.
 *
 * Connection Lifecycle Management:
 *   - onLastMessage(): Called when body completes successfully
 *     - keepAlive=true: Mark connection as reusable (ChannelState.forStatus)
 *     - keepAlive=false: Mark connection as invalid
 *   - channelInactive(): Called when channel closes prematurely
 *     - Always marks connection as Invalid to remove from pool
 *     - Allows parent AsyncBodyReader to fail the body callback
 *     - Ensures connection cleanup even on timeout/error
 *   - exceptionCaught(): Called on any exception during body reading
 *     - Marks connection as Invalid via Exit.fail
 *     - Ensures connection removed from pool on errors
 *
 * This ensures proper coordination with ZClient's connection pool:
 *   1. Body reads successfully → connection returned to pool (if keep-alive)
 *   2. Body read times out → connection invalidated and removed
 *   3. Channel closes early → connection invalidated and removed
 *   4. Exception occurs → connection invalidated and removed
 *
 * The onComplete promise is always fulfilled, preventing connection leaks.
 */
private[netty] final class ClientResponseStreamHandler(
  onComplete: Promise[Throwable, ChannelState],
  keepAlive: Boolean,
  status: Status,
  timeoutMillis: Option[Long],
)(implicit trace: Trace)
    extends AsyncBodyReader(timeoutMillis) { self =>

  private implicit val unsafe: Unsafe = Unsafe.unsafe

  override def onLastMessage(): Unit =
    if (keepAlive)
      onComplete.unsafe.done(Exit.succeed(ChannelState.forStatus(status)))
    else
      onComplete.unsafe.done(Exit.succeed(ChannelState.Invalid))

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpContent): Unit = {
    val isLast = msg.isInstanceOf[LastHttpContent]
    super.channelRead0(ctx, msg)
    if (isLast && !keepAlive) ctx.close(): Unit
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    onComplete.unsafe.done(Exit.fail(cause))
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    // Channel closed before body reading completed
    // Mark connection as invalid to ensure it's removed from pool
    // The parent AsyncBodyReader will handle failing the body callback
    onComplete.unsafe.done(Exit.succeed(ChannelState.Invalid))
    super.channelInactive(ctx)
  }
}
