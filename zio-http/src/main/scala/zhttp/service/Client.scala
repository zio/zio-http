package zhttp.service

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{ByteBuf, ByteBufUtil, Unpooled}
import io.netty.channel.{
  Channel,
  ChannelFactory => JChannelFactory,
  ChannelFuture => JChannelFuture,
  ChannelHandlerContext,
  ChannelInitializer,
  EventLoopGroup => JEventLoopGroup,
}
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import zhttp.http._
import zhttp.http.headers.HeaderExtension
import zhttp.service
import zhttp.service.Client.{ClientRequest, ClientResponse}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.{ClientInboundHandler, ClientSSLHandler}
import zhttp.socket.{Socket, SocketApp}
import zio.{Chunk, Promise, Task, ZIO}

import java.net.{InetAddress, InetSocketAddress, URI}

final case class Client[R](rtm: HttpRuntime[R], cf: JChannelFactory[Channel], el: JEventLoopGroup)
    extends HttpMessageCodec {

  def request(request: Client.ClientRequest): Task[Client.ClientResponse] =
    for {
      promise <- Promise.make[Throwable, Client.ClientResponse]
      jReq    <- encode(request)
      _       <- ChannelFuture
        .unit(unsafeRequest(request, jReq, promise))
        .catchAll(cause => promise.fail(cause))
      res     <- promise.await
    } yield res

  def socket(
    url: URL,
    headers: Headers = Headers.empty,
    socketApp: SocketApp[R],
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[R, Throwable, ClientResponse] = for {
    env <- ZIO.environment[R]
    res <- request(
      ClientRequest(
        url,
        Method.GET,
        headers,
        attribute = Client.Attribute(socketApp = Some(socketApp.provide(env)), ssl = Some(sslOptions)),
      ),
    )
  } yield res

  /**
   * It handles both - Websocket and HTTP requests.
   */
  private def unsafeRequest(
    req: ClientRequest,
    jReq: FullHttpRequest,
    promise: Promise[Throwable, ClientResponse],
  ): JChannelFuture = {

    try {
      val uri  = new URI(jReq.uri())
      val host = if (uri.getHost == null) jReq.headers().get(HeaderNames.host) else uri.getHost

      assert(host != null, "Host name is required")

      val port = req.url.port.getOrElse(80)

      val isWebSocket = req.url.scheme.exists(_.isWebSocket)
      val isSSL       = req.url.scheme.exists(_.isSecure)

      val initializer = new ChannelInitializer[Channel]() {
        override def initChannel(ch: Channel): Unit = {

          val pipeline                    = ch.pipeline()
          val sslOption: ClientSSLOptions = req.attribute.ssl.getOrElse(ClientSSLOptions.DefaultSSL)

          // If a https or wss request is made we need to add the ssl handler at the starting of the pipeline.
          if (isSSL) pipeline.addLast(SSL_HANDLER, ClientSSLHandler.ssl(sslOption).newHandler(ch.alloc, host, port))

          // Adding default client channel handlers
          pipeline.addLast(HTTP_CLIENT_CODEC, new HttpClientCodec)

          // ObjectAggregator is used to work with FullHttpRequests and FullHttpResponses
          // This is also required to make WebSocketHandlers work
          pipeline.addLast(HTTP_OBJECT_AGGREGATOR, new HttpObjectAggregator(Int.MaxValue))

          // ClientInboundHandler is used to take ClientResponse from FullHttpResponse
          pipeline.addLast(CLIENT_INBOUND_HANDLER, new ClientInboundHandler(rtm, jReq, promise, isWebSocket))

          // Add WebSocketHandlers if it's a `ws` or `wss` request
          if (isWebSocket) {
            val headers = req.headers.encode
            val app     = req.attribute.socketApp.getOrElse(Socket.empty.toSocketApp)
            val config  = app.protocol.clientBuilder
              .customHeaders(headers)
              .webSocketUri(req.url.encode)
              .build()

            // Handles the heavy lifting required to upgrade the connection to a WebSocket connection
            pipeline.addLast(WEB_SOCKET_CLIENT_PROTOCOL_HANDLER, new WebSocketClientProtocolHandler(config))
            pipeline.addLast(WEB_SOCKET_HANDLER, new WebSocketAppHandler(rtm, app))
          }
          ()
        }
      }

      val jBoo = new Bootstrap().channelFactory(cf).group(el).handler(initializer)

      jBoo.remoteAddress(new InetSocketAddress(host, port))

      jBoo.connect()
    } catch {
      case err: Throwable =>
        if (jReq.refCnt() > 0) {
          jReq.release(jReq.refCnt()): Unit
        }
        throw err
    }
  }
}

object Client {
  def make[R]: ZIO[R with EventLoopGroup with ChannelFactory, Nothing, Client[R]] = for {
    cf <- ZIO.service[JChannelFactory[Channel]]
    el <- ZIO.service[JEventLoopGroup]
    zx <- HttpRuntime.default[R]
  } yield service.Client(zx, cf, el)

  def request(
    url: String,
    method: Method = Method.GET,
    headers: Headers = Headers.empty,
    content: HttpData = HttpData.empty,
    ssl: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    for {
      uri <- ZIO.fromEither(URL.fromString(url))
      res <- request(ClientRequest(uri, method, headers, content, attribute = Attribute(ssl = Some(ssl))))
    } yield res

  def request(
    request: ClientRequest,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, ClientResponse] =
    for {
      clt <- make[Any]
      res <- clt.request(request)
    } yield res

  def socket[R](
    url: String,
    app: SocketApp[R],
    headers: Headers = Headers.empty,
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[R with EventLoopGroup with ChannelFactory, Throwable, ClientResponse] = {
    for {
      clt <- make[R]
      uri <- ZIO.fromEither(URL.fromString(url))
      res <- clt.socket(uri, headers, app, sslOptions)
    } yield res
  }

  final case class ClientRequest(
    url: URL,
    method: Method = Method.GET,
    headers: Headers = Headers.empty,
    private[zhttp] val data: HttpData = HttpData.empty,
    private[zhttp] val version: Version = Version.Http_1_1,
    private[zhttp] val attribute: Attribute = Attribute.empty,
    private val channelContext: ChannelHandlerContext = null,
  ) extends HeaderExtension[ClientRequest] {
    self =>

    def bodyAsString: Task[String] = bodyAsByteBuf.map(_.toString(headers.charset))

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
      self.copy(headers = update(self.headers))

    private[zhttp] def bodyAsByteBuf: Task[ByteBuf] = data.toByteBuf
  }

  final case class ClientResponse(status: Status, headers: Headers, private[zhttp] val buffer: ByteBuf)
      extends HeaderExtension[ClientResponse] {
    self =>

    def body: Task[Chunk[Byte]] = Task(Chunk.fromArray(ByteBufUtil.getBytes(buffer)))

    def bodyAsByteBuf: Task[ByteBuf] = Task(buffer)

    def bodyAsString: Task[String] = Task(buffer.toString(self.charset))

    override def updateHeaders(update: Headers => Headers): ClientResponse = self.copy(headers = update(headers))
  }

  case class Attribute(socketApp: Option[SocketApp[Any]] = None, ssl: Option[ClientSSLOptions] = None) { self =>
    def withSSL(ssl: ClientSSLOptions): Attribute           = self.copy(ssl = Some(ssl))
    def withSocketApp(socketApp: SocketApp[Any]): Attribute = self.copy(socketApp = Some(socketApp))
  }

  object ClientResponse {
    private[zhttp] def unsafeFromJResponse(jRes: FullHttpResponse): ClientResponse = {
      val status  = Status.fromHttpResponseStatus(jRes.status())
      val headers = Headers.decode(jRes.headers())
      val content = Unpooled.copiedBuffer(jRes.content())
      Client.ClientResponse(status, headers, content)
    }
  }

  object Attribute {
    def empty: Attribute = Attribute()
  }
}
