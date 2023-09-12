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
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.ChannelEvent.Read
import zio.http.netty.NettyChannel

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, _}

private[http] object WebSocketChannel {

  def make(
    nettyChannel: NettyChannel[JWebSocketFrame],
    queue: Queue[WebSocketChannelEvent],
  ): WebSocketChannel =
    new WebSocketChannel {
      def awaitShutdown(implicit trace: Trace): UIO[Unit] =
        nettyChannel.awaitClose

      def receive(implicit trace: Trace): Task[WebSocketChannelEvent] =
        queue.take

      def receiveAll[Env, Err](f: WebSocketChannelEvent => ZIO[Env, Err, Any])(implicit
        trace: Trace,
      ): ZIO[Env, Err, Unit] = {
        lazy val loop: ZIO[Env, Err, Unit] =
          queue.take.flatMap {
            case event @ ChannelEvent.ExceptionCaught(_) => f(event).unit
            case event @ ChannelEvent.Unregistered       => f(event).unit
            case event                                   => f(event) *> ZIO.yieldNow *> loop
          }

        loop
      }

      def send(in: WebSocketChannelEvent)(implicit trace: Trace): Task[Unit] = {
        in match {
          case Read(message) => nettyChannel.writeAndFlush(frameToNetty(message))
          case _             => ZIO.unit
        }
      }

      def sendAll(in: Iterable[WebSocketChannelEvent])(implicit trace: Trace): Task[Unit] =
        ZIO.suspendSucceed {
          val iterator = in.iterator.collect { case Read(message) => message }

          ZIO.whileLoop(iterator.hasNext) {
            val message = iterator.next()
            if (iterator.hasNext) nettyChannel.write(frameToNetty(message))
            else nettyChannel.writeAndFlush(frameToNetty(message))
          }(_ => ())
        }
      def shutdown(implicit trace: Trace): UIO[Unit]                                      =
        nettyChannel.close(false).orDie
    }

  private def frameToNetty(frame: WebSocketFrame): JWebSocketFrame = {
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
}
