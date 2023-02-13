package zio.http

import io.netty.channel.{Channel => JChannel, ChannelFuture => JChannelFuture}
import zio.http.netty.NettyFutureExecutor
import zio.{Task, Trace, UIO, ZIO}

final case class ChannelNetty[-A](
  private val channel: JChannel,
  private val convert: A => Any,
) extends Channel[A] {
  self =>

  private def foreach[S](await: Boolean)(run: JChannel => JChannelFuture): Task[Unit] = {
    if (await) NettyFutureExecutor.executed(run(channel))
    else ZIO.attempt(run(channel): Unit)
  }

  override def autoRead(flag: Boolean): UIO[Unit] =
    ZIO.succeed(channel.config.setAutoRead(flag): Unit)

  override def awaitClose: UIO[Unit] = ZIO.async[Any, Nothing, Unit] { register =>
    channel.closeFuture().addListener((_: JChannelFuture) => register(ZIO.unit))
    ()
  }

  override def close(await: Boolean = false): Task[Unit] = foreach(await) { _.close() }

  override def contramap[A1](f: A1 => A): ChannelNetty[A1] = copy(convert = convert.compose(f))

  override def flush: Task[Unit] = ZIO.attempt(channel.flush(): Unit)

  override def id: String = channel.id().asLongText()

  override def isAutoRead: UIO[Boolean] = ZIO.succeed(channel.config.isAutoRead)

  override def read: UIO[Unit] = ZIO.succeed(channel.read(): Unit)

  override def write(msg: A, await: Boolean = false): Task[Unit] = foreach(await) {
    _.write(convert(msg))
  }

  override def writeAndFlush(msg: A, await: Boolean = false): Task[Unit] = foreach(await) {
    _.writeAndFlush(convert(msg))
  }
}

object ChannelNetty {
  def make[A](channel: JChannel): ChannelNetty[A] = ChannelNetty(channel, identity)
}
