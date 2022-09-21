package zio.http

import io.netty.channel
import io.netty.channel.{ChannelFactory, EventLoopGroup, _}
import zio.ZLayer
import zio.http.netty.{ChannelFactories, ChannelType, EventLoopGroups, NettyRuntime}
import zio.http.socket.SocketApp
import zio.{Duration, Scope}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

case class ClientConfig(
  socketApp: Option[SocketApp[Any]] = None,
  ssl: Option[ClientSSLConfig] = None,
  proxy: Option[Proxy] = None,
  channelType: ChannelType = ChannelType.AUTO,
  nThreads: Int = 0,
  useAggregator: Boolean = true,
  connectionPool: ConnectionPoolConfig = ConnectionPoolConfig.Disabled,
) extends EventLoopGroups.Config {
  self =>
  def ssl(ssl: ClientSSLConfig): ClientConfig = self.copy(ssl = Some(ssl))

  def socketApp(socketApp: SocketApp[Any]): ClientConfig = self.copy(socketApp = Some(socketApp))

  def proxy(proxy: Proxy): ClientConfig = self.copy(proxy = Some(proxy))

  def channelType(channelType: ChannelType): ClientConfig = self.copy(channelType = channelType)

  def maxThreads(nThreads: Int): ClientConfig = self.copy(nThreads = nThreads)

  def useObjectAggregator(objectAggregator: Boolean): ClientConfig = self.copy(useAggregator = objectAggregator)

  def withFixedConnectionPool(size: Int): ClientConfig =
    self.copy(connectionPool = ConnectionPoolConfig.Fixed(size))

  def withDynamicConnectionPool(minimum: Int, maximum: Int, ttl: Duration): ClientConfig =
    self.copy(connectionPool = ConnectionPoolConfig.Dynamic(minimum = minimum, maximum = maximum, ttl = ttl))
}

object ClientConfig {
  def empty: ClientConfig = ClientConfig()

  def default: ZLayer[
    Scope,
    Nothing,
    ClientConfig with EventLoopGroup with ChannelFactory[channel.Channel] with NettyRuntime,
  ] = ZLayer.succeed(
    empty,
  ) >+> EventLoopGroups.fromConfig >+> ChannelFactories.Client.fromConfig >+> NettyRuntime.usingDedicatedThreadPool

  def live(
    clientConfig: ClientConfig,
  ): ZLayer[Scope, Nothing, ClientConfig with EventLoopGroup with ChannelFactory[channel.Channel] with NettyRuntime] =
    ZLayer.succeed(
      clientConfig,
    ) >+> EventLoopGroups.fromConfig >+> ChannelFactories.Client.fromConfig >+> NettyRuntime.usingDedicatedThreadPool
}
