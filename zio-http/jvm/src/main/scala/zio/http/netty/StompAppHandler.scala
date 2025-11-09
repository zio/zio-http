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
    // Convert Netty frame to zio-http frame
    val stompEvent: ChannelEvent[ZStompFrame] = event match {
      case ChannelEvent.Read(nettyFrame)      =>
        // Convert synchronously to avoid losing frames
        try {
          val frame = zExec.unsafeRunSync(NettyStompFrameCodec.fromNettyFrame(nettyFrame))
          ChannelEvent.Read(frame)
        } catch {
          case err: Throwable => ChannelEvent.ExceptionCaught(err)
        }
      case ChannelEvent.Registered            => ChannelEvent.Registered
      case ChannelEvent.Unregistered          => ChannelEvent.Unregistered
      case ChannelEvent.ExceptionCaught(err)  => ChannelEvent.ExceptionCaught(err)
      case ChannelEvent.UserEventTriggered(e) => ChannelEvent.UserEventTriggered(e)
    }

    // Offer to queue synchronously (queue is unbounded, won't block)
    val _ = zExec.unsafeRunSync(queue.offer(stompEvent))
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
