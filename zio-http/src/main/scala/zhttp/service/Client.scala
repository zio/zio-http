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
import zhttp.http.{HttpData, _}
import zhttp.service
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.{ClientChannelInitializer, ClientHttpChannelReader, ClientInboundHandler}
import zio.{Chunk, Promise, Task, ZIO}

import java.net.{InetAddress, InetSocketAddress}

final case class Client(zx: HttpRuntime[Any], cf: JChannelFactory[Channel], el: JEventLoopGroup)
    extends HttpMessageCodec {
  private def asyncRequest(
    req: Client.ClientParams,
    jReq: FullHttpRequest,
    promise: Promise[Throwable, FullHttpResponse],
    sslOption: ClientSSLOptions,
  ): Task[Unit] =
    ChannelFuture.unit {
      val read   = ClientHttpChannelReader(jReq, promise)
      val hand   = ClientInboundHandler(zx, read)
      val host   = req.url.host
      val port   = req.url.port.getOrElse(80) match {
        case -1   => 80
        case port => port
      }
      val scheme = req.url.kind match {
        case Location.Relative               => ""
        case Location.Absolute(scheme, _, _) => scheme.asString
      }
      val init   = ClientChannelInitializer(hand, scheme, sslOption)

      val jboo = new Bootstrap().channelFactory(cf).group(el).handler(init)
      if (host.isDefined) jboo.remoteAddress(new InetSocketAddress(host.get, port))

      jboo.connect()
    }

  def request(
    request: Client.ClientParams,
    sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): Task[Client.ClientResponse] =
    for {
      promise <- Promise.make[Throwable, FullHttpResponse]
      jReq = encodeClientParams(HttpVersion.HTTP_1_1, request)
      _    <- asyncRequest(request, jReq, promise, sslOption).catchAll(cause => promise.fail(cause)).fork
      jRes <- promise.await
      res  <- ZIO.fromEither(decodeJResponse(jRes))
    } yield res

}

object Client {
  def make: ZIO[EventLoopGroup with ChannelFactory, Nothing, Client] = for {
    cf <- ZIO.access[ChannelFactory](_.get)
    el <- ZIO.access[EventLoopGroup](_.get)
    zx <- HttpRuntime.default[Any]()
  } yield service.Client(zx, cf, el)

  def request(
    url: String,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] = for {
    url <- ZIO.fromEither(URL.fromString(url))
    res <- request(Method.GET -> url)
  } yield res

  def request(
    url: String,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] = for {
    url <- ZIO.fromEither(URL.fromString(url))
    res <- request(Method.GET -> url, sslOptions)
  } yield res

  def request(
    url: String,
    headers: List[Header],
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    for {
      url <- ZIO.fromEither(URL.fromString(url))
      res <- request(Method.GET -> url, headers, sslOptions)
    } yield res

  def request(
    url: String,
    headers: List[Header],
    content: HttpData[Any, Nothing],
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    for {
      url <- ZIO.fromEither(URL.fromString(url))
      res <- request(Method.GET -> url, headers, content)
    } yield res

  def request(
    endpoint: Endpoint,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientParams(endpoint))

  def request(
    endpoint: Endpoint,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientParams(endpoint), sslOptions)

  def request(
    endpoint: Endpoint,
    headers: List[Header],
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientParams(endpoint, headers), sslOptions)

  def request(
    endpoint: Endpoint,
    headers: List[Header],
    content: HttpData[Any, Nothing],
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientParams(endpoint, headers, content))

  def request(
    req: ClientParams,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    make.flatMap(_.request(req))

  def request(
    req: ClientParams,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    make.flatMap(_.request(req, sslOptions))

  final case class ClientParams(
    endpoint: Endpoint,
    headers: List[Header] = List.empty,
    content: HttpData[Any, Nothing] = HttpData.empty,
    private val channelContext: ChannelHandlerContext = null,
  ) extends HeadersHelpers { self =>
    val method: Method = endpoint._1
    val url: URL       = endpoint._2
    val route: Route   = method -> url.path

    def getBodyAsString: Option[String] = content match {
      case HttpData.Text(text, _) => Some(text)
      case HttpData.Binary(data)  => Some((new String(data.toArray, HTTP_CHARSET)))
      case HttpData.BinaryN(data) => Some(data.toString(HTTP_CHARSET))
      case _                      => Option.empty
    }

    def remoteAddress: Option[InetAddress] = {
      if (channelContext != null && channelContext.channel().remoteAddress().isInstanceOf[InetSocketAddress])
        Some(channelContext.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress)
      else
        None
    }
  }

  final case class ClientResponse(status: Status, headers: List[Header], content: Chunk[Byte])
}
