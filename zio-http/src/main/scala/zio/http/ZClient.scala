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
import zio.http.ZClient.{BodyEncoder, BodyDecoder}

import java.net.{InetSocketAddress, URI} // scalafix:ok;

/**
 * A [[zio.http.ZClient]] is an HTTP client that can send requests to a server,
 * using a range of settings (such as header, method, url, and encoding /
 * decoding) that may be common across multiple requests. The client embeds a
 * Driver, which is responsible for the actual invocation of the remote HTTP
 * endpoint.
 */
final case class ZClient[-Env, -In, +Err, +Out](
  headers: Headers,
  method: Method,
  sslConfig: Option[ClientSSLConfig],
  url: URL,
  version: Version,
  bodyEncoder: BodyEncoder[Env, Err, In],
  bodyDecoder: BodyDecoder[Env, Err, Out],
  driver: ZClient.Driver[Env, Err],
) extends HeaderOps[ZClient[Env, In, Err, Out]] { self =>

  override def updateHeaders(update: Headers => Headers): ZClient[Env, In, Err, Out] =
    copy(headers = update(headers))

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
    transform(
      new BodyEncoder[Env1, Err1, In2] {
        def encode(in: In2): ZIO[Env1, Err1, Body] = f(in).flatMap(self.bodyEncoder.encode)
      },
      self.bodyDecoder,
      self.driver,
    )

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

  final def mapZIO[Env1 <: Env, Err1 >: Err, Out2](f: Out => ZIO[Env1, Err1, Out2]): ZClient[Env1, In, Err1, Out2] =
    copy(bodyDecoder = new BodyDecoder[Env1, Err1, Out2] {
      def decode(response: Response): ZIO[Env1, Err1, Out2] =
        self.bodyDecoder.decode(response).flatMap(f)
    })

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
    transform(bodyEncoder.refineOrDie(pf), bodyDecoder.refineOrDie(pf), driver.refineOrDie(pf))

  final def request(method: Method, pathSuffix: String, body: In)(implicit trace: Trace): ZIO[Env, Err, Out] =
    bodyEncoder.encode(body).flatMap { body =>
      driver
        .request(
          self.version,
          self.method ++ method,
          self.url / pathSuffix,
          self.headers,
          body,
          sslConfig,
        )
        .flatMap(bodyDecoder.decode)
    }

  final def request(request: Request)(implicit ev: Body <:< In, trace: Trace): ZIO[Env, Err, Out] =
    bodyEncoder.encode(ev(request.body)).flatMap { body =>
      driver
        .request(
          self.version ++ request.version,
          self.method ++ request.method,
          self.url ++ request.url,
          self.headers ++ request.headers,
          body,
          sslConfig,
        )
        .flatMap(bodyDecoder.decode(_))
    }

  final def retry[Env1 <: Env](policy: Schedule[Env1, Err, Any]): ZClient[Env1, In, Err, Out] =
    transform[Env1, In, Err, Out](bodyEncoder, bodyDecoder, self.driver.retry(policy))

  final def scheme(scheme: Scheme): ZClient[Env, In, Err, Out] =
    copy(url = url.withScheme(scheme))

  final def socket[Env1 <: Env](
    pathSuffix: String,
  )(app: SocketApp[Env1])(implicit trace: Trace): ZIO[Env1 with Scope, Err, Out] =
    driver
      .socket(
        app,
        self.headers,
        self.url.copy(path = url.path / pathSuffix),
        Version.Http_1_1,
      )
      .flatMap(bodyDecoder.decode(_))

  final def socket[Env1 <: Env](
    url: String,
    app: SocketApp[Env1],
    headers: Headers = Headers.empty,
  )(implicit trace: Trace): ZIO[Env1 with Scope, Err, Out] =
    for {
      url2 <- ZIO.fromEither(URL.decode(url)).orDie
      out  <- driver
        .socket(
          app,
          self.headers ++ headers,
          self.url ++ url2,
          Version.Http_1_1,
        )
        .flatMap(bodyDecoder.decode(_))
    } yield out

  final def ssl(ssl: ClientSSLConfig): ZClient[Env, In, Err, Out] =
    copy(sslConfig = Some(ssl))

  final def uri(uri: URI): ZClient[Env, In, Err, Out] = url(URL.fromURI(uri).getOrElse(URL.empty))

  final def url(url: URL): ZClient[Env, In, Err, Out] = copy(url = url)

  def transform[Env2, In2, Err2, Out2](
    bodyEncoder: BodyEncoder[Env2, Err2, In2],
    bodyDecoder: BodyDecoder[Env2, Err2, Out2],
    driver: ZClient.Driver[Env2, Err2],
    headers: Headers = self.headers,
    method: Method = self.method,
    sslConfig: Option[ClientSSLConfig] = self.sslConfig,
    url: URL = self.url,
    version: Version = self.version,
  ): ZClient[Env2, In2, Err2, Out2] =
    ZClient(
      headers,
      method,
      sslConfig,
      url,
      version,
      bodyEncoder,
      bodyDecoder,
      driver,
    )
}

object ZClient {
  trait BodyDecoder[-Env, +Err, +Out] { self =>
    def decode(response: Response): ZIO[Env, Err, Out]

    def refineOrDie[Err2](
      pf: PartialFunction[Err, Err2],
    )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): BodyDecoder[Env, Err2, Out] =
      new BodyDecoder[Env, Err2, Out] {
        def decode(response: Response): ZIO[Env, Err2, Out] = self.decode(response).refineOrDie(pf)
      }
  }
  trait BodyEncoder[-Env, +Err, -In]  { self =>
    def encode(in: In): ZIO[Env, Err, Body]

    def refineOrDie[Err2](
      pf: PartialFunction[Err, Err2],
    )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): BodyEncoder[Env, Err2, In] =
      new BodyEncoder[Env, Err2, In] {
        def encode(in: In): ZIO[Env, Err2, Body] = self.encode(in).refineOrDie(pf)
      }
  }

  trait Driver[-Env, +Err] { self =>
    def refineOrDie[Err2](
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
        )(implicit trace: Trace): ZIO[Env, Err2, Response] =
          self.request(version, method, url, headers, body, sslConfig).refineOrDie(pf)

        override def socket[Env1 <: Env](
          app: SocketApp[Env1],
          headers: Headers,
          url: URL,
          version: Version,
        )(implicit trace: Trace): ZIO[Env1 with Scope, Err2, Response] =
          self
            .socket(
              app,
              headers,
              url,
              version,
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
    )(implicit trace: Trace): ZIO[Env, Err, Response]

    final def retry[Env1 <: Env, Err1 >: Err](policy: zio.Schedule[Env1, Err1, Any]) =
      new Driver[Env1, Err1] {
        override def request(
          version: Version,
          method: Method,
          url: URL,
          headers: Headers,
          body: Body,
          sslConfig: Option[ClientSSLConfig],
        )(implicit trace: Trace): ZIO[Env1, Err1, Response] =
          self.request(version, method, url, headers, body, sslConfig).retry(policy)

        override def socket[Env2 <: Env1](
          app: SocketApp[Env2],
          headers: Headers,
          url: URL,
          version: Version,
        )(implicit trace: Trace): ZIO[Env2 with Scope, Err1, Response] =
          self
            .socket(
              app,
              headers,
              url,
              version,
            )
            .retry(policy)
      }

    def socket[Env1 <: Env](
      app: SocketApp[Env1],
      headers: Headers,
      url: URL,
      version: Version,
    )(implicit trace: Trace): ZIO[Env1 with Scope, Err, Response]
  }

  final case class Config(
    socketApp: Option[SocketApp[Any]],
    ssl: Option[ClientSSLConfig],
    proxy: Option[zio.http.Proxy],
    useAggregator: Boolean,
    connectionPool: ConnectionPoolConfig,
    maxHeaderSize: Int,
    requestDecompression: Decompression,
    localAddress: Option[InetSocketAddress],
  ) {
    self =>
    def ssl(ssl: ClientSSLConfig): Config = self.copy(ssl = Some(ssl))

    def socketApp(socketApp: SocketApp[Any]): Config = self.copy(socketApp = Some(socketApp))

    def proxy(proxy: zio.http.Proxy): Config = self.copy(proxy = Some(proxy))

    def useObjectAggregator(objectAggregator: Boolean): Config = self.copy(useAggregator = objectAggregator)

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
          zio.Config.boolean("use-aggregator").withDefault(Config.default.useAggregator) ++
          ConnectionPoolConfig.config.nested("connection-pool").withDefault(Config.default.connectionPool) ++
          zio.Config.int("max-header-size").withDefault(Config.default.maxHeaderSize) ++
          Decompression.config.nested("request-decompression").withDefault(Config.default.requestDecompression)
      ).map { case (ssl, proxy, useAggregator, connectionPool, maxHeaderSize, requestDecompression) =>
        default.copy(
          ssl = ssl,
          proxy = proxy,
          useAggregator = useAggregator,
          connectionPool = connectionPool,
          maxHeaderSize = maxHeaderSize,
          requestDecompression = requestDecompression,
        )
      }

    lazy val default: Config = Config(
      socketApp = None,
      ssl = None,
      proxy = None,
      useAggregator = true,
      connectionPool = ConnectionPoolConfig.Disabled,
      maxHeaderSize = 8192,
      requestDecompression = Decompression.No,
      localAddress = None,
    )
  }

  final class ClientLive private (config: Config, driver: ClientBackend, connectionPool: ConnectionPool[Any])
      extends ZClient.Driver[Any, Throwable]
      with ClientRequestEncoder { self =>

    def this(driver: ClientBackend)(connectionPool: ConnectionPool[driver.Connection])(settings: Config) =
      this(settings, driver, connectionPool.asInstanceOf[ConnectionPool[Any]])

    val headers: Headers                   = Headers.empty
    val method: Method                     = Method.GET
    val sslConfig: Option[ClientSSLConfig] = config.ssl
    val url: URL                           = config.localAddress.map(_.getPort).fold(URL.empty)(URL.empty.withPort(_))
    val version: Version                   = Version.Http_1_1

    override def request(
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
      url: URL,
      version: Version,
    )(implicit trace: Trace): ZIO[R with Scope, Throwable, Response] =
      for {
        env <- ZIO.environment[R]
        res <- requestAsync(
          Request
            .get(url)
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
                        clientConfig.useAggregator,
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

  def fromDriver[Env, Err](driver: Driver[Env, Err]): ZClient[Env, Body, Err, Response] =
    ZClient(
      Headers.empty,
      Method.GET,
      None,
      URL.empty,
      Version.Http_1_1,
      new BodyEncoder[Env, Err, Body]     {
        def encode(body: Body): ZIO[Env, Err, Body] = Exit.succeed(body)
      },
      new BodyDecoder[Env, Err, Response] {
        def decode(response: Response): ZIO[Env, Err, Response] = Exit.succeed(response)
      },
      driver,
    )

  def request(
    url: String,
    method: Method = Method.GET,
    headers: Headers = Headers.empty,
    content: Body = Body.empty,
    addZioUserAgentHeader: Boolean = false,
  )(implicit trace: Trace): ZIO[Client, Throwable, Response] = {
    for {
      uri      <- ZIO.fromEither(URL.decode(url))
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

  val customized: ZLayer[Config with ClientBackend with DnsResolver, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.scoped {
      for {
        config         <- ZIO.service[Config]
        driver         <- ZIO.service[ClientBackend]
        dnsResolver    <- ZIO.service[DnsResolver]
        connectionPool <- driver.createConnectionPool(dnsResolver, config.connectionPool)
      } yield ZClient.fromDriver(new ClientLive(driver)(connectionPool)(config))
    }
  }

  val default: ZLayer[Any, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    (ZLayer.succeed(Config.default) ++ ZLayer.succeed(NettyConfig.default) ++
      DnsResolver.default) >>> live
  }

  lazy val live: ZLayer[ZClient.Config with NettyConfig with DnsResolver, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    (NettyClientBackend.live ++ ZLayer.service[DnsResolver]) >>> customized
  }.fresh

  private val zioHttpVersion: String                   = Client.getClass().getPackage().getImplementationVersion()
  private val zioHttpVersionNormalized: Option[String] = Option(zioHttpVersion)

  private val scalaVersion: String = util.Properties.versionString
  val defaultUAHeader: Headers     = Headers(
    Header.UserAgent
      .Complete(
        Header.UserAgent.Product("Zio-Http-Client", zioHttpVersionNormalized),
        Some(Header.UserAgent.Comment(s"Scala $scalaVersion")),
      )
      .untyped,
  )
}
