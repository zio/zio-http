package zio.http

import zio.{Task, Trace, UIO}

/**
 * An immutable and type-safe representation of one or more netty channels. `A`
 * represents the type of messages that can be written on the channel.
 */
// TODO Remove all netty-specific methods here and reduce footprint
trait ChannelForUserSocketApps[-A] {

  /*
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
//  @deprecated("TODO Confirm we can remove this", "1.0")
  def contramap[A1](f: A1 => A): ChannelForUserSocketApps[A1]

  /**
   * Flushes the pending write operations on the channel.
   */
  def flush(implicit trace: Trace): Task[Unit]

  /**
   * Returns the globally unique identifier of this channel.
   */
  @deprecated("TODO Confirm we can remove this", "1.0")
  def id(implicit trace: Trace): String

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
