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

/**
 * A `Channel` is an asynchronous communication channel that supports receiving
 * messages of type `In` and sending messages of type `Out`.
 */
trait Channel[-In, +Out] { self =>

  /**
   * Await shutdown of the channel.
   */
  def awaitShutdown(implicit trace: Trace): UIO[Unit]

  /**
   * Read a message from the channel, suspending until the next message is
   * available.
   */
  def receive(implicit trace: Trace): Task[Out]

  /**
   * Reads all messages from the channel, handling them with the specified
   * function.
   */
  def receiveAll[Env, Err](f: Out => ZIO[Env, Err, Any])(implicit trace: Trace): ZIO[Env, Err, Unit]

  /**
   * Send a message to the channel.
   */
  def send(in: In)(implicit trace: Trace): Task[Unit]

  /**
   * Send all messages to the channel.
   */
  def sendAll(in: Iterable[In])(implicit trace: Trace): Task[Unit]

  /**
   * Shut down the channel.
   */
  def shutdown(implicit trace: Trace): UIO[Unit]

  /**
   * Constructs a new channel that automatically transforms messages sent to
   * this channel using the specified function.
   */
  final def contramap[In2](f: In2 => In): Channel[In2, Out] =
    new Channel[In2, Out] {
      def awaitShutdown(implicit trace: Trace): UIO[Unit]                                                =
        self.awaitShutdown
      def receive(implicit trace: Trace): Task[Out]                                                      =
        self.receive
      def receiveAll[Env, Err](g: Out => ZIO[Env, Err, Any])(implicit trace: Trace): ZIO[Env, Err, Unit] =
        self.receiveAll(g)
      def send(in: In2)(implicit trace: Trace): Task[Unit]                                               =
        self.send(f(in))
      def sendAll(in: Iterable[In2])(implicit trace: Trace): Task[Unit]                                  =
        self.sendAll(in.map(f))
      def shutdown(implicit trace: Trace): UIO[Unit]                                                     =
        self.shutdown
    }

  /**
   * Constructs a new channel that automatically transforms messages received
   * from this channel using the specified function.
   */
  final def map[Out2](f: Out => Out2)(implicit trace: Trace): Channel[In, Out2] =
    new Channel[In, Out2] {
      def awaitShutdown(implicit trace: Trace): UIO[Unit]                                                 =
        self.awaitShutdown
      def receive(implicit trace: Trace): Task[Out2]                                                      =
        self.receive.map(f)
      def receiveAll[Env, Err](g: Out2 => ZIO[Env, Err, Any])(implicit trace: Trace): ZIO[Env, Err, Unit] =
        self.receiveAll(f andThen g)
      def send(in: In)(implicit trace: Trace): Task[Unit]                                                 =
        self.send(in)
      def sendAll(in: Iterable[In])(implicit trace: Trace): Task[Unit]                                    =
        self.sendAll(in)
      def shutdown(implicit trace: Trace): UIO[Unit]                                                      =
        self.shutdown
    }
}
