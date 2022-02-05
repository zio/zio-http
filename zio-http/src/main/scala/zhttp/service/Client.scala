package zhttp.service

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, ChannelHandlerContext, EventLoopGroup => JEventLoopGroup}
import io.netty.handler.codec.http.HttpVersion
import zhttp.http.URL.Location
import zhttp.http._
import zhttp.http.headers.HeaderExtension
import zhttp.service
import zhttp.service.Client.{ClientRequest, ClientResponse}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.model.ZConnectionState.ReqKey
import zhttp.service.client.model.{Timeouts, ZConnectionState}
import zhttp.service.client.transport.{Transport, ZConnectionManager}
import zhttp.service.client.{ClientChannelInitializer, ClientInboundHandler, DefaultClient}
import zio.duration.Duration
import zio.{Chunk, Promise, Task, ZIO}

import java.net.{InetAddress, InetSocketAddress}
import scala.collection.mutable
import scala.concurrent.duration.DurationInt

trait Client { self =>

  import Client._

  def ++(other: Client): Client =
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
    Client.make(self.asInstanceOf[Client])

}
@Deprecated
final case class OldClient(rtm: HttpRuntime[Any], cf: JChannelFactory[Channel], el: JEventLoopGroup)
    extends Client
    with HttpMessageCodec {
  def request(
    request: Client.ClientRequest,
    sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): Task[Client.ClientResponse] =
    for {
      promise <- Promise.make[Throwable, Client.ClientResponse]
      _       <- Task(asyncRequest(request, promise, sslOption)).catchAll(cause => promise.fail(cause))
      res     <- promise.await
    } yield res

  private def asyncRequest(
    req: ClientRequest,
    promise: Promise[Throwable, ClientResponse],
    sslOption: ClientSSLOptions,
  ): Unit = {
    val jReq = encodeClientParams(req)
    try {
      val hand   = ClientInboundHandler(rtm, jReq, promise)
      val host   = req.url.host
      val port   = req.url.port.getOrElse(80) match {
        case -1   => 80
        case port => port
      }
      val scheme = req.url.kind match {
        case Location.Relative               => ""
        case Location.Absolute(scheme, _, _) => scheme.encode
      }
      val init   = ClientChannelInitializer(hand, scheme, sslOption)

      val jboo = new Bootstrap().channelFactory(cf).group(el).handler(init)
      if (host.isDefined) jboo.remoteAddress(new InetSocketAddress(host.get, port))

      jboo.connect(): Unit
    } catch {
      case _: Throwable =>
        if (jReq.refCnt() > 0) {
          jReq.release(jReq.refCnt()): Unit
        }
    }
  }

}

object Client {

  type UClient = Client
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

  private final case class Concat[R, E](self: Client, other: Client)       extends Client
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

  def transport(transport: Transport): UClient = Client.TransportConfig(transport)
  def threads(threads: Int): UClient           = Client.Threads(threads)

  def responseHeaderTimeout(rht: Duration): UClient               = Client.ResponseHeaderTimeout(rht)
  def idleTimeout(idt: Duration): UClient                         = Client.IdleTimeout(idt)
  def requestTimeout(rqt: Duration): UClient                      = Client.RequestTimeout(rqt)
  def connectionTimeout(connT: Duration): UClient                 = Client.ConnectionTimeout(connT)
  def userAgent(ua: Option[String]): UClient                      = Client.UserAgent(ua)
  def maxTotalConnections(maxTotConn: Int): UClient               = Client.MaxTotalConnections(maxTotConn)
  def maxWaitQueueLimit(maxWQLt: Int): UClient                    = Client.MaxWaitQueueLimit(maxWQLt)
  def maxConnectionsPerRequestKey(maxConnPerReqKey: Int): UClient =
    Client.MaxConnectionsPerRequestKey(maxConnPerReqKey)

  def nio: UClient    = Client.TransportConfig(Transport.Nio)
  def epoll: UClient  = Client.TransportConfig(Transport.Epoll)
  def kQueue: UClient = Client.TransportConfig(Transport.KQueue)
  def uring: UClient  = Client.TransportConfig(Transport.URing)
  def auto: UClient   = Client.TransportConfig(Transport.Auto)

  def make[R](client: Client): Task[DefaultClient] = {
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

  def make: ZIO[EventLoopGroup with ChannelFactory, Nothing, OldClient] = for {
    cf <- ZIO.access[ChannelFactory](_.get)
    el <- ZIO.access[EventLoopGroup](_.get)
    zx <- HttpRuntime.default[Any]
  } yield service.OldClient(zx, cf, el)

  def request(
    url: String,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] = for {
    url <- ZIO.fromEither(URL.fromString(url))
    res <- request(Method.GET, url)
  } yield res

  def request(
    url: String,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] = for {
    url <- ZIO.fromEither(URL.fromString(url))
    res <- request(Method.GET, url, sslOptions)
  } yield res

  def request(
    url: String,
    headers: Headers,
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    for {
      url <- ZIO.fromEither(URL.fromString(url))
      res <- request(Method.GET, url, headers, sslOptions)
    } yield res

  def request(
    url: String,
    headers: Headers,
    content: HttpData,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    for {
      url <- ZIO.fromEither(URL.fromString(url))
      res <- request(Method.GET, url, headers, content)
    } yield res

  def request(
    method: Method,
    url: URL,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientRequest(method = method, url = url))

  def request(
    method: Method,
    url: URL,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientRequest(method = method, url = url), sslOptions)

  def request(
    method: Method,
    url: URL,
    headers: Headers,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientRequest(method = method, url = url, headers = headers), sslOptions)

  def request(
    method: Method,
    url: URL,
    headers: Headers,
    content: HttpData,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientRequest(method = method, url = url, headers = headers, data = content))

  def request(
    req: ClientRequest,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    make.flatMap(_.request(req))

  def request(
    req: ClientRequest,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    make.flatMap(_.request(req, sslOptions))

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
