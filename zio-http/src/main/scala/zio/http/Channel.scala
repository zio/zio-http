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

/**
  * A `Channel` is an asynchronous communication channel that supports
  * receiving messages of type `In` and sending messages of type `Out`.
  */
trait Channel[-In, +Out] { self =>

  /**
    * Await shutdown of the channel.
    */
  def awaitShutdown: UIO[Unit]

  /**
    * Read a message from the channel, suspending until the next message is
    * available.
    */
  def receive: Task[Out]

  /**
    * Send a message to the channel.
    */
  def send(in: In): Task[Unit]

  /**
    * Shut down the channel.
    */
  def shutdown: UIO[Unit]

  /**
    * Constructs a new channel that automatically transforms messages sent to
    * this channel using the specified function.
    */
  final def contramap[In2](f: In2 => In): Channel[In2, Out] =
    new Channel[In2, Out] {
      def awaitShutdown: UIO[Unit] =
        self.awaitShutdown
      def receive: Task[Out] =
        self.receive
      def send(in: In2): Task[Unit] =
        self.send(f(in))
      def shutdown: UIO[Unit] =
        self.shutdown
    }

  /**
   * Constructs a new channel that automatically transforms messages received
   * from this channel using the specified function.
   */
  final def map[Out2](f: Out => Out2): Channel[In, Out2] =
    new Channel[In, Out2] {
      def awaitShutdown: UIO[Unit] =
        self.awaitShutdown
      def receive: Task[Out2] =
        self.receive.map(f)
      def send(in: In): Task[Unit] =
        self.send(in)
      def shutdown: UIO[Unit] =
        self.shutdown
    }
}
