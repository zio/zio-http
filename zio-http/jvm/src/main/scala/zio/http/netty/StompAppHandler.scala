/*
 * Copyright 2021 - 2025 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
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
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.{ChannelEvent, StompFrame => ZStompFrame}

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.stomp.{StompFrame => JStompFrame}

/**
 * Netty handler that processes incoming STOMP frames and dispatches them to a
 * ZIO queue.
 */
private[netty] final class StompAppHandler(
  zExec: NettyRuntime,
  queue: Queue[ChannelEvent[ZStompFrame]],
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[JStompFrame](true) { // autoRelease = true

  implicit private val unsafeClass: Unsafe = Unsafe.unsafe

  private def dispatch(event: ChannelEvent[JStompFrame]): Unit = {
    // IMPORTANT: Offering to the queue must be run synchronously to avoid messages being added in the wrong order
    // Since the queue is unbounded, this will not block the event loop
    val _ = zExec.unsafeRunSync(queue.offer(event.map(frameFromNetty)))
  }

  private def frameFromNetty(jFrame: JStompFrame): ZStompFrame =
    try {
      NettyStompFrameCodec.fromNettyFrame(jFrame)
    } catch {
      case err: Throwable =>
        // Conversion errors will be caught and dispatched as ExceptionCaught events
        throw err
    }

  override def channelRead0(ctx: ChannelHandlerContext, msg: JStompFrame): Unit =
    dispatch(ChannelEvent.read(msg))

  override def channelRegistered(ctx: ChannelHandlerContext): Unit = {
    dispatch(ChannelEvent.registered)
    super.channelRegistered(ctx)
  }

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit = {
    dispatch(ChannelEvent.unregistered)
    super.channelUnregistered(ctx)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    dispatch(ChannelEvent.exceptionCaught(cause))
    val _ = ctx.close()
    ()
  }
}
