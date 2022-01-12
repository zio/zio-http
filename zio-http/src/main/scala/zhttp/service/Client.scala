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
import zhttp.service.Client.{ClientParams, ClientResponse}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.{ClientChannelInitializer, ClientInboundHandler}
import zio.{Chunk, Promise, Task, ZIO}

import java.net.{InetAddress, InetSocketAddress}

final case class Client(rtm: HttpRuntime[Any], cf: JChannelFactory[Channel], el: JEventLoopGroup)
    extends HttpMessageCodec {
  def request(
    request: Client.ClientParams,
    sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): Task[Client.ClientResponse] =
    for {
      promise <- Promise.make[Throwable, Client.ClientResponse]
      _       <- Task(asyncRequest(request, promise, sslOption)).catchAll(cause => promise.fail(cause))
      res     <- promise.await
    } yield res

  private def asyncRequest(
    req: ClientParams,
    promise: Promise[Throwable, ClientResponse],
    sslOption: ClientSSLOptions,
  ): Unit = {
    val jReq = encodeClientParams(HttpVersion.HTTP_1_1, req)
    try {
      val hand   = ClientInboundHandler(rtm, jReq, promise)
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
    cf <- ZIO.service[ChannelFactory]
    el <- ZIO.service[EventLoopGroup]
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
    request(ClientParams(method, url))

  def request(
    method: Method,
    url: URL,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientParams(method, url), sslOptions)

  def request(
    method: Method,
    url: URL,
    headers: Headers,
    sslOptions: ClientSSLOptions,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientParams(method, url, headers), sslOptions)

  def request(
    method: Method,
    url: URL,
    headers: Headers,
    content: HttpData,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    request(ClientParams(method, url, headers, content))

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
    method: Method,
    url: URL,
    getHeaders: Headers = Headers.empty,
    data: HttpData = HttpData.empty,
    private val channelContext: ChannelHandlerContext = null,
  ) extends HeaderExtension[ClientParams] { self =>

    def getBodyAsString: Option[String] = data match {
      case HttpData.Text(text, _)       => Some(text)
      case HttpData.BinaryChunk(data)   => Some(new String(data.toArray, HTTP_CHARSET))
      case HttpData.BinaryByteBuf(data) => Some(data.toString(HTTP_CHARSET))
      case _                            => Option.empty
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
    override def updateHeaders(update: Headers => Headers): ClientParams =
      self.copy(getHeaders = update(self.getHeaders))
  }

  final case class ClientResponse(status: Status, headers: Headers, private val buffer: ByteBuf)
      extends HeaderExtension[ClientResponse] { self =>

    def getBodyAsString: Task[String] = Task(buffer.toString(self.getCharset))

    def getBody: Task[Chunk[Byte]] = Task(Chunk.fromArray(ByteBufUtil.getBytes(buffer)))

    override def getHeaders: Headers = headers

    override def updateHeaders(update: Headers => Headers): ClientResponse = self.copy(headers = update(headers))
  }
}
