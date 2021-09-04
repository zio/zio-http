package zhttp.service.client

import io.netty.channel.ChannelHandlerContext
import zhttp.service.ChannelFuture
import zio.{Promise, Task, UIO}

final case class ClientHttpChannelReader[E, A](msg: AnyRef, promise: Promise[E, A]) {
  def onChannelRead(a: A): UIO[Unit]                   = {
println("resolving promise from http reader/handler")
    promise.succeed(a).unit
  }
  def onExceptionCaught(e: E): UIO[Unit]               = promise.fail(e).unit
  def onActive(ctx: ChannelHandlerContext): Task[Unit] = {
println("sending request from http handler reader on channel active")
    ChannelFuture.unit(ctx.writeAndFlush(msg))
  }
}
