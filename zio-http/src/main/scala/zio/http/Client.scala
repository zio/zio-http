package zio.http

import io.netty.channel.{Channel => JChannel, ChannelHandler}
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import io.netty.handler.flow.FlowControlHandler
import zio._
import zio.http.URL.Location
import zio.http.model._
import zio.http.model.headers.Headers
import zio.http.netty.client.ClientSSLHandler.ClientSSLOptions
import zio.http.netty.client._
import zio.http.netty.{NettyRuntime, _}
import zio.http.service._
import zio.http.socket.SocketApp

import scala.collection.mutable

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

  def header(key: String, value: String): Client =
    copy(headers = headers ++ Headers.Header(key, value))

  def host(host: String): Client =
    copy(hostOption = Some(host))

  def path(segment: String): Client =
    copy(pathPrefix = pathPrefix / segment)

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
    runtime: Runtime[Any],
    rtm: NettyRuntime,
    connectionPool: ConnectionPool,
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
        port     <- ZIO.fromOption(portOption).orElseSucceed(80)
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
          Request(
            version = Version.Http_1_1,
            Method.GET,
            uri,
            headers.combineIf(addZioUserAgentHeader)(Client.defaultUAHeader),
          ),
          clientConfig = settings.copy(socketApp = Some(app.provideEnvironment(env))),
        ).withFinalizer(_.close.orDie)
      } yield res

    private def requestAsync(request: Request, clientConfig: ClientConfig): ZIO[Any, Throwable, Response] =
      for {
        onResponse <- Promise.make[Throwable, Response]
        jReq       <- encode(request)
        _          <- internalRequest(request, jReq, onResponse, clientConfig)(Unsafe.unsafe)
          .catchAll(cause => onResponse.fail(cause))
        res        <- onResponse.await
      } yield res

    /**
     * It handles both - Websocket and HTTP requests.
     */
    private def internalRequest(
      req: Request,
      jReq: FullHttpRequest,
      onResponse: Promise[Throwable, Response],
      clientConfig: ClientConfig,
    )(implicit unsafe: Unsafe): ZIO[Any, Throwable, Unit] = {
      req.url.kind match {
        case location: Location.Absolute =>
          for {
            onComplete   <- Promise.make[Throwable, Unit]
            channelScope <- Scope.make
            _            <- ZIO.uninterruptibleMask { restore =>
              for {
                channel      <-
                  restore {
                    connectionPool
                      .get(
                        location,
                        clientConfig.proxy,
                        clientConfig.ssl.getOrElse(ClientSSLOptions.DefaultSSL),
                      )
                      .provideEnvironment(ZEnvironment(channelScope))
                  }
                resetChannel <- ZIO.attempt {
                  requestOnChannel(
                    channel,
                    location,
                    req,
                    jReq,
                    onResponse,
                    onComplete,
                    clientConfig.useAggregator,
                    () => clientConfig.socketApp.getOrElse(SocketApp()),
                  )
                }.tapErrorCause(cause => channelScope.close(Exit.failCause(cause)))
                // If request registration failed we release the channel immediately.
                // Otherwise we wait for completion signal from netty in a background fiber:
                _            <- onComplete.await.exit.flatMap { exit =>
                  resetChannel
                    .catchAll(_ => ZIO.succeed(false)) // In case resetting the channel fails we cannot reuse it
                    .flatMap { channelIsReusable =>
                      connectionPool
                        .invalidate(channel)
                        .when(!channelIsReusable || exit.isFailure)
                    }
                    .flatMap { _ =>
                      channelScope.close(exit)
                    }
                    .uninterruptible
                }.forkDaemon
              } yield ()
            }
          } yield ()
        case Location.Relative           =>
          ZIO.fail(throw new IllegalArgumentException("Absolute URL is required"))
      }
    }.tapError { _ =>
      ZIO.attempt {
        if (jReq.refCnt() > 0) {
          jReq.release(jReq.refCnt()): Unit
        }
      }
    }

    private def requestOnChannel(
      channel: JChannel,
      location: URL.Location.Absolute,
      req: Request,
      jReq: FullHttpRequest,
      onResponse: Promise[Throwable, Response],
      onComplete: Promise[Throwable, Unit],
      useAggregator: Boolean,
      createSocketApp: () => SocketApp[Any],
    ): ZIO[Any, Throwable, Boolean] = {
      log.debug(s"Request: [${jReq.method().asciiName()} ${req.url.encode}]")

      val pipeline                              = channel.pipeline()
      val toRemove: mutable.Set[ChannelHandler] = new mutable.HashSet[ChannelHandler]()

      // ObjectAggregator is used to work with FullHttpRequests and FullHttpResponses
      // This is also required to make WebSocketHandlers work
      if (useAggregator) {
        val httpObjectAggregator = new HttpObjectAggregator(Int.MaxValue)
        val clientInbound = new ClientInboundHandler(rtm, jReq, onResponse, onComplete, location.scheme.isWebSocket)
        pipeline.addLast(HTTP_OBJECT_AGGREGATOR, httpObjectAggregator)
        pipeline.addLast(CLIENT_INBOUND_HANDLER, clientInbound)

        toRemove.add(httpObjectAggregator)
        toRemove.add(clientInbound)
      } else {
        val flowControl   = new FlowControlHandler()
        val clientInbound = new ClientInboundStreamingHandler(rtm, req, onResponse, onComplete)

        pipeline.addLast(FLOW_CONTROL_HANDLER, flowControl)
        pipeline.addLast(CLIENT_INBOUND_HANDLER, clientInbound)

        toRemove.add(flowControl)
        toRemove.add(clientInbound)
      }

      // Add WebSocketHandlers if it's a `ws` or `wss` request
      if (location.scheme.isWebSocket) {
        val headers = req.headers.encode
        val app     = createSocketApp()
        val config  = app.protocol.clientBuilder
          .customHeaders(headers)
          .webSocketUri(req.url.encode)
          .build()

        // Handles the heavy lifting required to upgrade the connection to a WebSocket connection

        val webSocketClientProtocol = new WebSocketClientProtocolHandler(config)
        val webSocket               = new WebSocketAppHandler(rtm, app, true)

        pipeline.addLast(WEB_SOCKET_CLIENT_PROTOCOL_HANDLER, webSocketClientProtocol)
        pipeline.addLast(WEB_SOCKET_HANDLER, webSocket)

        toRemove.add(webSocketClientProtocol)
        toRemove.add(webSocket)

        pipeline.fireChannelRegistered()
        pipeline.fireChannelActive()

        ZIO.succeed(false) // channel becomes invalid - reuse of websocket channels not supported currently
      } else {

        pipeline.fireChannelRegistered()
        pipeline.fireChannelActive()

        val frozenToRemove = toRemove.toSet

        ZIO.attempt {
          frozenToRemove.foreach(pipeline.remove)
          true // channel can be reused
        }
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
          Request(
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

  val live: ZLayer[ClientConfig with ConnectionPool with NettyRuntime, Throwable, Client] =
    ZLayer {
      for {
        settings       <- ZIO.service[ClientConfig]
        runtime        <- ZIO.runtime[Any]
        zx             <- ZIO.service[NettyRuntime]
        connectionPool <- ZIO.service[ConnectionPool]
      } yield ClientLive(settings, runtime, zx, connectionPool)
    }

  val default =
    ClientConfig.default >+> ConnectionPool.disabled >+> NettyRuntime.usingDedicatedThreadPool >>> live

  val zioHttpVersion: CharSequence           = Client.getClass().getPackage().getImplementationVersion()
  val zioHttpVersionNormalized: CharSequence = Option(zioHttpVersion).getOrElse("")

  val scalaVersion: CharSequence   = util.Properties.versionString
  val userAgentValue: CharSequence = s"Zio-Http-Client ${zioHttpVersionNormalized} Scala $scalaVersion"
  val defaultUAHeader: Headers     = Headers(HeaderNames.userAgent, userAgentValue)

  private[zio] val log = Log.withTags("Client")

}
