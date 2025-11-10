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
 * Represents a STOMP channel for bidirectional communication.
 *
 * Similar to WebSocketChannel but for STOMP protocol frames.
 */
trait StompChannel {

  /**
   * Sends a STOMP frame to the remote peer
   */
  def send(frame: StompFrame)(implicit trace: Trace): Task[Unit]

  /**
   * Receives STOMP frames from the remote peer
   */
  def receive(implicit trace: Trace): Task[StompFrame]

  /**
   * Receives all frames and processes them with the given handler
   */
  def receiveAll[R](
    handler: ChannelEvent[StompFrame] => ZIO[R, Throwable, Any],
  )(implicit trace: Trace): ZIO[R, Throwable, Nothing]

  /**
   * Gracefully shuts down the STOMP channel
   */
  def shutdown(implicit trace: Trace): Task[Unit]
}

object StompChannel {
  // Platform-specific implementation will be provided in JVM sources
}
