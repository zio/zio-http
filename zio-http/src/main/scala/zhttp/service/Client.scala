package zhttp.service

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.channel.{
  Channel,
  ChannelFactory => JChannelFactory,
  ChannelHandlerContext,
  EventLoopGroup => JEventLoopGroup,
}
import io.netty.handler.codec.http.HttpVersion
import zhttp.http.URL.Location
import zhttp.http._
import zhttp.http.headers.HeaderExtension
import zhttp.service
import zhttp.service.Client.{ClientRequest, ClientResponse}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.{ClientChannelInitializer, ClientInboundHandler}
import zio.{Chunk, Promise, Task, ZIO}

import java.net.{InetAddress, InetSocketAddress}

final case class Client(rtm: HttpRuntime[Any], cf: JChannelFactory[Channel], el: JEventLoopGroup)
    extends HttpMessageCodec {
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
  def make: ZIO[EventLoopGroup with ChannelFactory, Nothing, Client] = for {
    cf <- ZIO.access[ChannelFactory](_.get)
    el <- ZIO.access[EventLoopGroup](_.get)
    zx <- HttpRuntime.default[Any]
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
