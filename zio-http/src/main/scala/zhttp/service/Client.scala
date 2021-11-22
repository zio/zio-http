package zhttp.service
import io.netty.bootstrap.Bootstrap
import io.netty.channel.{
  Channel,
  ChannelFactory => JChannelFactory,
  ChannelHandlerContext,
  EventLoopGroup => JEventLoopGroup,
}
import io.netty.handler.codec.http.{FullHttpRequest, FullHttpResponse, HttpVersion}
import zhttp.http.URL.Location
import zhttp.http._
import zhttp.service
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.{
  ClientChannelInitializer,
  ClientHttpChannelReader,
  Http2ClientResponseHandler,
  HttpClientResponseHandler,
}
import zio.{Chunk, Promise, Task, ZIO}

import java.net.{InetAddress, InetSocketAddress}
final case class Client(zx: HttpRuntime[Any], cf: JChannelFactory[Channel], el: JEventLoopGroup)
  extends HttpMessageCodec {
  private def asyncRequest(
                            req: Client.ClientParams,
                            jReq: FullHttpRequest,
                            promise: Promise[Throwable, FullHttpResponse],
                            sslOption: ClientSSLOptions,
                            enableHttp2: Boolean,
                          ): Task[Unit] =
    ChannelFuture.unit {
      val scheme    = req.url.kind match {
        case Location.Relative               => ""
        case Location.Absolute(scheme, _, _) => scheme.asString
      }
      val read      = ClientHttpChannelReader(jReq, promise)
      val httpHand  = HttpClientResponseHandler(zx, read)
      val host      = req.url.host
      val port      = req.url.port.getOrElse(80) match {
        case -1   => 80
        case port => port
      }
      val http2Hand = Http2ClientResponseHandler(zx)
      val ini       = ClientChannelInitializer(sslOption, httpHand, http2Hand, scheme, enableHttp2, jReq)
      // since the first request is send automatically, placing in the response handler map under 1
      if (enableHttp2) http2Hand.put(1, promise)
      val jboo      = new Bootstrap().channelFactory(cf).group(el).handler(ini)
      if (host.isDefined) jboo.remoteAddress(new InetSocketAddress(host.get, port))
      jboo.connect()
    }

  def request(
               request: Client.ClientParams,
               sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
               enableHttp2: Boolean,
             ): Task[Client.ClientResponse] =
    for {
      promise <- Promise.make[Throwable, FullHttpResponse]
      jReq = encodeClientParams(HttpVersion.HTTP_1_1, request, enableHttp2)
      _    <- asyncRequest(request, jReq, promise, sslOption, enableHttp2).catchAll(cause => promise.fail(cause)).fork
      jRes <- promise.await
      res  <- ZIO.fromEither(decodeJResponse(jRes))
    } yield res
}
object Client {
  type Endpoint = (Method, URL)
  def make: ZIO[EventLoopGroup with ChannelFactory, Nothing, Client] = for {
    cf <- ZIO.access[ChannelFactory](_.get)
    el <- ZIO.access[EventLoopGroup](_.get)
    zx <- HttpRuntime.default[Any]()
  } yield service.Client(zx, cf, el)

  def request(
               url: String,
               http2: Boolean,
             ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] = for {
    url <- ZIO.fromEither(URL.fromString(url))
    res <- request(Method.GET -> url, http2)
  } yield res

  def request(
               url: String,
               sslOptions: ClientSSLOptions,
               http2: Boolean,
             ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] = for {
    url <- ZIO.fromEither(URL.fromString(url))
    res <- request(Method.GET -> url, sslOptions, http2)
  } yield res

  def request(
               url: String,
               headers: List[Header],
               http2: Boolean = false,
               sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
             ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    for {
      url <- ZIO.fromEither(URL.fromString(url))
      res <- request(Method.GET -> url, headers, sslOptions, http2)
    } yield res

  def request(
               url: String,
               headers: List[Header],
               content: HttpData[Any, Nothing],
               http2: Boolean,
             ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    for {
      url <- ZIO.fromEither(URL.fromString(url))
      res <- request(Method.GET -> url, headers, content, http2)
    } yield res

  def request(
               endpoint: Endpoint,
               http2: Boolean,
             ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientParams(endpoint), http2)

  def request(
               endpoint: Endpoint,
               sslOptions: ClientSSLOptions,
               http2: Boolean,
             ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientParams(endpoint), sslOptions, http2)

  def request(
               endpoint: Endpoint,
               headers: List[Header],
               sslOptions: ClientSSLOptions,
               http2: Boolean,
             ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientParams(endpoint, headers), sslOptions, http2)

  def request(
               endpoint: Endpoint,
               headers: List[Header],
               content: HttpData[Any, Nothing],
               http2: Boolean,
             ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientParams(endpoint, headers, content), http2)

  def request(
               req: ClientParams,
               http2: Boolean,
             ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    make.flatMap(_.request(req, enableHttp2 = http2))

  def request(
               req: ClientParams,
               sslOptions: ClientSSLOptions,
               http2: Boolean,
             ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    make.flatMap(_.request(req, sslOptions, http2))

  final case class ClientParams(
                                 endpoint: Endpoint,
                                 getHeaders: List[Header] = List.empty,
                                 content: HttpData[Any, Nothing] = HttpData.empty,
                                 private val channelContext: ChannelHandlerContext = null,
                               ) extends HeaderExtension[ClientParams] { self =>
    val method: Method = endpoint._1
    val url: URL       = endpoint._2
    def getBodyAsString: Option[String] = content match {
      case HttpData.Text(text, _) => Some(text)
      case HttpData.BinaryChunk(data)  => Some((new String(data.toArray, HTTP_CHARSET)))
      case HttpData.BinaryByteBuf(data) => Some(data.toString(HTTP_CHARSET))
      case _                      => Option.empty
    }
    def remoteAddress: Option[InetAddress] = {
      if (channelContext != null && channelContext.channel().remoteAddress().isInstanceOf[InetSocketAddress])
        Some(channelContext.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress)
      else
        None
    }
    /**
     * Updates the headers using the provided function
     */
    override def updateHeaders(f: List[Header] => List[Header]): ClientParams =
      self.copy(getHeaders = f(self.getHeaders))
  }
  final case class ClientResponse(status: Status, headers: List[Header], content: Chunk[Byte])
}