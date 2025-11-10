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

package zio.http

import zio._

/**
 * Represents a STOMP application that handles STOMP frames.
 *
 * Similar to WebSocketApp but for STOMP protocol.
 *
 * Example usage:
 * {{{
 * val app = StompApp(
 *   Handler.webSocket { channel =>
 *     channel.send(StompFrame.Connect()) *>
 *     channel.receiveAll {
 *       case ChannelEvent.Read(frame) => handleFrame(frame)
 *       case _ => ZIO.unit
 *     }
 *   }
 * )
 * }}}
 */
final case class StompApp[-R](
  handler: Handler[R, Throwable, StompChannel, Any],
) { self =>

  def provideEnvironment(r: ZEnvironment[R])(implicit trace: Trace): StompApp[Any] =
    StompApp(handler.provideEnvironment(r))

  def provideLayer[R0](layer: ZLayer[R0, Throwable, R])(implicit
    trace: Trace,
  ): StompApp[R0] =
    StompApp(handler.provideLayer(layer))

  def provideSomeEnvironment[R1](f: ZEnvironment[R1] => ZEnvironment[R])(implicit
    trace: Trace,
  ): StompApp[R1] =
    StompApp(handler.provideSomeEnvironment(f))

  def provideSomeLayer[R0, R1: Tag](
    layer: ZLayer[R0, Throwable, R1],
  )(implicit ev: R0 with R1 <:< R, trace: Trace): StompApp[R0] =
    StompApp(handler.provideSomeLayer(layer))

  def tapErrorCauseZIO[R1 <: R](
    f: Cause[Throwable] => ZIO[R1, Throwable, Any],
  )(implicit trace: Trace): StompApp[R1] =
    StompApp(handler.tapErrorCauseZIO(f))

  /**
   * Returns a Handler that effectfully peeks at the failure of this StompApp.
   */
  def tapErrorZIO[R1 <: R](
    f: Throwable => ZIO[R1, Throwable, Any],
  )(implicit trace: Trace): StompApp[R1] =
    self.tapErrorCauseZIO(cause => cause.failureOption.fold[ZIO[R1, Throwable, Any]](ZIO.unit)(f))
}

object StompApp {
  def apply[R](handler: Handler[R, Throwable, StompChannel, Any]): StompApp[R] =
    StompApp(handler)

  val unit: StompApp[Any] = StompApp(Handler.unit)
}
