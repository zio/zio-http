package zhttp.service

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.channel.{Channel, ChannelHandlerContext}
import io.netty.handler.codec.http.HttpVersion
import zhttp.http._
import zhttp.http.headers.HeaderExtension
import zhttp.service.client.DefaultClient
import zhttp.service.client.model.ZConnectionState.ReqKey
import zhttp.service.client.model.{Timeouts, ZConnectionState}
import zhttp.service.client.transport.{Transport, ZConnectionManager}
import zio.duration.Duration
import zio.{Chunk, Task}

import java.net.{InetAddress, InetSocketAddress}
import scala.collection.mutable
import scala.concurrent.duration.DurationInt

trait NewClient { self =>

  import NewClient._

  def ++(other: NewClient): NewClient =
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

  def make: Task[DefaultClient] =
    NewClient.make(self.asInstanceOf[NewClient])

}

object NewClient {

  type UClient = NewClient
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

  private final case class Concat[R, E](self: NewClient, other: NewClient) extends NewClient
  private final case class TransportConfig(transport: Transport)           extends UClient
  private final case class Threads(threads: Int)                           extends UClient
  private final case class ResponseHeaderTimeout(rht: Duration)            extends UClient
  private final case class IdleTimeout(idlt: Duration)                     extends UClient
  private final case class RequestTimeout(reqt: Duration)                  extends UClient
  private final case class ConnectionTimeout(connt: Duration)              extends UClient
  private final case class UserAgent(ua: Option[String])                   extends UClient
  private final case class MaxTotalConnections(maxTotConn: Int)            extends UClient
  private final case class MaxWaitQueueLimit(mwql: Int)                    extends UClient
  private final case class MaxConnectionsPerRequestKey(maxConnPerReq: Int) extends UClient
  //  private final case class SSLContext(ssl: ClientSSLOptions)                                   extends UClient

  def transport(transport: Transport): UClient = NewClient.TransportConfig(transport)
  def threads(threads: Int): UClient           = NewClient.Threads(threads)

  def responseHeaderTimeout(rht: Duration): UClient               = NewClient.ResponseHeaderTimeout(rht)
  def idleTimeout(idt: Duration): UClient                         = NewClient.IdleTimeout(idt)
  def requestTimeout(rqt: Duration): UClient                      = NewClient.RequestTimeout(rqt)
  def connectionTimeout(connT: Duration): UClient                 = NewClient.ConnectionTimeout(connT)
  def userAgent(ua: Option[String]): UClient                      = NewClient.UserAgent(ua)
  def maxTotalConnections(maxTotConn: Int): UClient               = NewClient.MaxTotalConnections(maxTotConn)
  def maxWaitQueueLimit(maxWQLt: Int): UClient                    = NewClient.MaxWaitQueueLimit(maxWQLt)
  def maxConnectionsPerRequestKey(maxConnPerReqKey: Int): UClient =
    NewClient.MaxConnectionsPerRequestKey(maxConnPerReqKey)

  def nio: UClient    = NewClient.TransportConfig(Transport.Nio)
  def epoll: UClient  = NewClient.TransportConfig(Transport.Epoll)
  def kQueue: UClient = NewClient.TransportConfig(Transport.KQueue)
  def uring: UClient  = NewClient.TransportConfig(Transport.URing)
  def auto: UClient   = NewClient.TransportConfig(Transport.Auto)

  def make[R](client: NewClient): Task[DefaultClient] = {
    val settings = client.settings()
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
      clientImpl  = DefaultClient(settings, connManager)
    } yield {
      clientImpl
    }
  }

  final case class ClientRequest(
    httpVersion: HttpVersion = HttpVersion.HTTP_1_1,
    method: Method,
    url: URL,
    headers: Headers = Headers.empty,
    data: HttpData = HttpData.empty,
    private val channelContext: ChannelHandlerContext = null,
  ) extends HeaderExtension[ClientRequest] { self =>

    def getBodyAsString: Option[String] = data match {
      case HttpData.Text(text, _)       => Some(text)
      case HttpData.BinaryChunk(data)   => Some(new String(data.toArray, HTTP_CHARSET))
      case HttpData.BinaryByteBuf(data) => Some(data.toString(HTTP_CHARSET))
      case _                            => Option.empty
    }

    def getHeaders: Headers = headers

    def remoteAddress: Option[InetAddress] = {
      if (channelContext != null && channelContext.channel().remoteAddress().isInstanceOf[InetSocketAddress])
        Some(channelContext.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress)
      else
        None
    }

    /**
     * Updates the headers using the provided function
     */
    override def updateHeaders(update: Headers => Headers): ClientRequest =
      self.copy(headers = update(self.getHeaders))
  }

  final case class ClientResponse(status: Status, headers: Headers, private[zhttp] val buffer: ByteBuf)
      extends HeaderExtension[ClientResponse] { self =>

    def getBody: Task[Chunk[Byte]] = Task(Chunk.fromArray(ByteBufUtil.getBytes(buffer)))

    def getBodyAsByteBuf: Task[ByteBuf] = Task(buffer)

    def getBodyAsString: Task[String] = Task(buffer.toString(self.getCharset))

    override def getHeaders: Headers = headers

    override def updateHeaders(update: Headers => Headers): ClientResponse = self.copy(headers = update(headers))
  }

}
