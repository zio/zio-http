package zio.http

import zio.ZLayer
import zio.http.service.ClientSSLHandler.ClientSSLOptions
import zio.http.socket.SocketApp

case class ClientConfig(
  socketApp: Option[SocketApp[Any]] = None,
  ssl: Option[ClientSSLOptions] = None,
  proxy: Option[Proxy] = None,
  channelType: ChannelType = ChannelType.AUTO,
  nThreads: Int = 0,
) {
  self =>
  def ssl(ssl: ClientSSLOptions): ClientConfig = self.copy(ssl = Some(ssl))

  def socketApp(socketApp: SocketApp[Any]): ClientConfig = self.copy(socketApp = Some(socketApp))

  def proxy(proxy: Proxy): ClientConfig = self.copy(proxy = Some(proxy))

  def channelType(channelType: ChannelType): ClientConfig = self.copy(channelType = channelType)

  def maxThreads(nThreads: Int): ClientConfig = self.copy(nThreads = nThreads)
}

object ClientConfig {
  def empty: ClientConfig = ClientConfig()

  def default = ZLayer.succeed(empty)

  def live(clientConfig: ClientConfig) = ZLayer.succeed(clientConfig)
}
