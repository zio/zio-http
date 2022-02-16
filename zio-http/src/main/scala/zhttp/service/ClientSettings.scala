package zhttp.service

import zhttp.service.client.model.DefaultClient
import zhttp.service.client.transport.Transport
import zio.Task
import zio.duration.Duration

import scala.concurrent.duration.DurationInt

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
    // case _ To be deleted
    case _                                          => s.copy(threads = 2)
  }

  def make: Task[DefaultClient] =
    Client.make(self.asInstanceOf[ClientSettings])
}
object ClientSettings {
  type UClient = ClientSettings
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

  case class Concat[R, E](self: ClientSettings, other: ClientSettings) extends ClientSettings
  case class TransportConfig(transport: Transport)                     extends UClient
  case class Threads(threads: Int)                                     extends UClient
  case class ResponseHeaderTimeout(rht: Duration)                      extends UClient
  case class IdleTimeout(idlt: Duration)                               extends UClient
  case class RequestTimeout(reqt: Duration)                            extends UClient
  case class ConnectionTimeout(connt: Duration)                        extends UClient
  case class UserAgent(ua: Option[String])                             extends UClient
  case class MaxTotalConnections(maxTotConn: Int)                      extends UClient
  case class MaxWaitQueueLimit(mwql: Int)                              extends UClient
  case class MaxConnectionsPerRequestKey(maxConnPerReq: Int)           extends UClient
  //  private final case class SSLContext(ssl: ClientSSLOptions)                                   extends UClient

  def transport(transport: Transport) = ClientSettings.TransportConfig(transport)
  def threads(threads: Int)           = ClientSettings.Threads(threads)

  def responseHeaderTimeout(rht: Duration): UClient               = ClientSettings.ResponseHeaderTimeout(rht)
  def idleTimeout(idt: Duration): UClient                         = ClientSettings.IdleTimeout(idt)
  def requestTimeout(rqt: Duration): UClient                      = ClientSettings.RequestTimeout(rqt)
  def connectionTimeout(connT: Duration): UClient                 = ClientSettings.ConnectionTimeout(connT)
  def userAgent(ua: Option[String]): UClient                      = ClientSettings.UserAgent(ua)
  def maxTotalConnections(maxTotConn: Int): UClient               = ClientSettings.MaxTotalConnections(maxTotConn)
  def maxWaitQueueLimit(maxWQLt: Int): UClient                    = ClientSettings.MaxWaitQueueLimit(maxWQLt)
  def maxConnectionsPerRequestKey(maxConnPerReqKey: Int): UClient =
    ClientSettings.MaxConnectionsPerRequestKey(maxConnPerReqKey)

  def nio: UClient    = ClientSettings.TransportConfig(Transport.Nio)
  def epoll: UClient  = ClientSettings.TransportConfig(Transport.Epoll)
  def kQueue: UClient = ClientSettings.TransportConfig(Transport.KQueue)
  def uring: UClient  = ClientSettings.TransportConfig(Transport.URing)
  def auto: UClient   = ClientSettings.TransportConfig(Transport.Auto)

}
