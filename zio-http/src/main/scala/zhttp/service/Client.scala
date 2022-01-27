package zhttp.service

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup}
import io.netty.handler.codec.http.{FullHttpRequest, HttpVersion}
import zhttp.http.URL.Location
import zhttp.http._
import zhttp.http.headers.HeaderExtension
import zhttp.service
import zhttp.service.Client.ClientResponse
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.content.handlers.ClientResponseHandler
import zhttp.service.client.{ClientChannelInitializer, ClientInboundHandler, SocketClient}
import zhttp.socket.SocketApp
import zio.{Chunk, Promise, Task, ZIO}

import java.net.InetSocketAddress

final case class Client[R](rtm: HttpRuntime[R], cf: JChannelFactory[Channel], el: JEventLoopGroup)
    extends SocketClient(rtm, cf, el)
    with HttpMessageCodec {

  def request(
    request: Request,
    sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): Task[Client.ClientResponse] =
    for {
      promise <- Promise.make[Throwable, Client.ClientResponse]
      jReq    <- encodeClientParams(HttpVersion.HTTP_1_1, request)
      _       <- Task(asyncRequest(request, jReq, promise, sslOption)).catchAll(cause => promise.fail(cause))
      res     <- promise.await
    } yield res

  def socket(
    url: String,
    headers: Headers = Headers.empty,
    sa: SocketApp[R],
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[Any, Throwable, ClientResponse] = for {
    pr  <- Promise.make[Throwable, ClientResponse]
    _   <- Task(unsafeSocket(url, headers, sa, pr, sslOptions))
    res <- pr.await
  } yield res

  private def asyncRequest(
    req: Request,
    jReq: FullHttpRequest,
    promise: Promise[Throwable, ClientResponse],
    sslOption: ClientSSLOptions,
  ): Unit = {
    try {
      val hand   = List(new ClientResponseHandler(), ClientInboundHandler(rtm, jReq, promise))
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
  def make[R]: ZIO[R with EventLoopGroup with ChannelFactory, Nothing, Client[R]] = for {
    cf <- ZIO.access[ChannelFactory](_.get)
    el <- ZIO.access[EventLoopGroup](_.get)
    zx <- HttpRuntime.default[R]
  } yield service.Client(zx, cf, el)

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
    request(Request(method, url))

  def request(
    method: Method,
    url: URL,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(Request(method, url), sslOptions)

  def request(
    method: Method,
    url: URL,
    headers: Headers,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(Request(method, url, headers), sslOptions)

  def request(
    method: Method,
    url: URL,
    headers: Headers,
    content: HttpData,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(Request(method, url, headers, content, None))

  def request(
    req: Request,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    make[Any].flatMap(_.request(req))

  def request(
    req: Request,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    make[Any].flatMap(_.request(req, sslOptions))

  def socket[R](
    url: String,
    app: SocketApp[R],
    headers: Headers = Headers.empty,
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[R with EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    make[R].flatMap(_.socket(url, headers, app, sslOptions))

  final case class ClientResponse(status: Status, headers: Headers, private[zhttp] val buffer: ByteBuf)
      extends HeaderExtension[ClientResponse] {
    self =>

    def getBody: Task[Chunk[Byte]] = Task(Chunk.fromArray(ByteBufUtil.getBytes(buffer)))

    def getBodyAsByteBuf: Task[ByteBuf] = Task(buffer)

    def getBodyAsString: Task[String] = Task(buffer.toString(self.getCharset))

    override def getHeaders: Headers = headers

    override def updateHeaders(update: Headers => Headers): ClientResponse = self.copy(headers = update(headers))
  }
}
