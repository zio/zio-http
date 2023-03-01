package zio.http.netty

import io.netty.channel.{ChannelFactory, EventLoopGroup, ServerChannel}
import zio._
import zio.http._

import java.util.concurrent.atomic.AtomicReference // scalafix:ok;

package object server {
  private[server] type ErrorCallbackRef = AtomicReference[Option[Server.ErrorCallback]]
  private[server] type AppRef           = AtomicReference[(App[Any], ZEnvironment[Any])]
  private[server] type EnvRef           = AtomicReference[ZEnvironment[Any]]

  def default: ZLayer[ServerConfig, Throwable, Driver] =
    NettyDriver.default

  def manual: ZLayer[EventLoopGroup & ChannelFactory[ServerChannel] & ServerConfig, Nothing, Driver] =
    NettyDriver.manual
}
