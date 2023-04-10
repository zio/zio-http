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

import zio._
import zio.http.DnsResolver.Config
import zio.http.URL.Location
import zio.http.model._
import zio.http.model.headers.HeaderOps
import zio.http.netty.{ChannelType, EventLoopGroups, NettyConfig}
import zio.http.netty.client._
import zio.http.socket.SocketApp

import java.net.{InetSocketAddress, URI} // scalafix:ok;

trait ZClient[-Env, -In, +Err, +Out] extends HeaderOps[ZClient[Env, In, Err, Out]] { self =>
  def headers: Headers

  def method: Method

  def sslConfig: Option[ClientSSLConfig]

  def url: URL

  def version: Version

  override def updateHeaders(update: Headers => Headers): ZClient[Env, In, Err, Out] =
    new ZClient[Env, In, Err, Out] {
      override def headers: Headers = update(self.headers)

      override def method: Method = self.method

      override def sslConfig: Option[ClientSSLConfig] = self.sslConfig

      override def url: URL = self.url

      override def version: Version = self.version

      override def request(
        version: Version,
        method: Method,
        url: URL,
        headers: Headers,
        body: In,
        sslConfig: Option[ClientSSLConfig],
      )(implicit trace: Trace): ZIO[Env, Err, Out] =
        self.request(
          version,
          method,
          url,
          headers,
          body,
          sslConfig,
        )

      override def socket[Env1 <: Env](
        app: SocketApp[Env1],
        headers: Headers,
        hostOption: Option[String],
        pathPrefix: Path,
        portOption: Option[RuntimeFlags],
        queries: QueryParams,
        schemeOption: Option[Scheme],
        version: Version,
      )(implicit trace: Trace): ZIO[Env1 with Scope, Err, Out] =
        self.socket(app, headers, hostOption, pathPrefix, portOption, queries, schemeOption, version)
    }

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
    contramapZIO(in => ZIO.succeed(f(in)))

  final def contramapZIO[Env1 <: Env, Err1 >: Err, In2](f: In2 => ZIO[Env1, Err1, In]): ZClient[Env1, In2, Err1, Out] =
    new ZClient[Env1, In2, Err1, Out] {
      override def headers: Headers = self.headers

      override def method: Method = self.method

      override def sslConfig: Option[ClientSSLConfig] = self.sslConfig

      override def url: URL = self.url

      override def version: Version = self.version

      def request(
        version: Version,
        method: Method,
        url: URL,
        headers: Headers,
        body: In2,
        sslConfig: Option[ClientSSLConfig],
      )(implicit trace: Trace): ZIO[Env1, Err1, Out] =
        f(body).flatMap { body =>
          self.request(
            version,
            method,
            url,
            headers,
            body,
            sslConfig,
          )
        }
      def socket[Env2 <: Env1](
        app: SocketApp[Env2],
        headers: Headers,
        hostOption: Option[String],
        pathPrefix: Path,
        portOption: Option[Int],
        queries: QueryParams,
        schemeOption: Option[Scheme],
        version: Version,
      )(implicit trace: Trace): ZIO[Env2 with Scope, Err1, Out] =
        self.socket(
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

  final def delete(pathSuffix: String, body: In)(implicit trace: Trace): ZIO[Env, Err, Out] =
    request(Method.DELETE, pathSuffix, body)

  final def delete(pathSuffix: String)(implicit trace: Trace, ev: Body <:< In): ZIO[Env, Err, Out] =
    delete(pathSuffix, ev(Body.empty))

  final def dieOn(
    f: Err => Boolean,
  )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): ZClient[Env, In, Err, Out] =
    refineOrDie { case e if !f(e) => e }

  final def get(pathSuffix: String, body: In)(implicit trace: Trace): ZIO[Env, Err, Out] =
    request(Method.GET, pathSuffix, body)

  final def get(pathSuffix: String)(implicit trace: Trace, ev: Body <:< In): ZIO[Env, Err, Out] =
    get(pathSuffix, ev(Body.empty))

  final def head(pathSuffix: String, body: In)(implicit trace: Trace): ZIO[Env, Err, Out] =
    request(Method.HEAD, pathSuffix, body)

  final def head(pathSuffix: String)(implicit trace: Trace, ev: Body <:< In): ZIO[Env, Err, Out] =
    head(pathSuffix, Body.empty)

  final def host(host: String): ZClient[Env, In, Err, Out] =
    copy(url = url.withHost(host))

  final def map[Out2](f: Out => Out2): ZClient[Env, In, Err, Out2] =
    mapZIO(out => ZIO.succeed(f(out)))

  def mapError[Err2](f: Err => Err2): ZClient[Env, In, Err2, Out] =
    new ZClient[Env, In, Err2, Out] {
      override def headers: Headers                   = self.headers
      override def method: Method                     = self.method
      override def sslConfig: Option[ClientSSLConfig] = self.sslConfig
      override def url: URL                           = self.url
      override def version: Version                   = self.version
      override def request(
        version: Version,
        method: Method,
        url: URL,
        headers: Headers,
        body: In,
        sslConfig: Option[ClientSSLConfig],
      )(implicit trace: Trace): ZIO[Env, Err2, Out] =
        self.request(version, method, url, headers, body, sslConfig).mapError(f)

      override def socket[Env1 <: Env](
        app: SocketApp[Env1],
        headers: Headers,
        hostOption: Option[String],
        pathPrefix: Path,
        portOption: Option[RuntimeFlags],
        queries: QueryParams,
        schemeOption: Option[Scheme],
        version: Version,
      )(implicit trace: Trace): ZIO[Env1 with Scope, Err2, Out] =
        self.socket(app, headers, hostOption, pathPrefix, portOption, queries, schemeOption, version).mapError(f)
    }

  final def mapZIO[Env1 <: Env, Err1 >: Err, Out2](f: Out => ZIO[Env1, Err1, Out2]): ZClient[Env1, In, Err1, Out2] =
    new ZClient[Env1, In, Err1, Out2] {
      override def headers: Headers = self.headers

      override def method: Method = self.method

      override def sslConfig: Option[ClientSSLConfig] = self.sslConfig

      override def url: URL = self.url

      override def version: Version = self.version

      def request(
        version: Version,
        method: Method,
        url: URL,
        headers: Headers,
        body: In,
        sslConfig: Option[ClientSSLConfig],
      )(implicit trace: Trace): ZIO[Env1, Err1, Out2] =
        self
          .request(
            version,
            method,
            url,
            headers,
            body,
            sslConfig,
          )
          .flatMap(f)
      def socket[Env2 <: Env1](
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
          .socket(
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
    copy(url = url.copy(path = url.path / segment))

  final def port(port: Int): ZClient[Env, In, Err, Out] =
    copy(url = url.withPort(port))

  final def patch(pathSuffix: String, body: In)(implicit trace: Trace): ZIO[Env, Err, Out] =
    request(Method.PATCH, pathSuffix, body)

  final def post(pathSuffix: String, body: In)(implicit trace: Trace): ZIO[Env, Err, Out] =
    request(Method.POST, pathSuffix, body)

  final def put(pathSuffix: String, body: In)(implicit trace: Trace): ZIO[Env, Err, Out] =
    request(Method.PUT, pathSuffix, body)

  def query(key: String, value: String): ZClient[Env, In, Err, Out] =
    copy(url = url.copy(queryParams = url.queryParams.add(key, value)))

  final def refineOrDie[Err2](
    pf: PartialFunction[Err, Err2],
  )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): ZClient[Env, In, Err2, Out] =
    new ZClient[Env, In, Err2, Out] {
      override def headers: Headers = self.headers

      override def method: Method = self.method

      override def sslConfig: Option[ClientSSLConfig] = self.sslConfig

      override def url: URL = self.url

      override def version: Version = self.version

      def request(
        version: Version,
        method: Method,
        url: URL,
        headers: Headers,
        body: In,
        sslConfig: Option[ClientSSLConfig],
      )(implicit trace: Trace): ZIO[Env, Err2, Out] =
        self
          .request(
            version,
            method,
            url,
            headers,
            body,
            sslConfig,
          )
          .refineOrDie(pf)
      def socket[Env1 <: Env](
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
          .socket(
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
    request(
      version,
      method,
      url.copy(path = url.path / pathSuffix),
      headers,
      body,
      sslConfig,
    )

  final def request(request: Request)(implicit ev: Body <:< In, trace: Trace): ZIO[Env, Err, Out] = {
    self.request(
      request.version,
      request.method,
      self.url ++ request.url,
      self.headers ++ request.headers,
      request.body,
      sslConfig,
    )
  }

  final def retry[Env1 <: Env](policy: Schedule[Env1, Err, Any]): ZClient[Env1, In, Err, Out] =
    new ZClient[Env1, In, Err, Out] {
      override def headers: Headers = self.headers

      override def method: Method = self.method

      override def sslConfig: Option[ClientSSLConfig] = self.sslConfig

      override def url: URL = self.url

      override def version: Version = self.version

      def request(
        version: Version,
        method: Method,
        url: URL,
        headers: Headers,
        body: In,
        sslConfig: Option[ClientSSLConfig],
      )(implicit trace: Trace): ZIO[Env1, Err, Out] =
        self
          .request(
            version,
            method,
            url,
            headers,
            body,
            sslConfig,
          )
          .retry(policy)
      def socket[Env2 <: Env1](
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
          .socket(
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
    copy(url = url.withScheme(scheme))

  final def socket[Env1 <: Env](
    pathSuffix: String,
  )(app: SocketApp[Env1])(implicit trace: Trace): ZIO[Env1 with Scope, Err, Out] =
    socket(
      app,
      headers,
      url.host,
      url.path / pathSuffix,
      url.port,
      url.queryParams,
      url.scheme,
      Version.Http_1_1,
    )

  final def socket[Env1 <: Env](
    url: String,
    app: SocketApp[Env1],
    headers: Headers = Headers.empty,
  )(implicit trace: Trace): ZIO[Env1 with Scope, Err, Out] =
    for {
      url <- ZIO.fromEither(URL.decode(url)).orDie
      out <- socket(
        app,
        headers,
        url.host,
        url.path,
        url.port,
        url.queryParams,
        url.scheme,
        Version.Http_1_1,
      )
    } yield out

  final def ssl(ssl: ClientSSLConfig): ZClient[Env, In, Err, Out] =
    copy(sslConfig = Some(ssl))

  final def uri(uri: URI): ZClient[Env, In, Err, Out] = url(URL.fromURI(uri).getOrElse(URL.empty))

  final def url(url: URL): ZClient[Env, In, Err, Out] = copy(url = url)

  def request(
    version: Version,
    method: Method,
    url: URL,
    headers: Headers,
    body: In,
    sslConfig: Option[ClientSSLConfig],
  )(implicit trace: Trace): ZIO[Env, Err, Out]

  def socket[Env1 <: Env](
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
    headers: Headers = self.headers,
    method: Method = self.method,
    sslConfig: Option[ClientSSLConfig] = self.sslConfig,
    url: URL = self.url,
    version: Version = self.version,
  ): ZClient[Env, In, Err, Out] =
    ZClient.Proxy[Env, In, Err, Out](
      headers,
      method,
      sslConfig,
      url,
      version,
      self,
    )

  def withDisabledStreaming(implicit
    ev1: Out <:< Response,
    ev2: Err <:< Throwable,
  ): ZClient[Env, In, Throwable, Response] =
    mapError(ev2).mapZIO(out => ev1(out).collect)
}

object ZClient {

  case class Config(
    socketApp: Option[SocketApp[Any]],
    ssl: Option[ClientSSLConfig],
    proxy: Option[zio.http.Proxy],
    connectionPool: ConnectionPoolConfig,
    maxHeaderSize: Int,
    requestDecompression: Decompression,
    localAddress: Option[InetSocketAddress],
    addUserAgentHeader: Boolean,
  ) {
    self =>

    def addUserAgentHeader(addUserAgentHeader: Boolean): Config =
      self.copy(addUserAgentHeader = addUserAgentHeader)

    def ssl(ssl: ClientSSLConfig): Config = self.copy(ssl = Some(ssl))

    def socketApp(socketApp: SocketApp[Any]): Config = self.copy(socketApp = Some(socketApp))

    def proxy(proxy: zio.http.Proxy): Config = self.copy(proxy = Some(proxy))

    def withFixedConnectionPool(size: Int): Config =
      self.copy(connectionPool = ConnectionPoolConfig.Fixed(size))

    def withDynamicConnectionPool(minimum: Int, maximum: Int, ttl: Duration): Config =
      self.copy(connectionPool = ConnectionPoolConfig.Dynamic(minimum = minimum, maximum = maximum, ttl = ttl))

    /**
     * Configure the client to use `maxHeaderSize` value when encode/decode
     * headers.
     */
    def maxHeaderSize(headerSize: Int): Config = self.copy(maxHeaderSize = headerSize)

    def requestDecompression(isStrict: Boolean): Config =
      self.copy(requestDecompression = if (isStrict) Decompression.Strict else Decompression.NonStrict)
  }

  object Config {
    lazy val config: zio.Config[Config] =
      (
        ClientSSLConfig.config.nested("ssl").optional.withDefault(Config.default.ssl) ++
          zio.http.Proxy.config.nested("proxy").optional.withDefault(Config.default.proxy) ++
          ConnectionPoolConfig.config.nested("connection-pool").withDefault(Config.default.connectionPool) ++
          zio.Config.int("max-header-size").withDefault(Config.default.maxHeaderSize) ++
          Decompression.config.nested("request-decompression").withDefault(Config.default.requestDecompression) ++
          zio.Config.boolean("add-user-agent-header").withDefault(Config.default.addUserAgentHeader)
      ).map { case (ssl, proxy, connectionPool, maxHeaderSize, requestDecompression, addUserAgentHeader) =>
        default.copy(
          ssl = ssl,
          proxy = proxy,
          connectionPool = connectionPool,
          maxHeaderSize = maxHeaderSize,
          requestDecompression = requestDecompression,
          addUserAgentHeader = addUserAgentHeader,
        )
      }

    lazy val default: Config = Config(
      socketApp = None,
      ssl = None,
      proxy = None,
      connectionPool = ConnectionPoolConfig.Disabled,
      maxHeaderSize = 8192,
      requestDecompression = Decompression.No,
      localAddress = None,
      addUserAgentHeader = true,
    )
  }

  private final case class Proxy[-Env, -In, +Err, +Out](
    headers: Headers,
    method: Method,
    sslConfig: Option[ClientSSLConfig],
    url: URL,
    version: Version,
    client: ZClient[Env, In, Err, Out],
  ) extends ZClient[Env, In, Err, Out] {

    def request(
      version: Version,
      method: Method,
      url: URL,
      headers: Headers,
      body: In,
      sslConfig: Option[ClientSSLConfig],
    )(implicit trace: Trace): ZIO[Env, Err, Out] =
      client.request(
        version,
        method,
        url,
        headers,
        body,
        sslConfig,
      )

    def socket[Env1 <: Env](
      app: SocketApp[Env1],
      headers: Headers,
      hostOption: Option[String],
      pathPrefix: Path,
      portOption: Option[Int],
      queries: QueryParams,
      schemeOption: Option[Scheme],
      version: Version,
    )(implicit trace: Trace): ZIO[Env1 with Scope, Err, Out] =
      client.socket(app, headers, hostOption, pathPrefix, portOption, queries, schemeOption, version)

  }

  final class ClientLive private (config: Config, driver: ClientDriver, connectionPool: ConnectionPool[Any])
      extends Client { self =>

    def this(driver: ClientDriver)(connectionPool: ConnectionPool[driver.Connection])(settings: Config) =
      this(settings, driver, connectionPool.asInstanceOf[ConnectionPool[Any]])

    val headers: Headers                   = Headers.empty
    val method: Method                     = Method.GET
    val sslConfig: Option[ClientSSLConfig] = config.ssl
    val url: URL                           = config.localAddress.map(_.getPort).fold(URL.empty)(URL.empty.withPort(_))
    val version: Version                   = Version.Http_1_1

    def request(
      version: Version,
      method: Method,
      url: URL,
      headers: Headers,
      body: Body,
      sslConfig: Option[ClientSSLConfig],
    )(implicit trace: Trace): ZIO[Any, Throwable, Response] = {
      val request = Request(body, headers, method, url, version, None)
      val cfg     = sslConfig.fold(config)(config.ssl)

      requestAsync(request, cfg)
    }

    override def socket[R](
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
              headers = self.headers ++ headers,
            ),
          clientConfig = config.copy(socketApp = Some(app.provideEnvironment(env))),
        ).withFinalizer {
          case resp: Response.CloseableResponse => resp.close.orDie
          case _                                => ZIO.unit
        }
      } yield res

    private def requestAsync(request: Request, clientConfig: Config)(implicit
      trace: Trace,
    ): ZIO[Any, Throwable, Response] =
      request.url.kind match {
        case location: Location.Absolute =>
          ZIO.uninterruptibleMask { restore =>
            for {
              onComplete   <- Promise.make[Throwable, ChannelState]
              onResponse   <- Promise.make[Throwable, Response]
              channelFiber <- ZIO.scoped {
                for {
                  connection       <- connectionPool
                    .get(
                      location,
                      clientConfig.proxy,
                      clientConfig.ssl.getOrElse(ClientSSLConfig.Default),
                      clientConfig.maxHeaderSize,
                      clientConfig.requestDecompression,
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
                        () => clientConfig.socketApp.getOrElse(SocketApp()),
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
              response     <- restore(onResponse.await.onInterrupt {
                onComplete.interrupt *> channelFiber.join.orDie
              })
            } yield response
          }
        case Location.Relative           =>
          ZIO.fail(throw new IllegalArgumentException("Absolute URL is required"))
      }
  }

  def request(
    url: String,
    method: Method = Method.GET,
    headers: Headers = Headers.empty,
    content: Body = Body.empty,
  )(implicit trace: Trace): ZIO[Client, Throwable, Response] = {
    for {
      uri      <- ZIO.fromEither(URL.decode(url))
      response <- ZIO.serviceWithZIO[Client](
        _.request(
          Request.default(method, uri, content).addHeaders(headers),
        ),
      )
    } yield response

  }

  def delete(pathSuffix: String, body: Body)(implicit trace: Trace): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.delete(pathSuffix, body))

  def delete(pathSuffix: String)(implicit trace: Trace): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.delete(pathSuffix))

  def get(pathSuffix: String, body: Body)(implicit trace: Trace): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.get(pathSuffix, body))

  def get(pathSuffix: String)(implicit trace: Trace): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.get(pathSuffix))

  def head(pathSuffix: String, body: Body)(implicit trace: Trace): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.head(pathSuffix, body))

  def head(pathSuffix: String)(implicit trace: Trace): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.head(pathSuffix))

  def patch(pathSuffix: String, body: Body)(implicit trace: Trace): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.patch(pathSuffix, body))

  def post(pathSuffix: String, body: Body)(implicit trace: Trace): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.post(pathSuffix, body))

  def put(pathSuffix: String, body: Body)(implicit trace: Trace): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.put(pathSuffix, body))

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

  def configured(path: String = "zio.http.client"): ZLayer[DnsResolver, Throwable, Client] =
    (
      ZLayer.service[DnsResolver] ++
        ZLayer(ZIO.config(Config.config.nested(path))) ++
        ZLayer(ZIO.config(NettyConfig.config.nested(path)))
    ).mapError(error => new RuntimeException(s"Configuration error: $error")) >>> live

  val customized: ZLayer[Config with ClientDriver with DnsResolver, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.scoped {
      for {
        config         <- ZIO.service[Config]
        driver         <- ZIO.service[ClientDriver]
        dnsResolver    <- ZIO.service[DnsResolver]
        connectionPool <- driver.createConnectionPool(dnsResolver, config.connectionPool)
        baseClient = new ClientLive(driver)(connectionPool)(config)
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

  lazy val live: ZLayer[ZClient.Config with NettyConfig with DnsResolver, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    (NettyClientDriver.live ++ ZLayer.service[DnsResolver]) >>> customized
  }.fresh

  private val zioHttpVersion: String                   = Client.getClass().getPackage().getImplementationVersion()
  private val zioHttpVersionNormalized: Option[String] = Option(zioHttpVersion)

  private val scalaVersion: String           = util.Properties.versionString
  lazy val defaultUAHeader: Header.UserAgent = Header.UserAgent.Complete(
    Header.UserAgent.Product("Zio-Http-Client", zioHttpVersionNormalized),
    Some(Header.UserAgent.Comment(s"Scala $scalaVersion")),
  )
}
