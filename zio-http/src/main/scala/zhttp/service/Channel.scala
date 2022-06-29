package zhttp.service

import io.netty.channel.{Channel => JChannel, ChannelFuture => JChannelFuture}
import zio.Task

/**
 * An immutable and type-safe representation of one or more netty channels. `A`
 * represents the type of messages that can be written on the channel.
 */
final case class Channel[-A](
  private val channels: Vector[JChannel],
  private val convert: A => Any,
) {
  self =>

  private def foreach[S](await: Boolean)(run: JChannel => JChannelFuture): Task[Unit] = {
    if (await) {
      channels.foldLeft(Task {}) { (task, channel) => task <* ChannelFuture.unit(run(channel)) }
    } else Task(for (i <- channels) run(i))
  }

  def ++[A1 <: A](other: Channel[A1]): Channel[A1] = self combine other

  def close(await: Boolean = false): Task[Unit] = foreach(await) { _.close() }

  def combine[A1 <: A](other: Channel[A1]): Channel[A1] = self.copy(channels = channels ++ other.channels)

  def contramap[A1](f: A1 => A): Channel[A1] = copy(convert = convert.compose(f))

  def flush: Task[Unit] = Task(channels.foreach { _.flush() })

  def id: String = channels.map(_.id.asLongText()).mkString(",")

  def write(msg: A, await: Boolean = false): Task[Unit] = foreach(await) { _.write(convert(msg)) }

  def writeAndFlush(msg: A, await: Boolean = false): Task[Unit] = foreach(await) { _.writeAndFlush(convert(msg)) }
}

object Channel {
  def empty[A]: Channel[A]                   = Channel(Vector.empty, identity)
  def make[A](channel: JChannel): Channel[A] = Channel(Vector(channel), identity)
}
