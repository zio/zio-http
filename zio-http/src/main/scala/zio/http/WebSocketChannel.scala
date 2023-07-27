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

package zio.http

import zio._

import zio.http.ChannelEvent.{ExceptionCaught, Read, Registered, Unregistered, UserEventTriggered}
import zio.http.netty.NettyChannel

import io.netty.buffer.{ByteBufUtil, Unpooled}
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, _}

private[http] object WebSocketChannel {

  def make(
    nettyChannel: NettyChannel[JWebSocketFrame],
    queue: Queue[WebSocketChannelEvent],
  ): WebSocketChannel =
    new WebSocketChannel {
      def awaitShutdown: UIO[Unit]                                 =
        nettyChannel.awaitClose
      def receive: Task[WebSocketChannelEvent]                     =
        queue.take
      def send(in: WebSocketChannelEvent): Task[Unit]              =
        in match {
          case Read(message) => nettyChannel.writeAndFlush(frameToNetty(message))
          case _             => ZIO.unit
        }
      def sendAll(in: Iterable[WebSocketChannelEvent]): Task[Unit] =
        ZIO.suspendSucceed {
          val iterator = in.iterator.collect { case Read(message) => message }

          ZIO.whileLoop(iterator.hasNext) {
            val message = iterator.next()
            if (iterator.hasNext) nettyChannel.write(frameToNetty(message))
            else nettyChannel.writeAndFlush(frameToNetty(message))
          }(_ => ())
        }
      def shutdown: UIO[Unit]                                      =
        nettyChannel.close(false).orDie
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
