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
import zio.{Task, Trace, UIO}

/**
 * An immutable and type-safe representation of one or more netty channels. `A`
 * represents the type of messages that can be written on the channel.
 */
// TODO Remove all netty-specific methods here and reduce footprint
trait Channel[-A] {

  /**
   * When set to `true` (default) it will automatically read messages from the
   * channel. When set to false, the channel will not read messages until `read`
   * is called.
   */
  def autoRead(flag: Boolean)(implicit trace: Trace): UIO[Unit]

  /**
   * Provides a way to wait for the channel to be closed.
   */
  def awaitClose(implicit trace: Trace): UIO[Unit]

  /**
   * Closes the channel. Pass true to await to wait for the channel to be
   * closed.
   */
  def close(await: Boolean = false)(implicit trace: Trace): Task[Unit]

  /**
   * Creates a new channel that can write a different type of message by using a
   * transformation function.
   */
  def contramap[A1](f: A1 => A): Channel[A1]

  /**
   * Flushes the pending write operations on the channel.
   */
  def flush(implicit trace: Trace): Task[Unit]

  /**
   * Returns the globally unique identifier of this channel.
   */
  def id(implicit trace: Trace): String

  /**
   * Returns `true` if auto-read is set to true.
   */
  def isAutoRead(implicit trace: Trace): UIO[Boolean]

  /**
   * Schedules a read operation on the channel. This is not necessary if
   * auto-read is enabled.
   */
  def read(implicit trace: Trace): UIO[Unit]

  /**
   * Schedules a write operation on the channel. The actual write only happens
   * after calling `flush`. Pass `true` to await the completion of the write
   * operation.
   */
  def write(msg: A, await: Boolean = false)(implicit trace: Trace): Task[Unit]

  /**
   * Writes and flushes the message on the channel. Pass `true` to await the
   * completion of the write operation.
   */
  def writeAndFlush(msg: A, await: Boolean = false)(implicit trace: Trace): Task[Unit]
}
