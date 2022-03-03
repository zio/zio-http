package zhttp.service

import zhttp.service.client.domain.DefaultClient
import zhttp.service.client.transport.Transport
import zio.Task
import zio.duration.Duration

import scala.concurrent.duration.DurationInt

/**
 * Configuration settings for the Client.
 */
trait ClientSettings { self =>

  import ClientSettings._

  def ++(other: ClientSettings): ClientSettings =
    Concat(self, other)

  def settings(s: Config = Config()): Config = self match {
    case Concat(self, other)                        => other.settings(self.settings(s))
    case TransportConfig(transport)                 => s.copy(transport = transport)
    case Threads(threads)                           => s.copy(threads = threads)
    case ResponseHeaderTimeout(rht)                 => s.copy(responseHeaderTimeout = rht)
    case IdleTimeout(idlt)                          => s.copy(idleTimeout = idlt)
    case RequestTimeout(rqt)                        => s.copy(requestTimeout = rqt)
    case ConnectionTimeout(connt)                   => s.copy(connectionTimeout = connt)
    case UserAgent(ua)                              => s.copy(userAgent = ua)
    case MaxTotalConnections(maxTotConn)            => s.copy(maxTotalConnections = maxTotConn)
    case MaxWaitQueueLimit(mwql)                    => s.copy(maxWaitQueueLimit = mwql)
    case MaxConnectionsPerRequestKey(maxConnPerReq) => s.copy(maxConnectionsPerRequestKey = maxConnPerReq)
    case DefaultSetting                             => s
    case _                                          => s
  }

  def make: Task[DefaultClient] =
    Client.make(self.asInstanceOf[ClientSettings])

  /**
   * Creates a specified type transport underneath, (like Epoll/KQueue/NIO/URing
   * etc) Default is Auto
   */
  def withTransport(transport: Transport): ClientSettings = Concat(self, ClientSettings.TransportConfig(transport))

  /**
   */
  def withThreads(count: Int): ClientSettings = Concat(self, ClientSettings.Threads(count))

  /**
   */
  def withResponseHeaderTimeout(responseHeaderTimeout: Duration): ClientSettings =
    Concat(self, ClientSettings.ResponseHeaderTimeout(responseHeaderTimeout))

  /**
   */
  def withIdleTimeout(idleTimeout: Duration): ClientSettings =
    Concat(self, ClientSettings.IdleTimeout(idleTimeout))

  /**
   */
  def withRequestTimeout(requestTimeout: Duration): ClientSettings =
    Concat(self, ClientSettings.RequestTimeout(requestTimeout))

  /**
   */
  def withConnectionTimeout(connectionTimeout: Duration): ClientSettings =
    Concat(self, ClientSettings.ConnectionTimeout(connectionTimeout))

  /**
   */
  def withMaxTotalConnections(count: Int): ClientSettings = Concat(self, ClientSettings.MaxTotalConnections(count))

  /**
   */
  def withMaxWaitQueueLimit(count: Int): ClientSettings = Concat(self, ClientSettings.MaxWaitQueueLimit(count))

  /**
   */
  def withMaxConnectionsPerRequestKey(count: Int): ClientSettings =
    Concat(self, ClientSettings.MaxConnectionsPerRequestKey(count))

}
object ClientSettings {
  protected[zhttp] final case class Config(
    transport: Transport = Transport.Auto,
    threads: Int = 0,
    responseHeaderTimeout: Duration =
      Duration.Infinity, // duration between the submission of request and the completion of the response header
    // Does not include time to read the response body
    idleTimeout: Duration = Duration.fromScala(1.minute),
    requestTimeout: Duration = Duration.fromScala(1.minute),      //
    connectionTimeout: Duration = Duration.fromScala(10.seconds), //
    userAgent: Option[String] = Some("Client"),                   //
    maxTotalConnections: Int = 10,                                //
    maxWaitQueueLimit: Int = 256,                                 //
    maxConnectionsPerRequestKey: Int = 20,                        //
    //    sslContext: ClientSSLOptions,      //

  )

  case class Concat(self: ClientSettings, other: ClientSettings) extends ClientSettings
  case class TransportConfig(transport: Transport)               extends ClientSettings
  case class Threads(threads: Int)                               extends ClientSettings
  case class ResponseHeaderTimeout(rht: Duration)                extends ClientSettings
  case class IdleTimeout(idlt: Duration)                         extends ClientSettings
  case class RequestTimeout(reqt: Duration)                      extends ClientSettings
  case class ConnectionTimeout(connt: Duration)                  extends ClientSettings
  case class UserAgent(ua: Option[String])                       extends ClientSettings
  case class MaxTotalConnections(maxTotConn: Int)                extends ClientSettings
  case class MaxWaitQueueLimit(mwql: Int)                        extends ClientSettings
  case class MaxConnectionsPerRequestKey(maxConnPerReq: Int)     extends ClientSettings
  case object DefaultSetting                                     extends ClientSettings
  //  private final case class SSLContext(ssl: ClientSSLOptions)                                   extends ClientSettings

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

  // TODO: to be elaborated and used WIP.
  def responseHeaderTimeout(rht: Duration): ClientSettings = ClientSettings.ResponseHeaderTimeout(rht)
  def idleTimeout(idt: Duration): ClientSettings           = ClientSettings.IdleTimeout(idt)
  def requestTimeout(rqt: Duration): ClientSettings        = ClientSettings.RequestTimeout(rqt)
  def connectionTimeout(connT: Duration): ClientSettings   = ClientSettings.ConnectionTimeout(connT)
  def userAgent(ua: Option[String]): ClientSettings        = ClientSettings.UserAgent(ua)
  def maxTotalConnections(maxTotConn: Int): ClientSettings = ClientSettings.MaxTotalConnections(maxTotConn)
  def maxWaitQueueLimit(maxWQLt: Int): ClientSettings      = ClientSettings.MaxWaitQueueLimit(maxWQLt)
  def maxConnectionsPerRequestKey(maxConnPerReqKey: Int): ClientSettings =
    ClientSettings.MaxConnectionsPerRequestKey(maxConnPerReqKey)

  def nio: ClientSettings    = ClientSettings.TransportConfig(Transport.Nio)
  def epoll: ClientSettings  = ClientSettings.TransportConfig(Transport.Epoll)
  def kQueue: ClientSettings = ClientSettings.TransportConfig(Transport.KQueue)
  def uring: ClientSettings  = ClientSettings.TransportConfig(Transport.URing)
  def auto: ClientSettings   = ClientSettings.TransportConfig(Transport.Auto)

  def defaultSetting = ClientSettings.DefaultSetting

}
