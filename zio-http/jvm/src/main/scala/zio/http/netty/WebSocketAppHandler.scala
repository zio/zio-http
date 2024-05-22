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

import zio.http.ChannelEvent.UserEvent
import zio.http.internal.ChannelState
import zio.http.{ChannelEvent, WebSocketChannelEvent, WebSocketFrame}

import io.netty.buffer.ByteBufUtil
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.ServerHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, _}

/**
 * A generic SocketApp handler that can be used on both - the client and the
 * server.
 */
private[zio] final class WebSocketAppHandler(
  zExec: NettyRuntime,
  queue: Queue[WebSocketChannelEvent],
  onComplete: Option[Promise[Throwable, ChannelState]],
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[JWebSocketFrame] {

  implicit private val unsafeClass: Unsafe = Unsafe.unsafe

  private def dispatch(
    ctx: ChannelHandlerContext,
    event: ChannelEvent[JWebSocketFrame],
    close: Boolean = false,
  ): Unit = {
    // IMPORTANT: Offering to the queue must be run synchronously to avoid messages being added in the wrong order
    // Since the queue is unbounded, this will not block the event loop
    // TODO: We need to come up with a design that doesn't involve running an effect to offer to the queue
    zExec.unsafeRunSync(queue.offer(event.map(frameFromNetty)))
    onComplete match {
      case Some(promise) if close => promise.unsafe.done(Exit.succeed(ChannelState.Invalid))
      case _                      => ()
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: JWebSocketFrame): Unit =
    dispatch(ctx, ChannelEvent.read(msg))

  override def channelRegistered(ctx: ChannelHandlerContext): Unit =
    dispatch(ctx, ChannelEvent.registered)

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit =
    dispatch(ctx, ChannelEvent.unregistered, close = true)

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    dispatch(ctx, ChannelEvent.exceptionCaught(cause))
    onComplete match {
      case Some(promise) => promise.unsafe.done(Exit.fail(cause))
      case None          => ()
    }
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    msg match {
      case _: WebSocketServerProtocolHandler.HandshakeComplete | ClientHandshakeStateEvent.HANDSHAKE_COMPLETE =>
        dispatch(ctx, ChannelEvent.userEventTriggered(UserEvent.HandshakeComplete))
      case ServerHandshakeStateEvent.HANDSHAKE_TIMEOUT | ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT          =>
        dispatch(ctx, ChannelEvent.userEventTriggered(UserEvent.HandshakeTimeout))
      case _ => super.userEventTriggered(ctx, msg)
    }
  }

  private def frameFromNetty(jFrame: JWebSocketFrame): WebSocketFrame =
    jFrame match {
      case _: PingWebSocketFrame         => WebSocketFrame.Ping
      case _: PongWebSocketFrame         => WebSocketFrame.Pong
      case m: BinaryWebSocketFrame       =>
        WebSocketFrame.Binary(Chunk.fromArray(ByteBufUtil.getBytes(m.content())), m.isFinalFragment)
      case m: TextWebSocketFrame         => WebSocketFrame.Text(m.text(), m.isFinalFragment)
      case m: CloseWebSocketFrame        => WebSocketFrame.Close(m.statusCode(), Option(m.reasonText()))
      case m: ContinuationWebSocketFrame =>
        WebSocketFrame.Continuation(Chunk.fromArray(ByteBufUtil.getBytes(m.content())), m.isFinalFragment)
      case _                             => null
    }
}
