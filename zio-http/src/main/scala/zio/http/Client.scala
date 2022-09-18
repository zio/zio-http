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
import io.netty.handler.flow.FlowControlHandler
import io.netty.handler.proxy.HttpProxyHandler
import zio._
import zio.http.model._
import zio.http.model.headers.Headers
import zio.http.netty.client.ClientSSLHandler.ClientSSLOptions
import zio.http.netty.client._
import zio.http.netty.{NettyRuntime, _}
import zio.http.service._
import zio.http.socket.SocketApp

import java.net.InetSocketAddress

trait Client { self =>

  def headers: Headers

  def hostOption: Option[String]

  def pathPrefix: Path

  def portOption: Option[Int]

  def queries: Map[String, Chunk[String]]

  def sslOption: Option[ClientSSLOptions]

  def socket[R](
    url: String,
    app: SocketApp[R],
    headers: Headers = Headers.empty,
    addZioUserAgentHeader: Boolean = false,
  )(implicit unsafe: Unsafe): ZIO[R with Scope, Throwable, Response]

  final def get(pathSuffix: String)(body: Body): Task[Response] =
    request(Method.GET, pathSuffix, body)

  def header(key: String, value: String): Client =
    copy(headers = headers ++ Headers.Header(key, value))

  def host(host: String): Client =
    copy(hostOption = Some(host))

  def path(segment: String): Client =
    copy(pathPrefix = pathPrefix / segment)

  def put(pathSuffix: String)(body: Body): Task[Response] =
    request(Method.PUT, pathSuffix, body)

  def port(port: Int): Client =
    copy(portOption = Some(port))

  def query(key: String, value: String): Client =
    copy(queries = queries + (key -> Chunk(value)))

  final def request(method: Method, pathSuffix: String, body: Body): ZIO[Any, Throwable, Response] =
    requestInternal(
      body,
      headers,
      hostOption,
      method,
      pathPrefix / pathSuffix,
      portOption,
      queries,
      sslOption,
      Version.Http_1_1,
    )

  final def request(request: Request): ZIO[Any, Throwable, Response] = {
    requestInternal(
      request.body,
      headers ++ request.headers,
      request.url.host,
      request.method,
      pathPrefix ++ request.path,
      request.url.port,
      queries ++ request.url.queryParams,
      sslOption,
      request.version,
    )
  }

  def ssl(ssl: ClientSSLOptions): Client =
    copy(sslOption = Some(ssl))

  protected def requestInternal(
    body: Body,
    headers: Headers,
    hostOption: Option[String],
    method: Method,
    pathPrefix: Path,
    portOption: Option[Int],
    queries: Map[String, Chunk[String]],
    sslOption: Option[ClientSSLOptions],
    version: Version,
  ): ZIO[Any, Throwable, Response]

  private def copy(
    headers: Headers = headers,
    hostOption: Option[String] = hostOption,
    pathPrefix: Path = pathPrefix,
    portOption: Option[Int] = portOption,
    queries: Map[String, Chunk[String]] = queries,
    sslOption: Option[ClientSSLOptions] = sslOption,
  ): Client =
    Client.Proxy(self, headers, hostOption, pathPrefix, portOption, queries, sslOption)
}

object Client {

  private final case class Proxy(
    client: Client,
    headers: Headers,
    hostOption: Option[String],
    pathPrefix: Path,
    portOption: Option[Int],
    queries: Map[String, Chunk[String]],
    sslOption: Option[ClientSSLOptions],
  ) extends Client {

    def requestInternal(
      body: Body,
      headers: Headers,
      hostOption: Option[String],
      method: Method,
      path: Path,
      portOption: Option[Int],
      queries: Map[String, Chunk[String]],
      sslOption: Option[ClientSSLOptions],
      version: Version,
    ): ZIO[Any, Throwable, Response] =
      client.requestInternal(body, headers, hostOption, method, path, portOption, queries, sslOption, version)

    def socket[R](
      url: String,
      app: SocketApp[R],
      headers: Headers = Headers.empty,
      addZioUserAgentHeader: Boolean = false,
    )(implicit unsafe: Unsafe): ZIO[R with Scope, Throwable, Response] =
      client.socket(url, app, headers, addZioUserAgentHeader)

  }

  final case class ClientLive(
    settings: ClientConfig,
    rtm: NettyRuntime,
    cf: JChannelFactory[JChannel],
    el: JEventLoopGroup,
  ) extends Client
      with ClientRequestEncoder {
    val headers: Headers                    = Headers.empty
    val hostOption: Option[String]          = None
    val pathPrefix: Path                    = Path.empty
    val portOption: Option[Int]             = None
    val queries: Map[String, Chunk[String]] = Map.empty
    val sslOption: Option[ClientSSLOptions] = None

    def requestInternal(
      body: Body,
      headers: Headers,
      hostOption: Option[String],
      method: Method,
      path: Path,
      portOption: Option[Int],
      queries: Map[String, Chunk[String]],
      sslOption: Option[ClientSSLOptions],
      version: Version,
    ): ZIO[Any, Throwable, Response] = {

      for {
        host     <- ZIO.fromOption(hostOption).orElseFail(new IllegalArgumentException("Host is required"))
        port     <- ZIO.fromOption(portOption).orElseSucceed(sslOption.fold(80)(_ => 443))
        response <- requestAsync(
          Request(
            version = version,
            method = method,
            url = URL(path, URL.Location.Absolute(Scheme.HTTP, host, port)).setQueryParams(queries),
            headers = headers,
            body = body,
          ),
          sslOption.fold(settings)(settings.ssl),
        )
      } yield response
    }

    def socket[R](url: String, app: SocketApp[R], headers: Headers, addZioUserAgentHeader: Boolean)(implicit
      unsafe: Unsafe,
    ): ZIO[R with Scope, Throwable, Response] =
      for {
        env <- ZIO.environment[R]
        uri <- ZIO.fromEither(URL.fromString(url))
        res <- requestAsync(
          http.Request(
            version = Version.Http_1_1,
            Method.GET,
            uri,
            headers.combineIf(addZioUserAgentHeader)(Client.defaultUAHeader),
          ),
          clientConfig = settings.copy(socketApp = Some(app.provideEnvironment(env))),
        ).withFinalizer(_.close.orDie)
      } yield res

    private def requestAsync(request: Request, clientConfig: ClientConfig): ZIO[Any, Throwable, Response] = {
      for {
        promise <- Promise.make[Throwable, Response]
        jReq    <- encode(request)
        _       <- NettyFutureExecutor
          .executed(internalRequest(request, jReq, promise, clientConfig)(Unsafe.unsafe))
          .catchAll(cause => promise.fail(cause))
        res     <- promise.await
      } yield res

    }

    /**
     * It handles both - Websocket and HTTP requests.
     */
    private def internalRequest(
      req: Request,
      jReq: FullHttpRequest,
      promise: Promise[Throwable, Response],
      clientConfig: ClientConfig,
    )(implicit unsafe: Unsafe): JChannelFuture = {

      try {
        val host = req.url.host.getOrElse {
          assert(false, "Host name is required");
          ""
        }
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
            if (clientConfig.useAggregator) {
              pipeline.addLast(HTTP_OBJECT_AGGREGATOR, new HttpObjectAggregator(Int.MaxValue))
              pipeline
                .addLast(
                  CLIENT_INBOUND_HANDLER,
                  new ClientInboundHandler(rtm, jReq, promise, isWebSocket),
                )
            } else {

              // ClientInboundHandler is used to take ClientResponse from FullHttpResponse
              pipeline.addLast(FLOW_CONTROL_HANDLER, new FlowControlHandler())
              pipeline
                .addLast(
                  CLIENT_INBOUND_HANDLER,
                  new ClientInboundStreamingHandler(rtm, req, promise),
                )

            }

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
  def request(
    url: String,
    method: Method = Method.GET,
    headers: Headers = Headers.empty,
    content: Body = Body.empty,
    addZioUserAgentHeader: Boolean = false,
  ): ZIO[Client, Throwable, Response] = {
    for {
      uri      <- ZIO.fromEither(URL.fromString(url))
      response <- ZIO.serviceWithZIO[Client](
        _.request(
          http.Request(
            version = Version.Http_1_1,
            method = method,
            url = uri,
            headers = headers.combineIf(addZioUserAgentHeader)(Client.defaultUAHeader),
            body = content,
          ),
        ),
      )
    } yield response

  }

  def request(
    request: Request,
  ): ZIO[Client, Throwable, Response] = ZIO.serviceWithZIO[Client](_.request(request))

  def socket[R](
    url: String,
    app: SocketApp[R],
    headers: Headers = Headers.empty,
  ): ZIO[R with Client with Scope, Throwable, Response] =
    Unsafe.unsafe { implicit u =>
      ZIO.serviceWithZIO[Client](_.socket(url, app, headers))
    }

  val live: ZLayer[ClientConfig with ChannelFactory with EventLoopGroup with NettyRuntime, Throwable, Client] =
    ZLayer {
      for {
        settings       <- ZIO.service[ClientConfig]
        channelFactory <- ZIO.service[ChannelFactory]
        eventLoopGroup <- ZIO.service[EventLoopGroup]
        zx             <- ZIO.service[NettyRuntime]
      } yield ClientLive(settings, zx, channelFactory, eventLoopGroup)
    }

  val default =
    ClientConfig.default >+> EventLoopGroups.fromConfig >+> ChannelFactories.Client.fromConfig >+> NettyRuntime.usingDedicatedThreadPool >>> live

  val zioHttpVersion: CharSequence           = Client.getClass().getPackage().getImplementationVersion()
  val zioHttpVersionNormalized: CharSequence = Option(zioHttpVersion).getOrElse("")

  val scalaVersion: CharSequence   = util.Properties.versionString
  val userAgentValue: CharSequence = s"Zio-Http-Client ${zioHttpVersionNormalized} Scala $scalaVersion"
  val defaultUAHeader: Headers     = Headers(HeaderNames.userAgent, userAgentValue)

  private[zio] val log = Log.withTags("Client")

}
