package zio.http.netty

import io.netty.channel.{Channel => NettyChannel, _}
import zio._
import zio.http._
import zio.http.netty._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

import java.util.concurrent.atomic.AtomicReference

package object server {
  // Re-export this to work around Scala 2.12 bug where a private
  // trait's companion object cannot be accessed.
  val nettyDriverDefault: ZLayer[ServerConfig, Throwable, Driver] = NettyDriver.default
  val nettyDriverManual: ZLayer[EventLoopGroup & ChannelFactory[ServerChannel] & ServerConfig, Nothing, Driver] =
    NettyDriver.manual
  val nettyDriverMake: ZIO[
    AppRef
      & ChannelFactory[ServerChannel]
      & ChannelInitializer[NettyChannel]
      & EventLoopGroup
      & ErrorCallbackRef
      & ServerConfig
      & ServerInboundHandler,
    Nothing,
    Driver,
  ] = NettyDriver.make

  private[server] type ErrorCallbackRef = AtomicReference[Option[Server.ErrorCallback]]
  private[server] type AppRef           = AtomicReference[(App[Any], ZEnvironment[Any])]
  private[server] type EnvRef           = AtomicReference[ZEnvironment[Any]]
}
