package zhttp.service

import io.netty.channel.{Channel => JChannel, ChannelFuture => JChannelFuture}
import zio.Task

/**
 * An immutable and type-safe representation of one or more netty channels. `A`
 * represents the type of messages that can be written on the channel.
 */
final case class Channel[-A](
  private val channel: JChannel,
  private val convert: A => Any,
) {
  self =>

  private def foreach[S](await: Boolean)(run: JChannel => JChannelFuture): Task[Unit] = {
    if (await) ChannelFuture.unit(run(channel))
    else Task(run(channel): Unit)
  }

  def close(await: Boolean = false): Task[Unit] = foreach(await) { _.close() }

  def contramap[A1](f: A1 => A): Channel[A1] = copy(convert = convert.compose(f))

  def flush: Task[Unit] = Task(channel.flush(): Unit)

  def id: String = channel.id().asLongText()

  def write(msg: A, await: Boolean = false): Task[Unit] = foreach(await) { _.write(convert(msg)) }

  def writeAndFlush(msg: A, await: Boolean = false): Task[Unit] = foreach(await) { _.writeAndFlush(convert(msg)) }
}

object Channel {
  def make[A](channel: JChannel): Channel[A] = Channel(channel, identity)
}
