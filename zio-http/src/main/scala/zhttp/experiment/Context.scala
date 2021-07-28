package zhttp.experiment

import io.netty.channel.{ChannelHandlerContext => JChannelHandlerContext}
import zio.UIO

final class Context[-A](private[zhttp] val asJava: JChannelHandlerContext) extends AnyVal {
  private[zhttp] def fireExceptionCaught(cause: Throwable): UIO[Unit] = UIO(asJava.fireExceptionCaught(cause): Unit)
  private[zhttp] def fireRegistered(): UIO[Unit]                      = UIO(asJava.fireChannelRegistered(): Unit)
  private[zhttp] def fireChannelRead(data: Any): UIO[Unit]            = UIO(asJava.fireChannelRead(data): Unit)
  private[zhttp] def fireChannelReadComplete(): UIO[Unit]             = UIO(asJava.fireChannelReadComplete(): Unit)
  def write(a: A): UIO[Unit]                                          = UIO(asJava.write(a): Unit)
  def writeAndFlush(a: A): UIO[Unit]                                  = UIO(asJava.writeAndFlush(a): Unit)
  def read: UIO[Unit]                                                 = UIO(asJava.read(): Unit)
  def flush: UIO[Unit]                                                = UIO(asJava.flush(): Unit)
  def close: UIO[Unit]                                                = UIO(asJava.close(): Unit)
}
