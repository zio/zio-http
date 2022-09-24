package zio.http

import zio.{Trace, ZLayer}
import zio.http.netty.{ChannelFactories, EventLoopGroups, _}
import zio.http.socket.SocketApp
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

case class ClientConfig(
  socketApp: Option[SocketApp[Any]] = None,
  ssl: Option[ClientSSLConfig] = None,
  proxy: Option[Proxy] = None,
  channelType: ChannelType = ChannelType.AUTO,
  nThreads: Int = 0,
  useAggregator: Boolean = true,
  maxHeaderSize: Int = 8192,
  requestDecompression: Decompression = Decompression.No,
) extends EventLoopGroups.Config {
  self =>
  def ssl(ssl: ClientSSLConfig): ClientConfig = self.copy(ssl = Some(ssl))

  def socketApp(socketApp: SocketApp[Any]): ClientConfig = self.copy(socketApp = Some(socketApp))

  def proxy(proxy: Proxy): ClientConfig = self.copy(proxy = Some(proxy))

  def channelType(channelType: ChannelType): ClientConfig = self.copy(channelType = channelType)

  def maxThreads(nThreads: Int): ClientConfig = self.copy(nThreads = nThreads)

  def useObjectAggregator(objectAggregator: Boolean): ClientConfig = self.copy(useAggregator = objectAggregator)

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

  val default = {
    implicit val trace = Trace.empty
    ZLayer.succeed(
      empty,
    ) >+> EventLoopGroups.fromConfig >+> ChannelFactories.Client.fromConfig >+> NettyRuntime.usingDedicatedThreadPool
  }

  def live(clientConfig: ClientConfig)(implicit trace: Trace) =
    ZLayer.succeed(
      clientConfig,
    ) >+> EventLoopGroups.fromConfig >+> ChannelFactories.Client.fromConfig >+> NettyRuntime.usingDedicatedThreadPool
}
