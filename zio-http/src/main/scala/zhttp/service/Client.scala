package zhttp.service

import java.net.InetSocketAddress

import io.netty.handler.codec.http.{HttpHeaderNames, HttpVersion => JHttpVersion}
import zhttp.core._
import zhttp.http.URL.Location
import zhttp.http._
import zhttp.service
import zhttp.service.client.{ClientChannelInitializer, ClientHttpChannelReader, ClientInboundHandler}
import zio.{Promise, Task, ZIO}

final case class Client(zx: UnsafeChannelExecutor[Any], cf: JChannelFactory[JChannel], el: JEventLoopGroup)
    extends HttpMessageCodec {
  private def asyncRequest(
    req: Request,
    jReq: JFullHttpRequest,
    promise: Promise[Throwable, JFullHttpResponse],
    trustStorePath: String,
    trustStorePassword: String,
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
      val init   = ClientChannelInitializer(hand, scheme, trustStorePath, trustStorePassword)

      val jboo = new JBootstrap().channelFactory(cf).group(el).handler(init)
      if (host.isDefined) jboo.remoteAddress(new InetSocketAddress(host.get, port))

      jboo.connect()
    }

  def request(request: Request, trustStorePath: String, trustStorePassword: String): Task[UHttpResponse] = for {
    promise <- Promise.make[Throwable, JFullHttpResponse]
    jReq = encodeRequest(JHttpVersion.HTTP_1_1, request)
    _    <- asyncRequest(request, jReq, promise, trustStorePath, trustStorePassword)
      .catchAll(cause => promise.fail(cause))
      .fork
    jRes <- promise.await
    res  <- ZIO.fromEither(decodeJResponse(jRes))
  } yield res
}

object Client {
  def make: ZIO[EventLoopGroup with ChannelFactory, Nothing, Client] = for {
    cf <- ZIO.access[ChannelFactory](_.get)
    el <- ZIO.access[EventLoopGroup](_.get)
    zx <- UnsafeChannelExecutor.make[Any]
  } yield service.Client(zx, cf, el)

  def request(url: String): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] = for {
    url <- ZIO.fromEither(URL.fromString(url))
    res <- request(Method.GET -> url)
  } yield res

  def request(
    url: String,
    trustStorePath: String,
    trustStorePassword: String,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] = for {
    url <- ZIO.fromEither(URL.fromString(url))
    res <- request(Method.GET -> url, trustStorePath, trustStorePassword)
  } yield res

  def request(endpoint: Endpoint): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    request(Request(endpoint))

  def request(
    endpoint: Endpoint,
    trustStorePath: String,
    trustStorePassword: String,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    request(
      Request(endpoint),
      trustStorePath,
      trustStorePassword,
    )

  def request(
    req: Request,
    trustStorePath: String = "",
    trustStorePassword: String = "",
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    make.flatMap(_.request(req, trustStorePath, trustStorePassword))

}
