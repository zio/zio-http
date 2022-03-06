package zhttp.service

import zhttp.service.client.transport.Transport

/**
 * Configuration settings for the Client.
 */
trait ClientSettings { self =>

  import ClientSettings._

  def ++(other: ClientSettings): ClientSettings =
    Concat(self, other)

  def settings(s: Config = Config()): Config = self match {
    case Concat(self, other)        => other.settings(self.settings(s))
    case TransportConfig(transport) => s.copy(transport = transport)
    case Threads(threads)           => s.copy(threads = threads)
    case DefaultSetting             => s
    case _                          => s
  }

  /**
   * Creates a specified type transport underneath, (like Epoll/KQueue/NIO/URing
   * etc) Default is Auto
   */
  def withTransport(transport: Transport): ClientSettings = Concat(self, ClientSettings.TransportConfig(transport))

  /**
   * specify thread count to be used by underlying netty event loop group for
   * creating number of event loops. Each EventLoop object is exclusively
   * associated with a single Thread and each event loop is associated with
   * multiple channels. If not specified NIO/EPoll/Kqueue groups create a pool
   * of 2 * number of processors and distribute them evenly across Channels.
   */
  def withThreads(count: Int): ClientSettings = Concat(self, ClientSettings.Threads(count))

}
object ClientSettings {
  protected[zhttp] final case class Config(
    transport: Transport = Transport.Auto,
    threads: Int = 0,
  )

  case class Concat(self: ClientSettings, other: ClientSettings) extends ClientSettings
  case class TransportConfig(transport: Transport)               extends ClientSettings
  case class Threads(threads: Int)                               extends ClientSettings
  case object DefaultSetting                                     extends ClientSettings

  /**
   * Choosing transport types like Nio,Epoll,KQueue etc
   * @param transport
   *   (Transport.Auto / Transport.Nio / Transport.Epoll / Transport.Uring )
   * @return
   */
  def transport(transport: Transport) = ClientSettings.TransportConfig(transport)

  /**
   * Number of threads to be used by underlying netty EventLoopGroup
   * @param threads
   * @return
   */
  def threads(threads: Int) = ClientSettings.Threads(threads)

  def nio: ClientSettings    = ClientSettings.TransportConfig(Transport.Nio)
  def epoll: ClientSettings  = ClientSettings.TransportConfig(Transport.Epoll)
  def kQueue: ClientSettings = ClientSettings.TransportConfig(Transport.KQueue)
  def uring: ClientSettings  = ClientSettings.TransportConfig(Transport.URing)
  def auto: ClientSettings   = ClientSettings.TransportConfig(Transport.Auto)

  def defaultSetting = ClientSettings.DefaultSetting

}
