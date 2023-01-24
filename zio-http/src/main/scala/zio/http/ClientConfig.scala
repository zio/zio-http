package zio.http

import zio.http.netty.{ChannelType, EventLoopGroups}
import zio.http.socket.SocketApp
import zio.{Duration, ZLayer}

import java.net.InetSocketAddress

case class ClientConfig(
  socketApp: Option[SocketApp[Any]] = None,
  ssl: Option[ClientSSLConfig] = None,
  proxy: Option[Proxy] = None,
  channelType: ChannelType = ChannelType.AUTO,
  nThreads: Int = 0,
  useAggregator: Boolean = true,
  connectionPool: ConnectionPoolConfig = ConnectionPoolConfig.Disabled,
  maxHeaderSize: Int = 8192,
  requestDecompression: Decompression = Decompression.No,
  localAddress: Option[InetSocketAddress] = None,
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

  /**
   * Configure the client to use `maxHeaderSize` value when encode/decode
   * headers.
   */
  def maxHeaderSize(headerSize: Int): ClientConfig = self.copy(maxHeaderSize = headerSize)

  def requestDecompression(isStrict: Boolean): ClientConfig =
    self.copy(requestDecompression = if (isStrict) Decompression.Strict else Decompression.NonStrict)
}

object ClientConfig {
  def empty: ClientConfig = ClientConfig()

  val default: ZLayer[Any, Nothing, ClientConfig] =
    live(empty)

  def live(clientConfig: ClientConfig): ZLayer[Any, Nothing, ClientConfig] =
    ZLayer.succeed(clientConfig)
}
