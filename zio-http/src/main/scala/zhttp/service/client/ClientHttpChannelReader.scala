package zhttp.service.client

import io.netty.handler.codec.DecoderException
import zhttp.core.JChannelHandlerContext
import zhttp.service.ChannelFuture
import zio.{Promise, Task, UIO}

import javax.net.ssl.SSLHandshakeException

final case class ClientHttpChannelReader[E, A](msg: AnyRef, promise: Promise[E, A]) {
  def onChannelRead(a: A): UIO[Unit]                    = promise.succeed(a).unit
  def onExceptionCaught(e: E): UIO[Unit] = {
    e match {
      case a: DecoderException =>
        a.getCause match {
          case _: SSLHandshakeException =>
            promise
              .fail(
                new Exception("SSL certificate problem: unable to get local issuer certificate").asInstanceOf[E],
              ) //throw sslHandshake exception
              .unit
          case _                        => promise.fail(e).unit
        }
      case _                   => promise.fail(e).unit
    }
  }
  def onActive(ctx: JChannelHandlerContext): Task[Unit] = ChannelFuture.unit(ctx.writeAndFlush(msg))
}
