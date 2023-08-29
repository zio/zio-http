/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import java.net.{InetSocketAddress, URI}

import zio._

import zio.http.URL.Location
import zio.http.internal.HeaderOps
import zio.http.netty.NettyConfig
import zio.http.netty.client._

final case class ZClient[-Env, -In, +Err, +Out](
  version: Version,
  url: URL,
  headers: Headers,
  sslConfig: Option[ClientSSLConfig],
  bodyEncoder: ZClient.BodyEncoder[Env, Err, In],
  bodyDecoder: ZClient.BodyDecoder[Env, Err, Out],
  driver: ZClient.Driver[Env, Err],
) extends HeaderOps[ZClient[Env, In, Err, Out]] { self =>
  def apply(request: Request)(implicit ev: Body <:< In, trace: Trace): ZIO[Env & Scope, Err, Out] =
    self.request(request)

  override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): ZClient[Env, In, Err, Out] =
    copy(headers = update(headers))

  /**
   * Applies the specified client aspect, which can modify the execution of this
   * client.
   */
  def @@[
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

  def addPath(path: String): ZClient[Env, In, Err, Out] =
    copy(url = url.copy(path = url.path ++ Path(path)))

  def addPath(path: Path): ZClient[Env, In, Err, Out] =
    copy(url = url.copy(path = url.path ++ path))

  def addLeadingSlash: ZClient[Env, In, Err, Out] =
    copy(url = url.addLeadingSlash)

  def addQueryParam(key: String, value: String): ZClient[Env, In, Err, Out] =
    copy(url = url.copy(queryParams = url.queryParams.add(key, value)))

  def addQueryParams(params: QueryParams): ZClient[Env, In, Err, Out] =
    copy(url = url.copy(queryParams = url.queryParams ++ params))

  def addTrailingSlash: ZClient[Env, In, Err, Out] =
    copy(url = url.addTrailingSlash)

  def addUrl(url: URL): ZClient[Env, In, Err, Out] =
    copy(url = self.url ++ url)

  def contramap[In2](f: In2 => In): ZClient[Env, In2, Err, Out] =
    contramapZIO(in => ZIO.succeed(f(in)))

  def contramapZIO[Env1 <: Env, Err1 >: Err, In2](f: In2 => ZIO[Env1, Err1, In]): ZClient[Env1, In2, Err1, Out] =
    transform(
      self.bodyEncoder.contramapZIO(f),
      self.bodyDecoder,
      self.driver,
    )

  def delete(suffix: String)(implicit ev: Body <:< In): ZIO[Env & Scope, Err, Out] =
    request(Method.DELETE, suffix)(ev(Body.empty))

  def dieOn(
    f: Err => Boolean,
  )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): ZClient[Env, In, Err, Out] =
    refineOrDie { case e if !f(e) => e }

  def disableStreaming(implicit ev: Err <:< Throwable): ZClient[Env, In, Throwable, Out] =
    transform(bodyEncoder.widenError[Throwable], bodyDecoder.widenError[Throwable], driver.disableStreaming)

  def get(suffix: String)(implicit ev: Body <:< In): ZIO[Env & Scope, Err, Out] =
    request(Method.GET, suffix)(ev(Body.empty))

  def head(suffix: String)(implicit ev: Body <:< In): ZIO[Env & Scope, Err, Out] =
    request(Method.HEAD, suffix)(ev(Body.empty))

  def host(host: String): ZClient[Env, In, Err, Out] =
    copy(url = url.host(host))

  def map[Out2](f: Out => Out2): ZClient[Env, In, Err, Out2] =
    mapZIO(out => ZIO.succeed(f(out)))

  def mapError[Err2](f: Err => Err2): ZClient[Env, In, Err2, Out] =
    transform(
      bodyEncoder.mapError(f),
      new ZClient.BodyDecoder[Env, Err2, Out] {
        def decode(response: Response): ZIO[Env, Err2, Out] =
          self.bodyDecoder.decode(response).mapError(f)
      },
      driver.mapError(f),
    )

  def mapZIO[Env1 <: Env, Err1 >: Err, Out2](f: Out => ZIO[Env1, Err1, Out2]): ZClient[Env1, In, Err1, Out2] =
    transform(
      bodyEncoder,
      bodyDecoder.mapZIO(f),
      driver,
    )

  def path(path: String): ZClient[Env, In, Err, Out] = self.path(Path(path))

  def path(path: Path): ZClient[Env, In, Err, Out] =
    copy(url = url.copy(path = path))

  def patch(suffix: String)(implicit ev: Body <:< In): ZIO[Env & Scope, Err, Out] =
    request(Method.PATCH, suffix)(ev(Body.empty))

  def port(port: Int): ZClient[Env, In, Err, Out] =
    copy(url = url.port(port))

  def post(suffix: String)(body: In)(implicit trace: Trace): ZIO[Env & Scope, Err, Out] =
    request(Method.POST, suffix)(body)

  def put(suffix: String)(body: In)(implicit trace: Trace): ZIO[Env & Scope, Err, Out] =
    request(Method.PUT, suffix)(body)

  def refineOrDie[Err2](
    pf: PartialFunction[Err, Err2],
  )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): ZClient[Env, In, Err2, Out] =
    transform(bodyEncoder.refineOrDie(pf), bodyDecoder.refineOrDie(pf), driver.refineOrDie(pf))

  def request(request: Request)(implicit ev: Body <:< In): ZIO[Env & Scope, Err, Out] =
    if (bodyEncoder == ZClient.BodyEncoder.identity)
      bodyDecoder.decodeZIO(
        driver
          .request(
            self.version ++ request.version,
            request.method,
            self.url ++ request.url,
            self.headers ++ request.headers,
            request.body,
            sslConfig,
          ),
      )
    else
      bodyEncoder
        .encode(ev(request.body))
        .flatMap(body =>
          bodyDecoder.decodeZIO(
            driver
              .request(
                self.version ++ request.version,
                request.method,
                self.url ++ request.url,
                self.headers ++ request.headers,
                body,
                sslConfig,
              ),
          ),
        )

  def request(method: Method, suffix: String)(body: In)(implicit trace: Trace): ZIO[Env & Scope, Err, Out] =
    bodyEncoder
      .encode(body)
      .flatMap(body => bodyDecoder.decodeZIO[Env & Scope, Err](requestRaw(method, suffix, body)))

  private def requestRaw(method: Method, suffix: String, body: Body)(implicit
    trace: Trace,
  ): ZIO[Env & Scope, Err, Response] = {
    driver
      .request(
        version,
        method,
        if (suffix.nonEmpty) url.addPath(suffix) else url,
        headers,
        body,
        sslConfig,
      )
  }

  def retry[Env1 <: Env](policy: Schedule[Env1, Err, Any]): ZClient[Env1, In, Err, Out] =
    transform[Env1, In, Err, Out](bodyEncoder, bodyDecoder, self.driver.retry(policy))

  def scheme(scheme: Scheme): ZClient[Env, In, Err, Out] =
    copy(url = url.scheme(scheme))

  def socket[Env1 <: Env](app: WebSocketApp[Env1])(implicit trace: Trace): ZIO[Env1 & Scope, Err, Out] =
    driver
      .socket(
        Version.Default,
        url,
        headers,
        app,
      )
      .flatMap(bodyDecoder.decode)

  def ssl(ssl: ClientSSLConfig): ZClient[Env, In, Err, Out] =
    copy(sslConfig = Some(ssl))

  def transform[Env2, In2, Err2, Out2](
    bodyEncoder: ZClient.BodyEncoder[Env2, Err2, In2],
    bodyDecoder: ZClient.BodyDecoder[Env2, Err2, Out2],
    driver: ZClient.Driver[Env2, Err2],
  ): ZClient[Env2, In2, Err2, Out2] =
    ZClient(
      version,
      url,
      headers,
      sslConfig,
      bodyEncoder,
      bodyDecoder,
      driver,
    )

  def uri(uri: URI): ZClient[Env, In, Err, Out] = url(URL.fromURI(uri).getOrElse(URL.empty))

  def url(url: URL): ZClient[Env, In, Err, Out] = copy(url = url)
}

object ZClient {

  def configured(
    path: NonEmptyChunk[String] = NonEmptyChunk("zio", "http", "client"),
  ): ZLayer[DnsResolver, Throwable, Client] =
    (
      ZLayer.service[DnsResolver] ++
        ZLayer(ZIO.config(Config.config.nested(path.head, path.tail: _*))) ++
        ZLayer(ZIO.config(NettyConfig.config.nested(path.head, path.tail: _*)))
    ).mapError(error => new RuntimeException(s"Configuration error: $error")) >>> live

  val customized: ZLayer[Config with ClientDriver with DnsResolver, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.scoped {
      for {
        config         <- ZIO.service[Config]
        driver         <- ZIO.service[ClientDriver]
        dnsResolver    <- ZIO.service[DnsResolver]
        connectionPool <- driver.createConnectionPool(dnsResolver, config.connectionPool)
        baseClient = fromDriver(new DriverLive(driver)(connectionPool)(config))
      } yield
        if (config.addUserAgentHeader)
          baseClient.addHeader(defaultUAHeader)
        else
          baseClient
    }
  }

  val default: ZLayer[Any, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    (ZLayer.succeed(Config.default) ++ ZLayer.succeed(NettyConfig.default) ++
      DnsResolver.default) >>> live
  }

  def fromDriver[Env, Err](driver: Driver[Env, Err]): ZClient[Env, Body, Err, Response] =
    ZClient(
      Version.Default,
      URL.empty,
      Headers.empty,
      None,
      BodyEncoder.identity,
      BodyDecoder.identity,
      driver,
    )

  val live: ZLayer[ZClient.Config with NettyConfig with DnsResolver, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    (NettyClientDriver.live ++ ZLayer.service[DnsResolver]) >>> customized
  }.fresh

  def request(request: Request): ZIO[Client & Scope, Throwable, Response] =
    ZIO.serviceWithZIO[Client](c => c(request))

  def socket[R](socketApp: WebSocketApp[R]): ZIO[R with Client & Scope, Throwable, Response] =
    ZIO.serviceWithZIO[Client](c => c.socket(socketApp))

  trait BodyDecoder[-Env, +Err, +Out] { self =>
    def decode(response: Response): ZIO[Env, Err, Out]

    def decodeZIO[Env1 <: Env, Err1 >: Err](zio: ZIO[Env1, Err1, Response]): ZIO[Env1, Err1, Out] =
      zio.flatMap(decode)

    final def mapError[Err2](f: Err => Err2): BodyDecoder[Env, Err2, Out] =
      new BodyDecoder[Env, Err2, Out] {
        def decode(response: Response): ZIO[Env, Err2, Out] = self.decode(response).mapError(f)
      }

    final def mapZIO[Env1 <: Env, Err1 >: Err, Out2](f: Out => ZIO[Env1, Err1, Out2]): BodyDecoder[Env1, Err1, Out2] =
      new BodyDecoder[Env1, Err1, Out2] {
        def decode(response: Response): ZIO[Env1, Err1, Out2] = self.decode(response).flatMap(f)
      }

    final def refineOrDie[Err2](
      pf: PartialFunction[Err, Err2],
    )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): BodyDecoder[Env, Err2, Out] =
      new BodyDecoder[Env, Err2, Out] {
        def decode(response: Response): ZIO[Env, Err2, Out] = self.decode(response).refineOrDie(pf)
      }

    final def widenError[E1](implicit ev: Err <:< E1): BodyDecoder[Env, E1, Out] =
      self.asInstanceOf[BodyDecoder[Env, E1, Out]]
  }
  object BodyDecoder                  {
    val identity: BodyDecoder[Any, Nothing, Response] =
      new BodyDecoder[Any, Nothing, Response] {
        final def decode(response: Response): ZIO[Any, Nothing, Response] = Exit.succeed(response)

        override def decodeZIO[Env1 <: Any, Err1 >: Nothing](
          zio: ZIO[Env1, Err1, Response],
        ): ZIO[Env1, Err1, Response] =
          zio
      }
  }
  trait BodyEncoder[-Env, +Err, -In]  { self =>
    final def contramapZIO[Env1 <: Env, Err1 >: Err, In2](f: In2 => ZIO[Env1, Err1, In]): BodyEncoder[Env1, Err1, In2] =
      new BodyEncoder[Env1, Err1, In2] {
        def encode(in: In2): ZIO[Env1, Err1, Body] = f(in).flatMap(self.encode)
      }

    def encode(in: In): ZIO[Env, Err, Body]

    final def mapError[Err2](f: Err => Err2): BodyEncoder[Env, Err2, In] =
      new BodyEncoder[Env, Err2, In] {
        def encode(in: In): ZIO[Env, Err2, Body] = self.encode(in).mapError(f)
      }

    final def refineOrDie[Err2](
      pf: PartialFunction[Err, Err2],
    )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): BodyEncoder[Env, Err2, In] =
      new BodyEncoder[Env, Err2, In] {
        def encode(in: In): ZIO[Env, Err2, Body] = self.encode(in).refineOrDie(pf)
      }

    final def widenError[E1](implicit ev: Err <:< E1): BodyEncoder[Env, E1, In] =
      self.asInstanceOf[BodyEncoder[Env, E1, In]]
  }
  object BodyEncoder                  {
    val identity: BodyEncoder[Any, Nothing, Body] =
      new BodyEncoder[Any, Nothing, Body] {
        def encode(body: Body): ZIO[Any, Nothing, Body] = Exit.succeed(body)
      }
  }

  trait Driver[-Env, +Err] { self =>
    final def apply(request: Request)(implicit trace: Trace): ZIO[Env & Scope, Err, Response] =
      self.request(request.version, request.method, request.url, request.headers, request.body, None)

    final def disableStreaming(implicit ev: Err <:< Throwable): Driver[Env, Throwable] = {
      val self0 = self.widenError[Throwable]

      new Driver[Env, Throwable] {
        override def request(
          version: Version,
          method: Method,
          url: URL,
          headers: Headers,
          body: Body,
          sslConfig: Option[ClientSSLConfig],
        )(implicit trace: Trace): ZIO[Env & Scope, Throwable, Response] =
          self0.request(version, method, url, headers, body, sslConfig).flatMap { response =>
            response.body.asChunk.map { chunk =>
              response.copy(body = Body.fromChunk(chunk))
            }
          }

        override def socket[Env1 <: Env](
          version: Version,
          url: URL,
          headers: Headers,
          app: WebSocketApp[Env1],
        )(implicit trace: Trace): ZIO[Env1 & Scope, Throwable, Response] =
          self0
            .socket(
              version,
              url,
              headers,
              app,
            )
            .flatMap { response =>
              response.body.asChunk.map { chunk =>
                response.copy(body = Body.fromChunk(chunk))
              }
            }
      }
    }

    final def mapError[Err2](f: Err => Err2): Driver[Env, Err2] =
      new Driver[Env, Err2] {
        override def request(
          version: Version,
          method: Method,
          url: URL,
          headers: Headers,
          body: Body,
          sslConfig: Option[ClientSSLConfig],
        )(implicit trace: Trace): ZIO[Env & Scope, Err2, Response] =
          self.request(version, method, url, headers, body, sslConfig).mapError(f)

        override def socket[Env1 <: Env](
          version: Version,
          url: URL,
          headers: Headers,
          app: WebSocketApp[Env1],
        )(implicit trace: Trace): ZIO[Env1 & Scope, Err2, Response] =
          self
            .socket(
              version,
              url,
              headers,
              app,
            )
            .mapError(f)
      }

    final def refineOrDie[Err2](
      pf: PartialFunction[Err, Err2],
    )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): Driver[Env, Err2] =
      new Driver[Env, Err2] {
        override def request(
          version: Version,
          method: Method,
          url: URL,
          headers: Headers,
          body: Body,
          sslConfig: Option[ClientSSLConfig],
        )(implicit trace: Trace): ZIO[Env & Scope, Err2, Response] =
          self.request(version, method, url, headers, body, sslConfig).refineOrDie(pf)

        override def socket[Env1 <: Env](
          version: Version,
          url: URL,
          headers: Headers,
          app: WebSocketApp[Env1],
        )(implicit trace: Trace): ZIO[Env1 & Scope, Err2, Response] =
          self
            .socket(
              version,
              url,
              headers,
              app,
            )
            .refineOrDie(pf)
      }

    def request(
      version: Version,
      method: Method,
      url: URL,
      headers: Headers,
      body: Body,
      sslConfig: Option[ClientSSLConfig],
    )(implicit trace: Trace): ZIO[Env & Scope, Err, Response]

    final def request(req: Request)(implicit trace: Trace): ZIO[Env & Scope, Err, Response] =
      request(req.version, req.method, req.url, req.headers, req.body, None)

    final def retry[Env1 <: Env, Err1 >: Err](policy: zio.Schedule[Env1, Err1, Any]) =
      new Driver[Env1, Err1] {
        override def request(
          version: Version,
          method: Method,
          url: URL,
          headers: Headers,
          body: Body,
          sslConfig: Option[ClientSSLConfig],
        )(implicit trace: Trace): ZIO[Env1 & Scope, Err1, Response] =
          self.request(version, method, url, headers, body, sslConfig).retry(policy)

        override def socket[Env2 <: Env1](
          version: Version,
          url: URL,
          headers: Headers,
          app: WebSocketApp[Env2],
        )(implicit trace: Trace): ZIO[Env2 & Scope, Err1, Response] =
          self
            .socket(
              version,
              url,
              headers,
              app,
            )
            .retry(policy)
      }

    def socket[Env1 <: Env](
      version: Version,
      url: URL,
      headers: Headers,
      app: WebSocketApp[Env1],
    )(implicit trace: Trace): ZIO[Env1 & Scope, Err, Response]

    def widenError[E1](implicit ev: Err <:< E1): Driver[Env, E1] = self.asInstanceOf[Driver[Env, E1]]
  }

  final case class Config(
    ssl: Option[ClientSSLConfig],
    proxy: Option[zio.http.Proxy],
    connectionPool: ConnectionPoolConfig,
    maxHeaderSize: Int,
    requestDecompression: Decompression,
    localAddress: Option[InetSocketAddress],
    addUserAgentHeader: Boolean,
    webSocketConfig: WebSocketConfig,
    idleTimeout: Option[Duration],
    connectionTimeout: Option[Duration],
  ) {
    self =>

    def addUserAgentHeader(addUserAgentHeader: Boolean): Config =
      self.copy(addUserAgentHeader = addUserAgentHeader)

    def connectionTimeout(timeout: Duration): Config =
      self.copy(connectionTimeout = Some(timeout))

    def idleTimeout(timeout: Duration): Config =
      self.copy(idleTimeout = Some(timeout))

    def disabledConnectionPool: Config =
      self.copy(connectionPool = ConnectionPoolConfig.Disabled)

    /**
     * Configure the client to use `maxHeaderSize` value when encode/decode
     * headers.
     */
    def maxHeaderSize(headerSize: Int): Config = self.copy(maxHeaderSize = headerSize)

    def noConnectionTimeout: Config = self.copy(connectionTimeout = None)

    def noIdleTimeout: Config = self.copy(idleTimeout = None)

    def proxy(proxy: zio.http.Proxy): Config = self.copy(proxy = Some(proxy))

    def requestDecompression(isStrict: Boolean): Config =
      self.copy(requestDecompression = if (isStrict) Decompression.Strict else Decompression.NonStrict)

    def ssl(ssl: ClientSSLConfig): Config = self.copy(ssl = Some(ssl))

    def fixedConnectionPool(size: Int): Config =
      self.copy(connectionPool = ConnectionPoolConfig.Fixed(size))

    def dynamicConnectionPool(minimum: Int, maximum: Int, ttl: Duration): Config =
      self.copy(connectionPool = ConnectionPoolConfig.Dynamic(minimum = minimum, maximum = maximum, ttl = ttl))

    def webSocketConfig(webSocketConfig: WebSocketConfig): Config =
      self.copy(webSocketConfig = webSocketConfig)
  }

  object Config {
    lazy val config: zio.Config[Config] =
      (
        ClientSSLConfig.config.nested("ssl").optional.withDefault(Config.default.ssl) ++
          zio.http.Proxy.config.nested("proxy").optional.withDefault(Config.default.proxy) ++
          ConnectionPoolConfig.config.nested("connection-pool").withDefault(Config.default.connectionPool) ++
          zio.Config.int("max-header-size").withDefault(Config.default.maxHeaderSize) ++
          Decompression.config.nested("request-decompression").withDefault(Config.default.requestDecompression) ++
          zio.Config.boolean("add-user-agent-header").withDefault(Config.default.addUserAgentHeader) ++
          zio.Config.duration("idle-timeout").optional.withDefault(Config.default.idleTimeout) ++
          zio.Config.duration("connection-timeout").optional.withDefault(Config.default.connectionTimeout)
      ).map {
        case (
              ssl,
              proxy,
              connectionPool,
              maxHeaderSize,
              requestDecompression,
              addUserAgentHeader,
              idleTimeout,
              connectionTimeout,
            ) =>
          default.copy(
            ssl = ssl,
            proxy = proxy,
            connectionPool = connectionPool,
            maxHeaderSize = maxHeaderSize,
            requestDecompression = requestDecompression,
            addUserAgentHeader = addUserAgentHeader,
            idleTimeout = idleTimeout,
            connectionTimeout = connectionTimeout,
          )
      }

    lazy val default: Config = Config(
      ssl = None,
      proxy = None,
      connectionPool = ConnectionPoolConfig.Fixed(10),
      maxHeaderSize = 8192,
      requestDecompression = Decompression.No,
      localAddress = None,
      addUserAgentHeader = true,
      webSocketConfig = WebSocketConfig.default,
      idleTimeout = Some(50.seconds),
      connectionTimeout = None,
    )
  }

  private[http] final class DriverLive private (
    config: Config,
    driver: ClientDriver,
    connectionPool: ConnectionPool[Any],
  ) extends ZClient.Driver[Any, Throwable] { self =>

    def this(driver: ClientDriver)(connectionPool: ConnectionPool[driver.Connection])(settings: Config) =
      this(settings, driver, connectionPool.asInstanceOf[ConnectionPool[Any]])

    def request(
      version: Version,
      method: Method,
      url: URL,
      headers: Headers,
      body: Body,
      sslConfig: Option[ClientSSLConfig],
    )(implicit trace: Trace): ZIO[Scope, Throwable, Response] = {
      val requestHeaders = body.mediaType match {
        case None        => headers
        case Some(value) => headers.removeHeader(Header.ContentType).addHeader(Header.ContentType(value))
      }

      val request = Request(version, method, url, requestHeaders, body, None)
      val cfg     = sslConfig.fold(config)(config.ssl)

      requestAsync(request, cfg, () => WebSocketApp.unit, None)
    }

    def socket[Env1](
      version: Version,
      url: URL,
      headers: Headers,
      app: WebSocketApp[Env1],
    )(implicit trace: Trace): ZIO[Env1 & Scope, Throwable, Response] =
      for {
        env <- ZIO.environment[Env1]
        webSocketUrl = url.scheme(
          url.scheme match {
            case Some(Scheme.HTTP)  => Scheme.WS
            case Some(Scheme.HTTPS) => Scheme.WSS
            case Some(Scheme.WS)    => Scheme.WS
            case Some(Scheme.WSS)   => Scheme.WSS
            case None               => Scheme.WS
          },
        )
        scope <- ZIO.scope
        res <- requestAsync(
          Request(version = version, method = Method.GET, url = webSocketUrl, headers = headers),
          config,
          () => app.provideEnvironment(env),
          Some(scope),
        )
      } yield res

    private def requestAsync(
      request: Request,
      clientConfig: Config,
      createSocketApp: () => WebSocketApp[Any],
      outerScope: Option[Scope],
    )(implicit
      trace: Trace,
    ): ZIO[Scope, Throwable, Response] =
      request.url.kind match {
        case location: Location.Absolute =>
          ZIO.uninterruptibleMask { restore =>
            for {
              onComplete <- Promise.make[Throwable, ChannelState]
              onResponse <- Promise.make[Throwable, Response]
              inChannelScope = outerScope match {
                case Some(scope) => (zio: ZIO[Scope, Throwable, Unit]) => scope.extend(zio)
                case None        => (zio: ZIO[Scope, Throwable, Unit]) => ZIO.scoped(zio)
              }
              channelFiber <- inChannelScope {
                for {
                  connection       <- connectionPool
                    .get(
                      location,
                      clientConfig.proxy,
                      clientConfig.ssl.getOrElse(ClientSSLConfig.Default),
                      clientConfig.maxHeaderSize,
                      clientConfig.requestDecompression,
                      clientConfig.idleTimeout,
                      clientConfig.connectionTimeout,
                      clientConfig.localAddress,
                    )
                    .tapErrorCause(cause => onResponse.failCause(cause))
                    .map(_.asInstanceOf[driver.Connection])
                  channelInterface <-
                    driver
                      .requestOnChannel(
                        connection,
                        location,
                        request,
                        onResponse,
                        onComplete,
                        connectionPool.enableKeepAlive,
                        createSocketApp,
                        clientConfig.webSocketConfig,
                      )
                      .tapErrorCause(cause => onResponse.failCause(cause))
                  _                <-
                    onComplete.await.interruptible.exit.flatMap { exit =>
                      if (exit.isInterrupted) {
                        channelInterface.interrupt
                          .zipRight(connectionPool.invalidate(connection))
                          .uninterruptible
                      } else {
                        channelInterface.resetChannel
                          .zip(exit)
                          .map { case (s1, s2) => s1 && s2 }
                          .catchAllCause(_ =>
                            ZIO.succeed(ChannelState.Invalid),
                          ) // In case resetting the channel fails we cannot reuse it
                          .flatMap { channelState =>
                            connectionPool
                              .invalidate(connection)
                              .when(channelState == ChannelState.Invalid)
                          }
                          .uninterruptible
                      }
                    }
                } yield ()
              }.forkDaemon // Needs to live as long as the channel is alive, as the response body may be streaming
              _ <- ZIO.addFinalizer(onComplete.interrupt)
              response <- restore(onResponse.await.onInterrupt {
                onComplete.interrupt *> channelFiber.join.orDie
              })
            } yield response
          }
        case Location.Relative           =>
          ZIO.fail(throw new IllegalArgumentException("Absolute URL is required"))
      }
  }

  private val zioHttpVersion: String                   = Client.getClass().getPackage().getImplementationVersion()
  private val zioHttpVersionNormalized: Option[String] = Option(zioHttpVersion)

  private val scalaVersion: String           = util.Properties.versionString
  lazy val defaultUAHeader: Header.UserAgent = Header.UserAgent.Complete(
    Header.UserAgent.Product("Zio-Http-Client", zioHttpVersionNormalized),
    Some(Header.UserAgent.Comment(s"Scala $scalaVersion")),
  )
}
