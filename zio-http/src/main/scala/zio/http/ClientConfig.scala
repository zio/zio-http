package zio.http

import zio.http.service.ClientSSLHandler.ClientSSLOptions
import zio.http.socket.SocketApp

case class ClientConfig(
  socketApp: Option[SocketApp[Any]] = None,
  ssl: Option[ClientSSLOptions] = None,
  proxy: Option[Proxy] = None,
) {
  self =>
  def ssl(ssl: ClientSSLOptions): ClientConfig = self.copy(ssl = Some(ssl))

  def socketApp(socketApp: SocketApp[Any]): ClientConfig = self.copy(socketApp = Some(socketApp))

  def proxy(proxy: Proxy): ClientConfig = self.copy(proxy = Some(proxy))
}

object ClientConfig {
  def empty: ClientConfig = ClientConfig()
}
