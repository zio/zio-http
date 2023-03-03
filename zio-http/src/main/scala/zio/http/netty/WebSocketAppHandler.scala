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

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.ServerHandshakeStateEvent
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, WebSocketServerProtocolHandler}
import zio._
import zio.http.ChannelEvent
import zio.http.ChannelEvent.UserEvent
import zio.http.socket.{SocketApp, WebSocketFrame}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * A generic SocketApp handler that can be used on both - the client and the
 * server.
 */
private[zio] final class WebSocketAppHandler(
  zExec: NettyRuntime,
  app: SocketApp[Any],
  isClient: Boolean,
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[JWebSocketFrame] {

  implicit private val unsafeClass: Unsafe = Unsafe.unsafe

  private def dispatch(
    ctx: ChannelHandlerContext,
    event: ChannelEvent[JWebSocketFrame, JWebSocketFrame],
  ): Unit = {
    app.message match {
      case Some(f) =>
        zExec.runUninterruptible(ctx, NettyRuntime.noopEnsuring)(
          f(event.map(WebSocketFrame.unsafe.fromJFrame).contramap[WebSocketFrame](_.toWebSocketFrame)),
        )
      case None    => ()
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: JWebSocketFrame): Unit =
    dispatch(ctx, ChannelEvent.channelRead(ctx, msg))

  override def channelRegistered(ctx: ChannelHandlerContext): Unit =
    dispatch(ctx, ChannelEvent.channelRegistered(ctx))

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit =
    dispatch(ctx, ChannelEvent.channelUnregistered(ctx))

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    dispatch(ctx, ChannelEvent.exceptionCaught(ctx, cause))

  override def userEventTriggered(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    msg match {
      case _: WebSocketServerProtocolHandler.HandshakeComplete | ClientHandshakeStateEvent.HANDSHAKE_COMPLETE =>
        dispatch(ctx, ChannelEvent.userEventTriggered(ctx, UserEvent.HandshakeComplete))
      case ServerHandshakeStateEvent.HANDSHAKE_TIMEOUT | ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT          =>
        dispatch(ctx, ChannelEvent.userEventTriggered(ctx, UserEvent.HandshakeTimeout))
      case _ => super.userEventTriggered(ctx, msg)
    }
  }
}
