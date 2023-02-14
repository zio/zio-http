package zio.http

import zio._
import zio.http.URL.Location
import zio.http.model._
import zio.http.netty.client._
import zio.http.service._
import zio.http.socket.SocketApp

import java.net.URI // scalafix:ok;

trait ZClient[-Env, -In, +Err, +Out] { self =>

  def headers: Headers

  def hostOption: Option[String]

  def pathPrefix: Path

  def portOption: Option[Int]

  def queries: QueryParams

  def schemeOption: Option[Scheme]

  def sslConfig: Option[ClientSSLConfig]

  /**
   * Applies the specified client aspect, which can modify the execution of this
   * client.
   */
  final def @@[
    LowerEnv <: UpperEnv,
    UpperEnv <: Env,
    LowerIn <: UpperIn,
    UpperIn <: In,
    LowerErr >: Err,
    UpperErr >: LowerErr,
    LowerOut >: Out,
    UpperOut >: LowerOut,
  ](
    aspect: ZClientAspect[LowerEnv, UpperEnv, LowerIn, UpperIn, LowerErr, UpperErr, LowerOut, UpperOut],
  ): ZClient[UpperEnv, UpperIn, LowerErr, LowerOut] =
    aspect(self)

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
      ): ZIO[Env1, Err1, Out] =
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
      ): ZIO[Env2 with Scope, Err1, Out] =
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

  final def delete(pathSuffix: String, body: In): ZIO[Env, Err, Out] =
    request(Method.DELETE, pathSuffix, body)

  final def delete(pathSuffix: String)(implicit ev: Body <:< In): ZIO[Env, Err, Out] =
    delete(pathSuffix, ev(Body.empty))

  final def dieOn(
    f: Err => Boolean,
  )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err]): ZClient[Env, In, Err, Out] =
    refineOrDie { case e if !f(e) => e }

  final def get(pathSuffix: String, body: In): ZIO[Env, Err, Out] =
    request(Method.GET, pathSuffix, body)

  final def get(pathSuffix: String)(implicit ev: Body <:< In): ZIO[Env, Err, Out] =
    get(pathSuffix, ev(Body.empty))

  final def head(pathSuffix: String, body: In): ZIO[Env, Err, Out] =
    request(Method.HEAD, pathSuffix, body)

  final def head(pathSuffix: String)(implicit ev: Body <:< In): ZIO[Env, Err, Out] =
    head(pathSuffix, Body.empty)

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
      ): ZIO[Env1, Err1, Out2] =
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
      ): ZIO[Env2 with Scope, Err1, Out2] =
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

  final def port(port: Int): ZClient[Env, In, Err, Out] =
    copy(portOption = Some(port))

  final def patch(pathSuffix: String, body: In): ZIO[Env, Err, Out] =
    request(Method.PATCH, pathSuffix, body)

  final def post(pathSuffix: String, body: In): ZIO[Env, Err, Out] =
    request(Method.POST, pathSuffix, body)

  final def put(pathSuffix: String, body: In): ZIO[Env, Err, Out] =
    request(Method.PUT, pathSuffix, body)

  def query(key: String, value: String): ZClient[Env, In, Err, Out] =
    copy(queries = queries.add(key, value))

  final def refineOrDie[Err2](
    pf: PartialFunction[Err, Err2],
  )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err]): ZClient[Env, In, Err2, Out] =
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
      ): ZIO[Env, Err2, Out] =
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
      ): ZIO[Env1 with Scope, Err2, Out] =
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

  final def request(method: Method, pathSuffix: String, body: In): ZIO[Env, Err, Out] =
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

  final def request(request: Request)(implicit ev: Body <:< In): ZIO[Env, Err, Out] = {
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
      ): ZIO[Env1, Err, Out] =
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
      ): ZIO[Env2 with Scope, Err, Out] =
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
  )(app: SocketApp[Env1]): ZIO[Env1 with Scope, Err, Out] =
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
  ): ZIO[Env1 with Scope, Err, Out] =
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
  ): ZIO[Env, Err, Out]

  protected def socketInternal[Env1 <: Env](
    app: SocketApp[Env1],
    headers: Headers,
    hostOption: Option[String],
    pathPrefix: Path,
    portOption: Option[Int],
    queries: QueryParams,
    schemeOption: Option[Scheme],
    version: Version,
  ): ZIO[Env1 with Scope, Err, Out]

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
    ): ZIO[Env, Err, Out] =
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
    ): ZIO[Env1 with Scope, Err, Out] =
      client.socketInternal(app, headers, hostOption, pathPrefix, portOption, queries, schemeOption, version)

  }

  final class ClientLive private (config: ClientConfig, driver: ClientDriver, connectionPool: ConnectionPool[Any])
      extends Client
      with ClientRequestEncoder {

    def this(driver: ClientDriver)(connectionPool: ConnectionPool[driver.Connection])(settings: ClientConfig) =
      this(settings, driver, connectionPool.asInstanceOf[ConnectionPool[Any]])

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
    ): ZIO[Any, Throwable, Response] = {

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
          sslConfig.fold(config)(config.ssl),
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
    ): ZIO[R with Scope, Throwable, Response] =
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
          clientConfig = config.copy(socketApp = Some(app.provideEnvironment(env))),
        ).withFinalizer {
          case resp: Response.CloseableResponse => resp.close.orDie
          case _                                => ZIO.unit
        }
      } yield res

    private def requestAsync(request: Request, clientConfig: ClientConfig): ZIO[Any, Throwable, Response] =
      ZIO.uninterruptibleMask { restore =>
        Promise.make[Throwable, Response].flatMap { onResponse =>
          restore(internalRequest(request, onResponse, clientConfig)(Unsafe.unsafe)).foldCauseZIO(
            cause => onResponse.failCause(cause) *> restore(onResponse.await),
            canceler => restore(onResponse.await).onInterrupt(canceler),
          )
        }
      }

    val activeChannelScopes = new java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * It handles both - Websocket and HTTP requests.
     */
    private def internalRequest(
      req: Request,
      onResponse: Promise[Throwable, Response],
      clientConfig: ClientConfig,
    )(implicit unsafe: Unsafe): ZIO[Any, Throwable, ZIO[Any, Nothing, Unit]] =
      ZIO.scoped {
        req.url.kind match {
          case location: Location.Absolute =>
            for {
              onCompleteFinished <- Promise.make[Nothing, Unit]
              onComplete         <- Promise.make[Throwable, ChannelState]
              canceler = onComplete.interrupt *> onCompleteFinished.await
              channelScope <- Scope.make
              _            <- ZIO.uninterruptibleMask { restore =>
                for {
                  id               <- ZIO.succeed(scala.util.Random.nextInt())
                  _                <- channelScope.addFinalizer {
                    ZIO.succeed {
                      val n = activeChannelScopes.decrementAndGet()
                      println(s"channel scope $id closed, active scopes: $n")
                    }
                  }
                  _                <- ZIO.succeed {
                    val n = activeChannelScopes.incrementAndGet()
                    println(s"channel scope $id created, active scopes: $n")
                  }
                  connection       <-
                    restore {
                      connectionPool
                        .get(
                          location,
                          clientConfig.proxy,
                          clientConfig.ssl.getOrElse(ClientSSLConfig.Default),
                          clientConfig.maxHeaderSize,
                          clientConfig.requestDecompression,
                          clientConfig.localAddress,
                        )
                        .map(_.asInstanceOf[driver.Connection])
                        .provideEnvironment(ZEnvironment(channelScope))
                    }.onExit { exit =>
                      channelScope
                        .close(exit)
                        .zipRight(onCompleteFinished.succeed(()))
                        .when(exit.isInterrupted || exit.isFailure)
                    }
                  channelInterface <-
                    driver
                      .requestOnChannel(
                        connection,
                        location,
                        req,
                        onResponse,
                        onComplete,
                        clientConfig.useAggregator,
                        connectionPool.enableKeepAlive,
                        () => clientConfig.socketApp.getOrElse(SocketApp()),
                      )
                      .tapErrorCause { cause =>
                        channelScope.close(Exit.failCause(cause))
                      }
                  // If request registration failed we release the channel immediately.
                  // Otherwise we wait for completion signal from netty in a background fiber:
                  _                <-
                    onComplete.await.interruptible.exit.flatMap { exit =>
                      if (exit.isInterrupted) {
                        channelInterface
                          .interrupt()
                          .zipRight(connectionPool.invalidate(connection))
                          .zipRight(channelScope.close(exit))
                          .zipRight(onCompleteFinished.succeed(()))
                          .uninterruptible
                      } else {
                        channelInterface
                          .resetChannel()
                          .zip(exit)
                          .map { case (s1, s2) => s1 && s2 }
                          .catchAll(_ =>
                            ZIO.succeed(ChannelState.Invalid),
                          ) // In case resetting the channel fails we cannot reuse it
                          .flatMap { channelState =>
                            connectionPool
                              .invalidate(connection)
                              .when(channelState == ChannelState.Invalid)
                          }
                          .zipRight(channelScope.close(exit))
                          .zipRight(onCompleteFinished.succeed(()))
                          .uninterruptible
                      }
                    }.forkDaemon
                } yield ()
              }
            } yield canceler
          case Location.Relative           =>
            ZIO.fail(throw new IllegalArgumentException("Absolute URL is required"))
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
          Request
            .default(method, uri, content)
            .copy(
              headers = headers.combineIf(addZioUserAgentHeader)(Client.defaultUAHeader),
            ),
        ),
      )
    } yield response

  }

  def delete(pathSuffix: String, body: Body): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.delete(pathSuffix, body))

  def delete(pathSuffix: String): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.delete(pathSuffix))

  def get(pathSuffix: String, body: Body): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.get(pathSuffix, body))

  def get(pathSuffix: String): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.get(pathSuffix))

  def head(pathSuffix: String, body: Body): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.head(pathSuffix, body))

  def head(pathSuffix: String): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.head(pathSuffix))

  def patch(pathSuffix: String, body: Body): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.patch(pathSuffix, body))

  def post(pathSuffix: String, body: Body): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.post(pathSuffix, body))

  def put(pathSuffix: String, body: Body): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.put(pathSuffix, body))

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

  val live: ZLayer[ClientConfig with ClientDriver, Throwable, Client] = {
    ZLayer.scoped {
      for {
        config         <- ZIO.service[ClientConfig]
        driver         <- ZIO.service[ClientDriver]
        connectionPool <- driver.createConnectionPool(config.connectionPool)
      } yield new ClientLive(driver)(connectionPool)(config)
    }
  }
  val fromConfig: ZLayer[ClientConfig, Throwable, Client]             = {
    NettyClientDriver.fromConfig >>> live
  }.fresh

  val default: ZLayer[Any, Throwable, Client] = {
    ClientConfig.default >>> fromConfig
  }

  val zioHttpVersion: CharSequence           = Client.getClass().getPackage().getImplementationVersion()
  val zioHttpVersionNormalized: CharSequence = Option(zioHttpVersion).getOrElse("")

  val scalaVersion: CharSequence   = util.Properties.versionString
  val userAgentValue: CharSequence = s"Zio-Http-Client ${zioHttpVersionNormalized} Scala $scalaVersion"
  val defaultUAHeader: Headers     = Headers(HeaderNames.userAgent, userAgentValue)

  private[zio] val log = Log.withTags("Client")

}
