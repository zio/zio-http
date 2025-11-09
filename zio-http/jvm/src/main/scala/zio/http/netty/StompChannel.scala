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

import zio.http.ChannelEvent.Read
import zio.http.{ChannelEvent, StompFrame => ZStompFrame}

import io.netty.handler.codec.stomp.{StompFrame => JStompFrame}

private[http] object StompChannel {

  def make(
    nettyChannel: NettyChannel[JStompFrame],
    queue: Queue[ChannelEvent[ZStompFrame]],
  ): zio.http.StompChannel =
    new zio.http.StompChannel {

      def receive(implicit trace: Trace): Task[ZStompFrame] =
        queue.take.flatMap {
          case Read(frame)                       => ZIO.succeed(frame)
          case ChannelEvent.ExceptionCaught(err) => ZIO.fail(err)
          case ChannelEvent.Unregistered         => ZIO.fail(new RuntimeException("Channel closed"))
          case _                                 => receive
        }

      def receiveAll[R](
        handler: ChannelEvent[ZStompFrame] => ZIO[R, Throwable, Any],
      )(implicit trace: Trace): ZIO[R, Throwable, Nothing] = {
        lazy val loop: ZIO[R, Throwable, Nothing] =
          queue.take.flatMap {
            case event: ChannelEvent.ExceptionCaught   =>
              handler(event) *> ZIO.fail(event.cause)
            case event: ChannelEvent.Unregistered.type =>
              handler(event) *> ZIO.fail(new RuntimeException("Channel unregistered"))
            case event                                 =>
              handler(event) *> ZIO.yieldNow *> loop
          }

        loop
      }

      def send(frame: ZStompFrame)(implicit trace: Trace): Task[Unit] =
        NettyStompFrameCodec.toNettyFrame(frame).flatMap { nettyFrame =>
          nettyChannel.writeAndFlush(nettyFrame)
        }

      def shutdown(implicit trace: Trace): Task[Unit] =
        nettyChannel.close(false)
    }
}
