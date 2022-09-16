package zio.http

import io.netty.channel.{Channel => JChannel, ChannelHandler}
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import io.netty.handler.flow.FlowControlHandler
import zio._
import zio.http.URL.Location
import zio.http.service.ClientSSLHandler.ClientSSLOptions
import zio.http.service._
import zio.http.socket.SocketApp

import scala.collection.mutable

trait Client {
  def request(request: Request): ZIO[Any, Throwable, Response]

  def socket[R](
    url: String,
    app: SocketApp[R],
    headers: Headers = Headers.empty,
    addZioUserAgentHeader: Boolean = false,
  )(implicit unsafe: Unsafe): ZIO[R with Scope, Throwable, Response]

}

object Client {
  final case class ClientLive(
    settings: ClientConfig,
    runtime: Runtime[Any],
    rtm: HttpRuntime[Any],
    connectionPool: ConnectionPool,
  ) extends Client
      with ClientRequestEncoder {

    override def request(request: Request): ZIO[Any, Throwable, Response] =
      requestAsync(request, settings)

    override def socket[R](url: String, app: SocketApp[R], headers: Headers, addZioUserAgentHeader: Boolean)(implicit
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

      try {
        req.url.kind match {
          case location: Location.Absolute =>
            for {
              channelScope <- Scope.make
              channel      <-
                connectionPool
                  .get(
                    location,
                    clientConfig.proxy,
                    clientConfig.ssl.getOrElse(ClientSSLOptions.DefaultSSL),
                  )
                  .provideEnvironment(ZEnvironment(channelScope))
              onComplete   <- Promise.make[Throwable, Unit]
              release      <- ZIO.attempt {
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
              }
              // TODO: cleanup, ensure channel is always released, invalidate channel in pool in case of error
              _            <- onComplete.await.exit.flatMap { exit =>
                (release.catchAll(onResponse.fail) *>
                  channelScope.close(exit)).uninterruptible
              }.forkDaemon
            } yield ()
          case Location.Relative           =>
            throw new IllegalArgumentException("Absolute URL is required")
        }
      } catch {
        case err: Throwable =>
          if (jReq.refCnt() > 0) {
            jReq.release(jReq.refCnt()): Unit
          }
          throw err
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
    ): ZIO[Any, Throwable, Unit] = {
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

        if (!location.scheme.isWebSocket) {
          // NOTE: _something_ seems to remove the HttpObjectAggregator on websocket upgrade but not clear what
          toRemove.add(httpObjectAggregator)

          // NOTE: in case of websocket connections the handler removes itself (refactor?)
          toRemove.add(clientInbound)
        }
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
      }

      val frozenToRemove = toRemove.toSet
      ZIO.attempt {
        frozenToRemove.foreach(pipeline.remove)

        // TODO: upgrade to websocket removes the HttpClientCodec from the pipeline, re-add it or mark the channel invalid?
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

  val live: ZLayer[ClientConfig with ConnectionPool, Throwable, Client] =
    ZLayer {
      for {
        settings       <- ZIO.service[ClientConfig]
        runtime        <- ZIO.runtime[Any]
        zx             <- HttpRuntime.default[Any]
        connectionPool <- ZIO.service[ConnectionPool]
      } yield ClientLive(settings, runtime, zx, connectionPool)
    }

  val default = ClientConfig.default >+> ConnectionPool.disabled >>> live

  val zioHttpVersion: CharSequence           = Client.getClass().getPackage().getImplementationVersion()
  val zioHttpVersionNormalized: CharSequence = Option(zioHttpVersion).getOrElse("")

  val scalaVersion: CharSequence   = util.Properties.versionString
  val userAgentValue: CharSequence = s"Zio-Http-Client ${zioHttpVersionNormalized} Scala $scalaVersion"
  val defaultUAHeader: Headers     = Headers(HeaderNames.userAgent, userAgentValue)

  private[zio] val log = Log.withTags("Client")

}
