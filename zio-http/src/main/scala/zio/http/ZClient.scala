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
import zio.http.netty.client._
import zio.http.netty.{NettyRuntime, _}
import zio.http.service._
import zio.http.socket.SocketApp

import java.net.{InetSocketAddress, URI}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait ZClient[-Env, -In, +Err, +Out] { self =>

  def headers: Headers

  def hostOption: Option[String]

  def pathPrefix: Path

  def portOption: Option[Int]

  def queries: QueryParams

  def schemeOption: Option[Scheme]

  def sslConfig: Option[ClientSSLConfig]

  final def contramap[In2](f: In2 => In): ZClient[Env, In2, Err, Out] =
    contramapZIO(in => ZIO.succeedNow(f(in)))

  final def contramapZIO[Env1 <: Env, Err1 >: Err, In2](f: In2 => ZIO[Env1, Err1, In]): ZClient[Env1, In2, Err1, Out] =
    new ZClient[Env1, In2, Err1, Out] {
      def headers: Headers                   = self.headers
      def hostOption: Option[String]         = self.hostOption
      def pathPrefix: Path                   = self.pathPrefix
      def portOption: Option[Int]            = self.portOption
      def queries: QueryParams               = self.queries
      def schemeOption: Option[Scheme]       = self.schemeOption
      def sslConfig: Option[ClientSSLConfig] = self.sslConfig
      def requestInternal(
        body: In2,
        headers: Headers,
        hostOption: Option[String],
        method: Method,
        pathPrefix: Path,
        portOption: Option[Int],
        queries: QueryParams,
        schemeOption: Option[Scheme],
        sslConfig: Option[ClientSSLConfig],
        version: Version,
      )(implicit trace: Trace): ZIO[Env1, Err1, Out] =
        f(body).flatMap { body =>
          self.requestInternal(
            body,
            headers,
            hostOption,
            method,
            pathPrefix,
            portOption,
            queries,
            schemeOption,
            sslConfig,
            version,
          )
        }
      def socketInternal[Env2 <: Env1](
        app: SocketApp[Env2],
        headers: Headers,
        hostOption: Option[String],
        pathPrefix: Path,
        portOption: Option[Int],
        queries: QueryParams,
        schemeOption: Option[Scheme],
        version: Version,
      )(implicit trace: Trace): ZIO[Env2 with Scope, Err1, Out] =
        self.socketInternal(
          app,
          headers,
          hostOption,
          pathPrefix,
          portOption,
          queries,
          schemeOption,
          version,
        )
    }

  final def dieOn(
    f: Err => Boolean,
  )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): ZClient[Env, In, Err, Out] =
    refineOrDie { case e if !f(e) => e }

  final def get(pathSuffix: String)(body: In)(implicit trace: Trace): ZIO[Env, Err, Out] =
    request(Method.GET, pathSuffix, body)

  final def header(key: String, value: String): ZClient[Env, In, Err, Out] =
    copy(headers = headers ++ Headers.Header(key, value))

  final def host(host: String): ZClient[Env, In, Err, Out] =
    copy(hostOption = Some(host))

  final def map[Out2](f: Out => Out2): ZClient[Env, In, Err, Out2] =
    mapZIO(out => ZIO.succeedNow(f(out)))

  final def mapZIO[Env1 <: Env, Err1 >: Err, Out2](f: Out => ZIO[Env1, Err1, Out2]): ZClient[Env1, In, Err1, Out2] =
    new ZClient[Env1, In, Err1, Out2] {
      def headers: Headers                   = self.headers
      def hostOption: Option[String]         = self.hostOption
      def pathPrefix: Path                   = self.pathPrefix
      def portOption: Option[Int]            = self.portOption
      def queries: QueryParams               = self.queries
      def schemeOption: Option[Scheme]       = self.schemeOption
      def sslConfig: Option[ClientSSLConfig] = self.sslConfig
      def requestInternal(
        body: In,
        headers: Headers,
        hostOption: Option[String],
        method: Method,
        pathPrefix: Path,
        portOption: Option[Int],
        queries: QueryParams,
        schemeOption: Option[Scheme],
        sslConfig: Option[ClientSSLConfig],
        version: Version,
      )(implicit trace: Trace): ZIO[Env1, Err1, Out2] =
        self
          .requestInternal(
            body,
            headers,
            hostOption,
            method,
            pathPrefix,
            portOption,
            queries,
            schemeOption,
            sslConfig,
            version,
          )
          .flatMap(f)
      protected def socketInternal[Env2 <: Env1](
        app: SocketApp[Env2],
        headers: Headers,
        hostOption: Option[String],
        pathPrefix: Path,
        portOption: Option[Int],
        queries: QueryParams,
        schemeOption: Option[Scheme],
        version: Version,
      )(implicit trace: Trace): ZIO[Env2 with Scope, Err1, Out2] =
        self
          .socketInternal(
            app,
            headers,
            hostOption,
            pathPrefix,
            portOption,
            queries,
            schemeOption,
            version,
          )
          .flatMap(f)
    }

  final def path(segment: String): ZClient[Env, In, Err, Out] =
    copy(pathPrefix = pathPrefix / segment)

  final def put(pathSuffix: String)(body: In)(implicit trace: Trace): ZIO[Env, Err, Out] =
    request(Method.PUT, pathSuffix, body)

  final def port(port: Int): ZClient[Env, In, Err, Out] =
    copy(portOption = Some(port))

  def query(key: String, value: String): ZClient[Env, In, Err, Out] =
    copy(queries = queries.add(key, value))

  final def refineOrDie[Err2](
    pf: PartialFunction[Err, Err2],
  )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): ZClient[Env, In, Err2, Out] =
    new ZClient[Env, In, Err2, Out] {
      def headers: Headers                   = self.headers
      def hostOption: Option[String]         = self.hostOption
      def pathPrefix: Path                   = self.pathPrefix
      def portOption: Option[Int]            = self.portOption
      def queries: QueryParams               = self.queries
      def schemeOption: Option[Scheme]       = self.schemeOption
      def sslConfig: Option[ClientSSLConfig] = self.sslConfig
      def requestInternal(
        body: In,
        headers: Headers,
        hostOption: Option[String],
        method: Method,
        pathPrefix: Path,
        portOption: Option[Int],
        queries: QueryParams,
        schemeOption: Option[Scheme],
        sslConfig: Option[ClientSSLConfig],
        version: Version,
      )(implicit trace: Trace): ZIO[Env, Err2, Out] =
        self
          .requestInternal(
            body,
            headers,
            hostOption,
            method,
            pathPrefix,
            portOption,
            queries,
            schemeOption,
            sslConfig,
            version,
          )
          .refineOrDie(pf)
      protected def socketInternal[Env1 <: Env](
        app: SocketApp[Env1],
        headers: Headers,
        hostOption: Option[String],
        pathPrefix: Path,
        portOption: Option[Int],
        queries: QueryParams,
        schemeOption: Option[Scheme],
        version: Version,
      )(implicit trace: Trace): ZIO[Env1 with Scope, Err2, Out] =
        self
          .socketInternal(
            app,
            headers,
            hostOption,
            pathPrefix,
            portOption,
            queries,
            schemeOption,
            version,
          )
          .refineOrDie(pf)
    }

  final def request(method: Method, pathSuffix: String, body: In)(implicit trace: Trace): ZIO[Env, Err, Out] =
    requestInternal(
      body,
      headers,
      hostOption,
      method,
      pathPrefix / pathSuffix,
      portOption,
      queries,
      schemeOption,
      sslConfig,
      Version.Http_1_1,
    )

  final def request(request: Request)(implicit ev: Body <:< In, trace: Trace): ZIO[Env, Err, Out] = {
    requestInternal(
      ev(request.body),
      headers ++ request.headers,
      request.url.host,
      request.method,
      pathPrefix ++ request.path,
      request.url.port,
      queries ++ request.url.queryParams,
      request.url.scheme,
      sslConfig,
      request.version,
    )
  }

  final def retry[Env1 <: Env](policy: Schedule[Env1, Err, Any]): ZClient[Env1, In, Err, Out] =
    new ZClient[Env1, In, Err, Out] {
      def headers: Headers                   = self.headers
      def hostOption: Option[String]         = self.hostOption
      def pathPrefix: Path                   = self.pathPrefix
      def portOption: Option[Int]            = self.portOption
      def queries: QueryParams               = self.queries
      def schemeOption: Option[Scheme]       = self.schemeOption
      def sslConfig: Option[ClientSSLConfig] = self.sslConfig
      def requestInternal(
        body: In,
        headers: Headers,
        hostOption: Option[String],
        method: Method,
        pathPrefix: Path,
        portOption: Option[Int],
        queries: QueryParams,
        schemeOption: Option[Scheme],
        sslConfig: Option[ClientSSLConfig],
        version: Version,
      )(implicit trace: Trace): ZIO[Env1, Err, Out] =
        self
          .requestInternal(
            body,
            headers,
            hostOption,
            method,
            pathPrefix,
            portOption,
            queries,
            schemeOption,
            sslConfig,
            version,
          )
          .retry(policy)
      def socketInternal[Env2 <: Env1](
        app: SocketApp[Env2],
        headers: Headers,
        hostOption: Option[String],
        pathPrefix: Path,
        portOption: Option[Int],
        queries: QueryParams,
        schemeOption: Option[Scheme],
        version: Version,
      )(implicit trace: Trace): ZIO[Env2 with Scope, Err, Out] =
        self
          .socketInternal(
            app,
            headers,
            hostOption,
            pathPrefix,
            portOption,
            queries,
            schemeOption,
            version,
          )
          .retry(policy)
    }

  final def scheme(scheme: Scheme): ZClient[Env, In, Err, Out] =
    copy(schemeOption = Some(scheme))

  final def socket[Env1 <: Env](
    pathSuffix: String,
  )(app: SocketApp[Env1])(implicit trace: Trace): ZIO[Env1 with Scope, Err, Out] =
    socketInternal(
      app,
      headers,
      hostOption,
      pathPrefix / pathSuffix,
      portOption,
      queries,
      schemeOption,
      Version.Http_1_1,
    )

  final def socket[Env1 <: Env](
    url: String,
    app: SocketApp[Env1],
    headers: Headers = Headers.empty,
  )(implicit trace: Trace, unsafe: Unsafe): ZIO[Env1 with Scope, Err, Out] =
    for {
      url <- ZIO.fromEither(URL.fromString(url)).orDie
      out <- socketInternal(
        app,
        headers,
        url.host,
        pathPrefix ++ url.path,
        url.port,
        queries ++ url.queryParams,
        url.scheme,
        Version.Http_1_1,
      )
    } yield out

  final def ssl(ssl: ClientSSLConfig): ZClient[Env, In, Err, Out] =
    copy(sslConfig = Some(ssl))

  final def uri(uri: URI): ZClient[Env, In, Err, Out] =
    copy(
      hostOption = Option(uri.getHost),
      pathPrefix = pathPrefix ++ Path.decode(uri.getRawPath),
      portOption = Option(uri.getPort).filter(_ != -1).orElse(Scheme.decode(uri.getScheme).map(_.port)),
      queries = queries ++ QueryParams.decode(uri.getRawQuery),
    )

  final def url(url: URL): ZClient[Env, In, Err, Out] =
    copy(
      hostOption = url.host,
      pathPrefix = pathPrefix ++ url.path,
      portOption = url.port,
      queries = queries ++ url.queryParams,
    )

  protected def requestInternal(
    body: In,
    headers: Headers,
    hostOption: Option[String],
    method: Method,
    pathPrefix: Path,
    portOption: Option[Int],
    queries: QueryParams,
    schemeOption: Option[Scheme],
    sslConfig: Option[ClientSSLConfig],
    version: Version,
  )(implicit trace: Trace): ZIO[Env, Err, Out]

  protected def socketInternal[Env1 <: Env](
    app: SocketApp[Env1],
    headers: Headers,
    hostOption: Option[String],
    pathPrefix: Path,
    portOption: Option[Int],
    queries: QueryParams,
    schemeOption: Option[Scheme],
    version: Version,
  )(implicit trace: Trace): ZIO[Env1 with Scope, Err, Out]

  private final def copy(
    headers: Headers = headers,
    hostOption: Option[String] = hostOption,
    pathPrefix: Path = pathPrefix,
    portOption: Option[Int] = portOption,
    queries: QueryParams = queries,
    schemeOption: Option[Scheme] = schemeOption,
    sslConfig: Option[ClientSSLConfig] = sslConfig,
  ): ZClient[Env, In, Err, Out] =
    ZClient.Proxy[Env, In, Err, Out](
      self,
      headers,
      hostOption,
      pathPrefix,
      portOption,
      queries,
      schemeOption,
      sslConfig,
    )
}

object ZClient {

  private final case class Proxy[-Env, -In, +Err, +Out](
    client: ZClient[Env, In, Err, Out],
    headers: Headers,
    hostOption: Option[String],
    pathPrefix: Path,
    portOption: Option[Int],
    queries: QueryParams,
    schemeOption: Option[Scheme],
    sslConfig: Option[ClientSSLConfig],
  ) extends ZClient[Env, In, Err, Out] {

    def requestInternal(
      body: In,
      headers: Headers,
      hostOption: Option[String],
      method: Method,
      path: Path,
      portOption: Option[Int],
      queries: QueryParams,
      schemeOption: Option[Scheme],
      sslConfig: Option[ClientSSLConfig],
      version: Version,
    )(implicit trace: Trace): ZIO[Env, Err, Out] =
      client.requestInternal(
        body,
        headers,
        hostOption,
        method,
        path,
        portOption,
        queries,
        schemeOption,
        sslConfig,
        version,
      )

    protected def socketInternal[Env1 <: Env](
      app: SocketApp[Env1],
      headers: Headers,
      hostOption: Option[String],
      pathPrefix: Path,
      portOption: Option[Int],
      queries: QueryParams,
      schemeOption: Option[Scheme],
      version: Version,
    )(implicit trace: Trace): ZIO[Env1 with Scope, Err, Out] =
      client.socketInternal(app, headers, hostOption, pathPrefix, portOption, queries, schemeOption, version)

  }

  final case class ClientLive(
    settings: ClientConfig,
    rtm: NettyRuntime,
    cf: JChannelFactory[JChannel],
    el: JEventLoopGroup,
  ) extends Client
      with ClientRequestEncoder {
    val headers: Headers                   = Headers.empty
    val hostOption: Option[String]         = None
    val pathPrefix: Path                   = Path.empty
    val portOption: Option[Int]            = None
    val queries: QueryParams               = QueryParams.empty
    val schemeOption: Option[Scheme]       = None
    val sslConfig: Option[ClientSSLConfig] = None

    def requestInternal(
      body: Body,
      headers: Headers,
      hostOption: Option[String],
      method: Method,
      path: Path,
      portOption: Option[Int],
      queries: QueryParams,
      schemeOption: Option[Scheme],
      sslConfig: Option[ClientSSLConfig],
      version: Version,
    )(implicit trace: Trace): ZIO[Any, Throwable, Response] = {

      for {
        host     <- ZIO.fromOption(hostOption).orElseFail(new IllegalArgumentException("Host is required"))
        port     <- ZIO.fromOption(portOption).orElseSucceed(sslConfig.fold(80)(_ => 443))
        response <- requestAsync(
          Request
            .default(
              method,
              URL(path, URL.Location.Absolute(schemeOption.getOrElse(Scheme.HTTP), host, port)).setQueryParams(queries),
              body,
            )
            .copy(
              version = version,
              headers = headers,
            ),
          sslConfig.fold(settings)(settings.ssl),
        )
      } yield response
    }

    protected override def socketInternal[R](
      app: SocketApp[R],
      headers: Headers,
      hostOption: Option[String],
      path: Path,
      portOption: Option[Int],
      queries: QueryParams,
      schemeOption: Option[Scheme],
      version: Version,
    )(implicit trace: Trace): ZIO[R with Scope, Throwable, Response] =
      for {
        env      <- ZIO.environment[R]
        location <- ZIO.fromOption {
          for {
            host   <- hostOption
            port   <- portOption
            scheme <- schemeOption
          } yield URL.Location.Absolute(scheme, host, port)
        }.orElseSucceed(URL.Location.Relative)
        res      <- requestAsync(
          Request
            .get(URL(path, location))
            .copy(
              version = version,
              headers = headers,
            ),
          clientConfig = settings.copy(socketApp = Some(app.provideEnvironment(env))),
        ).withFinalizer(_.close.orDie)
      } yield res

    private def requestAsync(request: Request, clientConfig: ClientConfig)(implicit
      trace: Trace,
    ): ZIO[Any, Throwable, Response] = {
      for {
        promise <- Promise.make[Throwable, Response]
        jReq    <- encode(request)
        _       <- NettyFutureExecutor
          .executed(internalRequest(request, jReq, promise, clientConfig)(Unsafe.unsafe, trace))
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
    )(implicit unsafe: Unsafe, trace: Trace): JChannelFuture = {

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

            val pipeline  = ch.pipeline()
            val sslConfig = clientConfig.ssl.getOrElse(ClientSSLConfig.Default)

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
            if (isSSL)
              pipeline.addLast(
                SSL_HANDLER,
                ClientSSLConverter
                  .toNettySSLContext(sslConfig)
                  .newHandler(ch.alloc, host, port),
              )

            // Adding default client channel handlers
            // Defaults from netty:
            //   maxInitialLineLength=4096
            //   maxHeaderSize=8192
            //   maxChunkSize=8192
            // and we add: failOnMissingResponse=true
            // This way, if the server closes the connection before the whole response has been sent,
            // we get an error. (We can also handle the channelInactive callback, but since for now
            // we always buffer the whole HTTP response we can letty Netty take care of this)
            pipeline.addLast(HTTP_CLIENT_CODEC, new HttpClientCodec(4096, clientConfig.maxHeaderSize, 8192, true))

            // HttpContentDecompressor
            if (clientConfig.requestDecompression.enabled)
              pipeline.addLast(
                HTTP_REQUEST_DECOMPRESSION,
                new HttpContentDecompressor(clientConfig.requestDecompression.strict),
              )

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
  )(implicit trace: Trace): ZIO[Client, Throwable, Response] = {
    for {
      uri      <- ZIO.fromEither(URL.fromString(url))
      response <- ZIO.serviceWithZIO[Client](
        _.request(
          Request
            .default(method, uri, content)
            .copy(
              headers = headers.combineIf(addZioUserAgentHeader)(Client.defaultUAHeader),
            ),
        ),
      )
    } yield response

  }

  def request(
    request: Request,
  )(implicit trace: Trace): ZIO[Client, Throwable, Response] = ZIO.serviceWithZIO[Client](_.request(request))

  def socket[R](
    url: String,
    app: SocketApp[R],
    headers: Headers = Headers.empty,
  )(implicit trace: Trace): ZIO[R with Client with Scope, Throwable, Response] =
    Unsafe.unsafe { implicit u =>
      ZIO.serviceWithZIO[Client](_.socket(url, app, headers))
    }

  val live: ZLayer[ClientConfig with ChannelFactory with EventLoopGroup with NettyRuntime, Throwable, Client] = {
    implicit val trace = Trace.empty
    ZLayer {

      for {
        settings       <- ZIO.service[ClientConfig]
        channelFactory <- ZIO.service[ChannelFactory]
        eventLoopGroup <- ZIO.service[EventLoopGroup]
        zx             <- ZIO.service[NettyRuntime]
      } yield ClientLive(settings, zx, channelFactory, eventLoopGroup)
    }
  }

  val fromConfig = {
    implicit val trace = Trace.empty
    EventLoopGroups.fromConfig >+> ChannelFactories.Client.fromConfig >+> NettyRuntime.usingDedicatedThreadPool >>> live
  }

  val default = {
    implicit val trace = Trace.empty
    ClientConfig.default >>> fromConfig
  }

  val zioHttpVersion: CharSequence           = Client.getClass().getPackage().getImplementationVersion()
  val zioHttpVersionNormalized: CharSequence = Option(zioHttpVersion).getOrElse("")

  val scalaVersion: CharSequence   = util.Properties.versionString
  val userAgentValue: CharSequence = s"Zio-Http-Client ${zioHttpVersionNormalized} Scala $scalaVersion"
  val defaultUAHeader: Headers     = Headers(HeaderNames.userAgent, userAgentValue)

  private[zio] val log = Log.withTags("Client")

}
