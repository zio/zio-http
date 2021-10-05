package zhttp.experiment

import io.netty.channel.ChannelHandlerContext
import zio.UIO

case class Context[-A](ctx: ChannelHandlerContext) extends AnyVal {
  def fireChannelRegistered(): UIO[Unit]               = UIO(ctx.fireChannelRegistered(): Unit)
  def fireChannelUnregistered(): UIO[Unit]             = UIO(ctx.fireChannelUnregistered(): Unit)
  def fireChannelActive(): UIO[Unit]                   = UIO(ctx.fireChannelActive(): Unit)
  def fireChannelInactive(): UIO[Unit]                 = UIO(ctx.fireChannelInactive(): Unit)
  def fireExceptionCaught(cause: Throwable): UIO[Unit] = UIO(ctx.fireExceptionCaught(cause): Unit)
  def fireUserEventTriggered(msg: Any): UIO[Unit]      = UIO(ctx.fireUserEventTriggered(msg): Unit)
  def fireChannelRead(a: Any): UIO[Unit]               = UIO(ctx.fireChannelRead(a): Unit)
  def fireChannelReadComplete(): UIO[Unit]             = UIO(ctx.fireChannelReadComplete(): Unit)
  def fireChannelWritabilityChanged(): UIO[Unit]       = UIO(ctx.fireChannelWritabilityChanged(): Unit)

  def writeAndFlush(a: A): UIO[Unit] = UIO(ctx.writeAndFlush(a): Unit)
  def write(a: A): UIO[Unit]         = UIO(ctx.write(a): Unit)
  def flush: UIO[Unit]               = UIO(ctx.flush(): Unit)
  def read: UIO[Unit]                = UIO(ctx.read(): Unit)
  def close: UIO[Unit]               = UIO(ctx.close(): Unit)
}
