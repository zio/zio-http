package zhttp.service

import io.netty.handler.codec.http.{HttpVersion => JHttpVersion}
import zhttp.core._
import zhttp.http._
import zhttp.service
import zhttp.service.client.{ClientChannelInitializer, ClientHttpChannelReader, ClientInboundHandler}
import zio.{Promise, Task, ZIO}

import java.net.InetSocketAddress

final case class Client(zx: UnsafeChannelExecutor[Any], cf: JChannelFactory[JChannel], el: JEventLoopGroup)
    extends HttpMessageCodec {
  private def asyncRequest(
    req: Request,
    jReq: JFullHttpRequest,
    promise: Promise[Throwable, JFullHttpResponse],
  ): Task[Unit] =
    ChannelFuture.unit {
      val read = ClientHttpChannelReader(jReq, promise)
      val hand = ClientInboundHandler(zx, read)
      val init = ClientChannelInitializer(hand)
      val host = req.url.host
      val port = req.url.port.getOrElse(80) match {
        case -1   => 80
        case port => port
      }

      val jboo = new JBootstrap().channelFactory(cf).group(el).handler(init)
      if (host.isDefined) jboo.remoteAddress(new InetSocketAddress(host.get, port))

      jboo.connect()
    }

  def request(request: Request): Task[UHttpResponse] = for {
    promise <- Promise.make[Throwable, JFullHttpResponse]
    jReq    <- encodeRequest(JHttpVersion.HTTP_1_1, request)
    _       <- asyncRequest(request, jReq, promise).catchAll(cause => promise.fail(cause)).fork
    jRes    <- promise.await
    res     <- ZIO.fromEither(decodeJResponse(jRes))
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

  def request(endpoint: Endpoint): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    request(Request(endpoint, headers = List.empty, content = HttpData.Empty))

  def request(req: Request): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    make.flatMap(_.request(req))
}
