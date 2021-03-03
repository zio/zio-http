package zhttp.service.netty.client

import zhttp.core.netty.JChannelHandlerContext
import zhttp.service.netty.ChannelFuture
import zio.{Promise, Task, UIO}

final case class ClientHttpChannelReader[E, A](msg: AnyRef, promise: Promise[E, A]) {
  def onChannelRead(a: A): UIO[Unit]                    = promise.succeed(a).unit
  def onExceptionCaught(e: E): UIO[Unit]                = promise.fail(e).unit
  def onActive(ctx: JChannelHandlerContext): Task[Unit] = ChannelFuture.unit(ctx.writeAndFlush(msg))
}
