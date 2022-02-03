package zhttp.service.client.experimental

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import zhttp.service.client.experimental.model.ZConnectionState.ReqKey
import zhttp.service.client.experimental.model.{Timeouts, ZConnectionState}
import zhttp.service.client.experimental.transport.Transport
import zio.Task
import zio.duration.Duration

import scala.collection.mutable
import scala.concurrent.duration.DurationInt

trait ZClient[-R, +E] { self =>

  import ZClient._

  def ++[R1 <: R, E1 >: E](other: ZClient[R1, E1]): ZClient[R1, E1] =
    Concat(self, other)

  private def settings(s: Config = Config()): Config = self match {
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

  def make(implicit
    ev: E <:< Throwable,
  ): Task[DefaultZClient] =
    ZClient.make(self.asInstanceOf[ZClient[R, Throwable]])

}

object ZClient {
  type UClient = ZClient[Any, Nothing]
  private[zhttp] final case class Config(
    transport: Transport = Transport.Auto,
    threads: Int = 0,
    responseHeaderTimeout: Duration =
      Duration.Infinity, // duration between the submission of request and the completion of the response header
    // Does not include time to read the response body
    idleTimeout: Duration = Duration.fromScala(1.minute),
    requestTimeout: Duration = Duration.fromScala(1.minute),      //
    connectionTimeout: Duration = Duration.fromScala(10.seconds), //
    userAgent: Option[String] = Some("ZClient"),                  //
    maxTotalConnections: Int = 10,                                //
    maxWaitQueueLimit: Int = 256,                                 //
    maxConnectionsPerRequestKey: Int = 20,                        //
//    sslContext: ClientSSLOptions,      //

  )

  private final case class Concat[R, E](self: ZClient[R, E], other: ZClient[R, E]) extends ZClient[R, E]
  private final case class TransportConfig(transport: Transport)                   extends UClient
  private final case class Threads(threads: Int)                                   extends UClient
  private final case class ResponseHeaderTimeout(rht: Duration)                    extends UClient
  private final case class IdleTimeout(idlt: Duration)                             extends UClient
  private final case class RequestTimeout(reqt: Duration)                          extends UClient
  private final case class ConnectionTimeout(connt: Duration)                      extends UClient
  private final case class UserAgent(ua: Option[String])                           extends UClient
  private final case class MaxTotalConnections(maxTotConn: Int)                    extends UClient
  private final case class MaxWaitQueueLimit(mwql: Int)                            extends UClient
  private final case class MaxConnectionsPerRequestKey(maxConnPerReq: Int)         extends UClient
//  private final case class SSLContext(ssl: ClientSSLOptions)                                   extends UClient

  def transport(transport: Transport): UClient = ZClient.TransportConfig(transport)
  def threads(threads: Int): UClient           = ZClient.Threads(threads)

  def responseHeaderTimeout(rht: Duration): UClient               = ZClient.ResponseHeaderTimeout(rht)
  def idleTimeout(idt: Duration): UClient                         = ZClient.IdleTimeout(idt)
  def requestTimeout(rqt: Duration): UClient                      = ZClient.RequestTimeout(rqt)
  def connectionTimeout(connT: Duration): UClient                 = ZClient.ConnectionTimeout(connT)
  def userAgent(ua: Option[String]): UClient                      = ZClient.UserAgent(ua)
  def maxTotalConnections(maxTotConn: Int): UClient               = ZClient.MaxTotalConnections(maxTotConn)
  def maxWaitQueueLimit(maxWQLt: Int): UClient                    = ZClient.MaxWaitQueueLimit(maxWQLt)
  def maxConnectionsPerRequestKey(maxConnPerReqKey: Int): UClient =
    ZClient.MaxConnectionsPerRequestKey(maxConnPerReqKey)

  def nio: UClient    = ZClient.TransportConfig(Transport.Nio)
  def epoll: UClient  = ZClient.TransportConfig(Transport.Epoll)
  def kQueue: UClient = ZClient.TransportConfig(Transport.KQueue)
  def uring: UClient  = ZClient.TransportConfig(Transport.URing)
  def auto: UClient   = ZClient.TransportConfig(Transport.Auto)

  def make[R](zClient: ZClient[R, Throwable]): Task[DefaultZClient] = {
    val settings = zClient.settings()
    for {
      channelFactory <- settings.transport.clientChannel
      eventLoopGroup <- settings.transport.eventLoopGroupTask(settings.threads)
      zExec          <- zhttp.service.HttpRuntime.default[Any]

      clientBootStrap = new Bootstrap()
        .channelFactory(channelFactory)
        .group(eventLoopGroup)
      connRef <- zio.Ref.make(
        mutable.Map.empty[ReqKey, Channel],
      )
      timeouts    = Timeouts(settings.connectionTimeout, settings.idleTimeout, settings.requestTimeout)
      connManager = ZConnectionManager(connRef, ZConnectionState(), timeouts, clientBootStrap, zExec)
      clientImpl  = DefaultZClient(settings, connManager)
    } yield {
      clientImpl
    }
  }
}
