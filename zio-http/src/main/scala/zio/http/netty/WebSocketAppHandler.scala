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

import zio.http.ChannelEvent.UserEvent
import zio.http.socket.{SocketApp, WebSocketFrame}
import zio.http.{Channel, ChannelEvent}

import io.netty.buffer.{ByteBufUtil, Unpooled}
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
  app: SocketApp[Any],
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
          f(event.map(frameFromNetty).contramap[WebSocketFrame](frameToNetty)),
        )
      case None    => ()
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: JWebSocketFrame): Unit =
    dispatch(ctx, ChannelEvent.channelRead(channelOf(ctx), msg))

  override def channelRegistered(ctx: ChannelHandlerContext): Unit =
    dispatch(ctx, ChannelEvent.channelRegistered(channelOf(ctx)))

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit =
    dispatch(ctx, ChannelEvent.channelUnregistered(channelOf(ctx)))

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    dispatch(ctx, ChannelEvent.exceptionCaught(channelOf(ctx), cause))

  override def userEventTriggered(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    msg match {
      case _: WebSocketServerProtocolHandler.HandshakeComplete | ClientHandshakeStateEvent.HANDSHAKE_COMPLETE =>
        dispatch(ctx, ChannelEvent.userEventTriggered(channelOf(ctx), UserEvent.HandshakeComplete))
      case ServerHandshakeStateEvent.HANDSHAKE_TIMEOUT | ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT          =>
        dispatch(ctx, ChannelEvent.userEventTriggered(channelOf(ctx), UserEvent.HandshakeTimeout))
      case _ => super.userEventTriggered(ctx, msg)
    }
  }

  private def channelOf(ctx: ChannelHandlerContext): Channel[JWebSocketFrame] =
    NettyChannel.make(ctx.channel())

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

  private def frameToNetty(frame: WebSocketFrame): JWebSocketFrame =
    frame match {
      case b: WebSocketFrame.Binary                 =>
        new BinaryWebSocketFrame(b.isFinal, 0, Unpooled.wrappedBuffer(b.bytes.toArray))
      case t: WebSocketFrame.Text                   =>
        new TextWebSocketFrame(t.isFinal, 0, t.text)
      case WebSocketFrame.Close(status, Some(text)) =>
        new CloseWebSocketFrame(status, text)
      case WebSocketFrame.Close(status, None)       =>
        new CloseWebSocketFrame(status, null)
      case WebSocketFrame.Ping                      =>
        new PingWebSocketFrame()
      case WebSocketFrame.Pong                      =>
        new PongWebSocketFrame()
      case c: WebSocketFrame.Continuation           =>
        new ContinuationWebSocketFrame(c.isFinal, 0, Unpooled.wrappedBuffer(c.buffer.toArray))
    }
}
