package zhttp.service

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup}
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
import zio.{Promise, Task, ZIO}

import java.net.InetSocketAddress

final case class Client(zx: UnsafeChannelExecutor[Any], cf: JChannelFactory[Channel], el: JEventLoopGroup)
    extends HttpMessageCodec {
  private def asyncRequest(
    req: Request,
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
      //since the first request is send automatically, placing in the response handler map under 1
      if (enableHttp2) http2Hand.put(1, promise)
      val jboo      = new Bootstrap().channelFactory(cf).group(el).handler(ini)
      if (host.isDefined) jboo.remoteAddress(new InetSocketAddress(host.get, port))
      jboo.connect()
    }

  def request(
    request: Request,
    enableHttp2: Boolean,
    sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): Task[UHttpResponse] =
    for {
      promise <- Promise.make[Throwable, FullHttpResponse]
      jReq = encodeRequest(HttpVersion.HTTP_1_1, request, enableHttp2)
      _    <- asyncRequest(request, jReq, promise, sslOption, enableHttp2).catchAll(cause => promise.fail(cause)).fork
      jRes <- promise.await
      res  <- ZIO.fromEither(decodeJResponse(jRes))
    } yield res

}

object Client {
  def make: ZIO[EventLoopGroup with ChannelFactory, Nothing, Client] = for {
    cf <- ZIO.access[ChannelFactory](_.get)
    el <- ZIO.access[EventLoopGroup](_.get)
    zx <- UnsafeChannelExecutor.make[Any]()
  } yield service.Client(zx, cf, el)

  def request(
    url: String,
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] = for {
    url <- ZIO.fromEither(URL.fromString(url))
    res <- request(Method.GET -> url, enableHttp2)
  } yield res

  def request(
    url: String,
    sslOptions: ClientSSLOptions,
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] = for {
    url <- ZIO.fromEither(URL.fromString(url))
    res <- request(Method.GET -> url, sslOptions, enableHttp2)
  } yield res

  def request(
    url: String,
    headers: List[Header],
    enableHttp2: Boolean,
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    for {
      url <- ZIO.fromEither(URL.fromString(url))
      res <- request(Method.GET -> url, headers, sslOptions, enableHttp2)
    } yield res

  def request(
    url: String,
    headers: List[Header],
    content: HttpData[Any, Nothing],
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    for {
      url <- ZIO.fromEither(URL.fromString(url))
      res <- request(Method.GET -> url, headers, content, enableHttp2)
    } yield res

  def request(
    endpoint: Endpoint,
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    request(Request(endpoint), enableHttp2)

  def request(
    endpoint: Endpoint,
    sslOptions: ClientSSLOptions,
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    request(Request(endpoint), enableHttp2, sslOptions)

  def request(
    endpoint: Endpoint,
    headers: List[Header],
    sslOptions: ClientSSLOptions,
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    request(Request(endpoint, headers), enableHttp2, sslOptions)

  def request(
    endpoint: Endpoint,
    headers: List[Header],
    content: HttpData[Any, Nothing],
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    request(Request(endpoint, headers, content), enableHttp2)

  def request(
    req: Request,
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    make.flatMap(_.request(req, enableHttp2))

  def request(
    req: Request,
    enableHttp2: Boolean,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    make.flatMap(_.request(req, enableHttp2, sslOptions))

}
