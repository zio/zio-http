package zhttp.service.client

import io.netty.channel.ChannelHandlerContext
import zhttp.service.ChannelFuture
import zio.{Promise, Task, UIO}

final case class ClientHttpChannelReader[E, A](msg: AnyRef, promise: Promise[E, A]) {
  def onChannelRead(a: A): UIO[Unit]                   = promise.succeed(a).unit
  def onExceptionCaught(e: E): UIO[Unit]               = promise.fail(e).unit
  def onActive(ctx: ChannelHandlerContext): Task[Unit] = ChannelFuture.unit(ctx.writeAndFlush(msg))
}
