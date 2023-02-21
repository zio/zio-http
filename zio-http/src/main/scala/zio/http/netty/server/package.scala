package zio.http.netty

import io.netty.channel.{ChannelFactory, EventLoopGroup, ServerChannel}
import zio.http._
import zio._

import java.util.concurrent.atomic.AtomicReference
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

package object server {

  private[server] type ErrorCallbackRef = AtomicReference[Option[Server.ErrorCallback]]
  private[server] type AppRef           = AtomicReference[(App[Any], ZEnvironment[Any])]
  private[server] type EnvRef           = AtomicReference[ZEnvironment[Any]]

  def default: ZLayer[ServerConfig, Throwable, Driver] =
    NettyDriver.default

  def manual: ZLayer[EventLoopGroup & ChannelFactory[ServerChannel] & ServerConfig, Nothing, Driver] =
    NettyDriver.manual
}
