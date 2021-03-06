package zhttp.service

import zhttp.core._
import zhttp.http.{Request, Response}
import zhttp.service
import zhttp.service.client.{ClientChannelInitializer, ClientHttpChannelReader, ClientInboundHandler}
import zio.{Promise, Task, ZIO}

import java.net.InetSocketAddress

final case class Client(zx: UnsafeChannelExecutor[Any], cf: JChannelFactory[JChannel], el: JEventLoopGroup) {
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
      val port = req.url.port

      val jboo = new JBootstrap().channelFactory(cf).group(el).handler(init)
      if (host.isDefined) jboo.remoteAddress(new InetSocketAddress(host.get, port.getOrElse(80)))

      jboo.connect()
    }

  def request(request: Request): Task[Response] = for {
    promise <- Promise.make[Throwable, JFullHttpResponse]
    jReq    <- request.asJFullHttpRequest
    _       <- asyncRequest(request, jReq, promise).catchAll(cause => promise.fail(cause)).fork
    jRes    <- promise.await
    res     <- Response.fromJFullHttpResponse(jRes)
  } yield res
}

object Client {
  def make: ZIO[EventLoopGroup with ChannelFactory, Nothing, Client] = for {
    cf <- ZIO.access[ChannelFactory](_.get)
    el <- ZIO.access[EventLoopGroup](_.get)
    zx <- UnsafeChannelExecutor.make[Any]
  } yield service.Client(zx, cf, el)

  def request(req: Request): ZIO[EventLoopGroup with ChannelFactory, Throwable, Response] =
    make.flatMap(_.request(req))
}
