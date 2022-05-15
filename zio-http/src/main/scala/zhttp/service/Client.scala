package zhttp.service

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{
  Channel,
  ChannelFactory => JChannelFactory,
  ChannelFuture => JChannelFuture,
  ChannelInitializer,
  EventLoopGroup => JEventLoopGroup,
}
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import zhttp.http._
import zhttp.service
import zhttp.service.Client.Config
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.{ClientInboundHandler, ClientSSLHandler}
import zhttp.socket.{Socket, SocketApp}
import zio.{Promise, Task, ZIO, ZManaged}

import java.net.{InetSocketAddress, URI}

final case class Client[R](rtm: HttpRuntime[R], cf: JChannelFactory[Channel], el: JEventLoopGroup)
    extends HttpMessageCodec {

  def request(request: Request, clientConfig: Config): Task[Response] =
    for {
      promise <- Promise.make[Throwable, Response]
      jReq    <- encode(request)
      _       <- ChannelFuture
        .unit(unsafeRequest(request, clientConfig, jReq, promise))
        .catchAll(cause => promise.fail(cause))
      res     <- promise.await
    } yield res

  def socket(
    url: URL,
    headers: Headers = Headers.empty,
    socketApp: SocketApp[R],
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZManaged[R, Throwable, Response] = for {
    env <- ZManaged.environment[R]
    res <- request(
      Request(
        version = Version.Http_1_1,
        Method.GET,
        url,
        headers,
      ),
      clientConfig = Client.Config(socketApp = Some(socketApp.provideEnvironment(env)), ssl = Some(sslOptions)),
    ).toManaged(_.close.orDie)
  } yield res

  /**
   * It handles both - Websocket and HTTP requests.
   */
  private def unsafeRequest(
    req: Request,
    clientConfig: Config,
    jReq: FullHttpRequest,
    promise: Promise[Throwable, Response],
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
          val sslOption: ClientSSLOptions = clientConfig.ssl.getOrElse(ClientSSLOptions.DefaultSSL)

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
            val app     = clientConfig.socketApp.getOrElse(Socket.empty.toSocketApp)
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
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, Response] =
    for {
      uri <- ZIO.fromEither(URL.fromString(url))
      res <- request(
        Request(Version.Http_1_1, method, uri, headers, data = content),
        clientConfig = Config(ssl = Some(ssl)),
      )
    } yield res

  def request(
    request: Request,
    clientConfig: Config,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, Response] =
    for {
      clt <- make[Any]
      res <- clt.request(request, clientConfig)
    } yield res

  def socket[R](
    url: String,
    app: SocketApp[R],
    headers: Headers = Headers.empty,
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZManaged[R with EventLoopGroup with ChannelFactory, Throwable, Response] = {
    for {
      clt <- make[R].toManaged_
      uri <- ZIO.fromEither(URL.fromString(url)).toManaged_
      res <- clt.socket(uri, headers, app, sslOptions)
    } yield res
  }

  case class Config(socketApp: Option[SocketApp[Any]] = None, ssl: Option[ClientSSLOptions] = None) { self =>
    def withSSL(ssl: ClientSSLOptions): Config           = self.copy(ssl = Some(ssl))
    def withSocketApp(socketApp: SocketApp[Any]): Config = self.copy(socketApp = Some(socketApp))
  }

  object Config {
    def empty: Config = Config()
  }
}
