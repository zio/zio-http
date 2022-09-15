package zio.http

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{
  Channel => JChannel,
  ChannelFactory => JChannelFactory,
  ChannelFuture => JChannelFuture,
  ChannelInitializer,
  EventLoopGroup => JEventLoopGroup,
}
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import io.netty.handler.proxy.HttpProxyHandler
import zio._
import zio.http.Client.log
import zio.http.service.ClientSSLHandler.ClientSSLOptions
import zio.http.service._
import zio.http.socket.SocketApp

import java.net.InetSocketAddress

final case class Client(rtm: HttpRuntime[Any], cf: JChannelFactory[JChannel], el: JEventLoopGroup)
    extends ClientRequestEncoder {

  def request(
    url: String,
    method: Method = Method.GET,
    headers: Headers = Headers.empty,
    content: Body = Body.empty,
    ssl: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): Task[Response] =
    for {
      uri <- ZIO.fromEither(URL.fromString(url))
      res <- request(
        Request(Version.Http_1_1, method, uri, headers, body = content),
        clientConfig = ClientConfig(ssl = Some(ssl)),
      )
    } yield res

  def request(request: Request, clientConfig: ClientConfig): Task[Response] =
    for {
      promise <- Promise.make[Throwable, Response]
      jReq    <- encode(request)
      _       <- ChannelFuture
        .unit(this.request(request, clientConfig, jReq, promise)(Unsafe.unsafe))
        .catchAll(cause => promise.fail(cause))
      res     <- promise.await
    } yield res

  def socket[R](
    url: URL,
    headers: Headers = Headers.empty,
    socketApp: SocketApp[R],
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[R with Scope, Throwable, Response] = for {
    env <- ZIO.environment[R]
    res <- request(
      Request(
        version = Version.Http_1_1,
        Method.GET,
        url,
        headers,
      ),
      clientConfig = ClientConfig(socketApp = Some(socketApp.provideEnvironment(env)), ssl = Some(sslOptions)),
    ).withFinalizer(_.close.orDie)
  } yield res

  /**
   * It handles both - Websocket and HTTP requests.
   */
  private def request(
    req: Request,
    clientConfig: ClientConfig,
    jReq: FullHttpRequest,
    promise: Promise[Throwable, Response],
  )(implicit unsafe: Unsafe): JChannelFuture = {

    try {
      val host = req.url.host.getOrElse { assert(false, "Host name is required"); "" }
      val port = req.url.port.getOrElse(80)

      val isWebSocket = req.url.scheme.exists(_.isWebSocket)
      val isSSL       = req.url.scheme.exists(_.isSecure)
      val isProxy     = clientConfig.proxy.isDefined

      log.debug(s"Request: [${jReq.method().asciiName()} ${req.url.encode}]")
      val initializer = new ChannelInitializer[JChannel]() {
        override def initChannel(ch: JChannel): Unit = {

          val pipeline                    = ch.pipeline()
          val sslOption: ClientSSLOptions = clientConfig.ssl.getOrElse(ClientSSLOptions.DefaultSSL)

          // Adding proxy handler
          if (isProxy) {
            val handler: HttpProxyHandler =
              clientConfig.proxy
                .flatMap(_.encode)
                .getOrElse(new HttpProxyHandler(new InetSocketAddress(host, port)))

            pipeline.addLast(
              PROXY_HANDLER,
              handler,
            )
          }

          // If a https or wss request is made we need to add the ssl handler at the starting of the pipeline.
          if (isSSL) pipeline.addLast(SSL_HANDLER, ClientSSLHandler.ssl(sslOption).newHandler(ch.alloc, host, port))

          // Adding default client channel handlers
          // Defaults from netty:
          //   maxInitialLineLength=4096
          //   maxHeaderSize=8192
          //   maxChunkSize=8192
          // and we add: failOnMissingResponse=true
          // This way, if the server closes the connection before the whole response has been sent,
          // we get an error. (We can also handle the channelInactive callback, but since for now
          // we always buffer the whole HTTP response we can letty Netty take care of this)
          pipeline.addLast(HTTP_CLIENT_CODEC, new HttpClientCodec(4096, 8192, 8192, true))

          // ObjectAggregator is used to work with FullHttpRequests and FullHttpResponses
          // This is also required to make WebSocketHandlers work
          pipeline.addLast(HTTP_OBJECT_AGGREGATOR, new HttpObjectAggregator(Int.MaxValue))

          // ClientInboundHandler is used to take ClientResponse from FullHttpResponse
          pipeline.addLast(CLIENT_INBOUND_HANDLER, new ClientInboundHandler(rtm, jReq, promise, isWebSocket))

          // Add WebSocketHandlers if it's a `ws` or `wss` request
          if (isWebSocket) {
            val headers = req.headers.encode
            val app     = clientConfig.socketApp.getOrElse(SocketApp())
            val config  = app.protocol.clientBuilder
              .customHeaders(headers)
              .webSocketUri(req.url.encode)
              .build()

            // Handles the heavy lifting required to upgrade the connection to a WebSocket connection
            pipeline.addLast(WEB_SOCKET_CLIENT_PROTOCOL_HANDLER, new WebSocketClientProtocolHandler(config))
            pipeline.addLast(WEB_SOCKET_HANDLER, new WebSocketAppHandler(rtm, app, true))
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
  def make: ZIO[EventLoopGroup with ChannelFactory, Nothing, Client] = for {
    cf <- ZIO.service[JChannelFactory[JChannel]]
    el <- ZIO.service[JEventLoopGroup]
    zx <- HttpRuntime.default[Any]
  } yield http.Client(zx, cf, el)

  def request(
    url: String,
    method: Method = Method.GET,
    headers: Headers = Headers.empty,
    content: Body = Body.empty,
    ssl: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, Response] =
    for {
      clt <- make
      res <- clt.request(url, method, headers, content, ssl)
    } yield res

  def request(
    request: Request,
    clientConfig: ClientConfig,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, Response] =
    for {
      clt <- make
      res <- clt.request(request, clientConfig)
    } yield res

  def socket[R](
    url: String,
    app: SocketApp[R],
    headers: Headers = Headers.empty,
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[R with EventLoopGroup with ChannelFactory with Scope, Throwable, Response] = {
    for {
      clt <- make
      uri <- ZIO.fromEither(URL.fromString(url))
      res <- clt.socket(uri, headers, app, sslOptions)
    } yield res
  }

  private[zio] val log = Log.withTags("Client")
}
