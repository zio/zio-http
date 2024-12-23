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
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.stream.ZStream

import zio.http.Header.UserAgent
import zio.http.URL.Location
import zio.http.internal._

final case class ZClient[-Env, ReqEnv, -In, +Err, +Out](
  version: Version,
  url: URL,
  headers: Headers,
  sslConfig: Option[ClientSSLConfig],
  proxy: Option[Proxy],
  bodyEncoder: ZClient.BodyEncoder[Env, Err, In],
  bodyDecoder: ZClient.BodyDecoder[Env, Err, Out],
  driver: ZClient.Driver[Env, ReqEnv, Err],
) extends HeaderOps[ZClient[Env, ReqEnv, In, Err, Out]] { self =>
  def apply(request: Request)(implicit ev: Body <:< In, trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    self.request(request)

  override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): ZClient[Env, ReqEnv, In, Err, Out] =
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
  ): ZClient[UpperEnv, ReqEnv, UpperIn, LowerErr, LowerOut] =
    aspect(self)

  def addPath(path: String): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.copy(path = url.path ++ Path(path)))

  def addPath(path: Path): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.copy(path = url.path ++ path))

  def addLeadingSlash: ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.addLeadingSlash)

  def addQueryParam(key: String, value: String): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.copy(queryParams = url.queryParams.addQueryParam(key, value)))

  def addQueryParams(params: QueryParams): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.copy(queryParams = url.queryParams ++ params))

  def addTrailingSlash: ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.addTrailingSlash)

  def addUrl(url: URL): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = self.url ++ url)

  def batched(
    request: Request,
  )(implicit trace: Trace, ev1: ReqEnv =:= Scope, ev3: Body <:< In): ZIO[Env, Err, Out] =
    batched.apply(request)

  /**
   * Converts this streaming client into a batched client.
   *
   * '''NOTE''': This client will materialize the responses into memory. If the
   * response is streaming, it will await for it to be fully collected before
   * resuming.
   */
  def batched(implicit ev1: ReqEnv =:= Scope): ZClient[Env, Any, In, Err, Out] =
    self.transform[Env, Any, In, Err, Out](
      self.bodyEncoder,
      self.bodyDecoder,
      self.driver.disableStreaming,
    )

  def contramap[In2](f: In2 => In)(implicit trace: Trace): ZClient[Env, ReqEnv, In2, Err, Out] =
    contramapZIO(in => ZIO.succeed(f(in)))

  def contramapZIO[Err1 >: Err, In2](f: In2 => IO[Err1, In]): ZClient[Env, ReqEnv, In2, Err1, Out] =
    transform(
      self.bodyEncoder.contramapZIO(f),
      self.bodyDecoder,
      self.driver,
    )

  def delete(suffix: String)(implicit ev: Body <:< In, trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    request(Method.DELETE, suffix)(ev(Body.empty))

  def dieOn(
    f: Err => Boolean,
  )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): ZClient[Env, ReqEnv, In, Err, Out] =
    refineOrDie { case e if !f(e) => e }

  @deprecated("use `batched` instead", since = "3.0.0")
  def disableStreaming(implicit ev1: ReqEnv =:= Scope): ZClient[Env, Any, In, Err, Out] =
    batched

  def get(suffix: String)(implicit ev: Body <:< In, trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    request(Method.GET, suffix)(ev(Body.empty))

  def head(suffix: String)(implicit ev: Body <:< In, trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    request(Method.HEAD, suffix)(ev(Body.empty))

  def host(host: String): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.host(host))

  def map[Out2](f: Out => Out2)(implicit trace: Trace): ZClient[Env, ReqEnv, In, Err, Out2] =
    mapZIO(out => ZIO.succeed(f(out)))

  def mapError[Err2](f: Err => Err2): ZClient[Env, ReqEnv, In, Err2, Out] =
    transform(
      bodyEncoder.mapError(f),
      bodyDecoder.mapError(f),
      driver.mapError(f),
    )

  def mapZIO[Env1 <: Env, Err1 >: Err, Out2](f: Out => ZIO[Env1, Err1, Out2]): ZClient[Env1, ReqEnv, In, Err1, Out2] =
    transform(
      bodyEncoder,
      bodyDecoder.mapZIO(f),
      driver,
    )

  def path(path: String): ZClient[Env, ReqEnv, In, Err, Out] = self.path(Path(path))

  def path(path: Path): ZClient[Env, ReqEnv, In, Err, Out] = updatePath(_ => path)

  def updatePath(f: Path => Path): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.copy(path = f(url.path)))

  def patch(suffix: String)(implicit ev: Body <:< In, trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    request(Method.PATCH, suffix)(ev(Body.empty))

  def port(port: Int): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.port(port))

  def post(suffix: String)(body: In)(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    request(Method.POST, suffix)(body)

  def put(suffix: String)(body: In)(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    request(Method.PUT, suffix)(body)

  def refineOrDie[Err2](
    pf: PartialFunction[Err, Err2],
  )(implicit
    ev1: Err IsSubtypeOfError Throwable,
    ev2: CanFail[Err],
    trace: Trace,
  ): ZClient[Env, ReqEnv, In, Err2, Out] =
    transform(bodyEncoder.refineOrDie(pf), bodyDecoder.refineOrDie(pf), driver.refineOrDie(pf))

  def request(request: Request)(implicit ev: Body <:< In, trace: Trace): ZIO[Env & ReqEnv, Err, Out] = {
    def makeRequest(body: Body) = {
      driver.request(
        self.version ++ request.version,
        request.method,
        self.url ++ request.url,
        self.headers ++ request.headers,
        body,
        sslConfig,
        proxy,
      )
    }
    if (bodyEncoder == ZClient.BodyEncoder.identity)
      bodyDecoder.decodeZIO(makeRequest(request.body))
    else
      bodyEncoder
        .encode(ev(request.body))
        .flatMap(body => bodyDecoder.decodeZIO(makeRequest(body)))
  }

  def request(method: Method, suffix: String)(body: In)(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    bodyEncoder
      .encode(body)
      .flatMap(body => bodyDecoder.decodeZIO[Env & ReqEnv, Err](requestRaw(method, suffix, body)))

  private def requestRaw(method: Method, suffix: String, body: Body)(implicit
    trace: Trace,
  ): ZIO[Env & ReqEnv, Err, Response] =
    driver.request(version, method, if (suffix.nonEmpty) url.addPath(suffix) else url, headers, body, sslConfig, proxy)

  def retry[Env1 <: Env](policy: Schedule[Env1, Err, Any]): ZClient[Env1, ReqEnv, In, Err, Out] =
    transform[Env1, ReqEnv, In, Err, Out](bodyEncoder, bodyDecoder, self.driver.retry(policy))

  def scheme(scheme: Scheme): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.scheme(scheme))

  def socket[Env1 <: Env](
    app: WebSocketApp[Env1],
  )(implicit trace: Trace, ev: ReqEnv =:= Scope): ZIO[Env1 & ReqEnv, Err, Out] =
    driver
      .socket(
        Version.Default,
        url,
        headers,
        app,
      )
      .flatMap(bodyDecoder.decode)

  /**
   * Executes an HTTP request and transforms the response into a `ZStream` using
   * the provided function
   */
  def stream[R, E0 >: Err, A](request: Request)(f: Out => ZStream[R, E0, A])(implicit
    trace: Trace,
    ev1: Body <:< In,
    ev2: ReqEnv =:= Scope,
  ): ZStream[R & Env, E0, A] = ZStream.unwrapScoped[R & Env] {
    self
      .request(request)
      .asInstanceOf[ZIO[R & Env & Scope, Err, Out]]
      .fold(ZStream.fail(_), f)
  }

  def ssl(ssl: ClientSSLConfig): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(sslConfig = Some(ssl))

  def proxy(proxy: Proxy): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(proxy = Some(proxy))

  def transform[Env2, S2, In2, Err2, Out2](
    bodyEncoder: ZClient.BodyEncoder[Env2, Err2, In2],
    bodyDecoder: ZClient.BodyDecoder[Env2, Err2, Out2],
    driver: ZClient.Driver[Env2, S2, Err2],
  ): ZClient[Env2, S2, In2, Err2, Out2] =
    ZClient(
      version,
      url,
      headers,
      sslConfig,
      proxy,
      bodyEncoder,
      bodyDecoder,
      driver,
    )

  def uri(uri: URI): ZClient[Env, ReqEnv, In, Err, Out] = url(URL.fromURI(uri).getOrElse(URL.empty))

  def url(url: URL): ZClient[Env, ReqEnv, In, Err, Out] = copy(url = url)

  def updateURL(f: URL => URL): ZClient[Env, ReqEnv, In, Err, Out] = copy(url = f(url))
}

object ZClient extends ZClientPlatformSpecific {

  /**
   * Executes an HTTP request and extracts the response
   *
   * '''NOTE''': This method materializes the full response into memory. If the
   * response is streaming, it will await for it to be fully collected before
   * resuming.
   *
   * @see
   *   [[streaming]] for a variant that doesn't materialize the response body in
   *   memory, allowing to stream response bodies
   */
  def batched(request: Request)(implicit trace: Trace): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.batched.request(request))

  def fromDriver[Env, ReqEnv, Err](driver: Driver[Env, ReqEnv, Err]): ZClient[Env, ReqEnv, Body, Err, Response] =
    ZClient(
      Version.Default,
      URL.empty,
      Headers.empty,
      None,
      None,
      BodyEncoder.identity,
      BodyDecoder.identity,
      driver,
    )

  @deprecated("Use `batched` or `streaming` instead", since = "3.0.0")
  def request(request: Request)(implicit trace: Trace): ZIO[Client & Scope, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.request(request))

  /**
   * Executes an HTTP request and extracts the response.
   *
   * It is the responsibility of the user to ensure that the resources
   * associated with the request are properly finalized via the returned
   * `Scope`.
   *
   * '''NOTE''': The `Scope` must be closed ''after'' the body has been
   * collected.
   *
   * @see
   *   [[batched]] for a variant that doesn't require manual handling of the
   *   request's resources (i.e., `Scope`)
   */
  def streaming(request: Request)(implicit trace: Trace): ZIO[Client & Scope, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.request(request))

  /**
   * Executes an HTTP request, and transforms the response to a `ZStream` using
   * the provided function. The resources associated with this request will be
   * automatically cleaned up when the `ZStream` terminates.
   *
   * This method does not materialize the response body in memory, so it will
   * resume as soon as the response is received, and it is safe to use with
   * large response bodies
   */
  def streamingWith[R, A](request: Request)(f: Response => ZStream[R, Throwable, A])(implicit
    trace: Trace,
  ): ZStream[R & Client, Throwable, A] =
    ZStream.serviceWithStream[Client](_.stream(request)(f))

  def socket[R](socketApp: WebSocketApp[R])(implicit trace: Trace): ZIO[R with Client & Scope, Throwable, Response] =
    ZIO.serviceWithZIO[Client](c => c.socket(socketApp))

  trait BodyDecoder[-Env, +Err, +Out] { self =>
    def decode(response: Response)(implicit trace: Trace): ZIO[Env, Err, Out]

    def decodeZIO[Env1 <: Env, Err1 >: Err](zio: ZIO[Env1, Err1, Response])(implicit
      trace: Trace,
    ): ZIO[Env1, Err1, Out] =
      zio.flatMap(decode)

    final def mapError[Err2](f: Err => Err2): BodyDecoder[Env, Err2, Out] =
      new BodyDecoder[Env, Err2, Out] {
        def decode(response: Response)(implicit trace: Trace): ZIO[Env, Err2, Out] = self.decode(response).mapError(f)
      }

    final def mapZIO[Env1 <: Env, Err1 >: Err, Out2](f: Out => ZIO[Env1, Err1, Out2]): BodyDecoder[Env1, Err1, Out2] =
      new BodyDecoder[Env1, Err1, Out2] {
        def decode(response: Response)(implicit trace: Trace): ZIO[Env1, Err1, Out2] = self.decode(response).flatMap(f)
      }

    final def refineOrDie[Err2](
      pf: PartialFunction[Err, Err2],
    )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): BodyDecoder[Env, Err2, Out] =
      new BodyDecoder[Env, Err2, Out] {
        def decode(response: Response)(implicit trace: Trace): ZIO[Env, Err2, Out] =
          self.decode(response).refineOrDie(pf)
      }

    final def widenError[E1](implicit ev: Err <:< E1): BodyDecoder[Env, E1, Out] =
      self.asInstanceOf[BodyDecoder[Env, E1, Out]]
  }
  object BodyDecoder                  {
    val identity: BodyDecoder[Any, Nothing, Response] =
      new BodyDecoder[Any, Nothing, Response] {
        final def decode(response: Response)(implicit trace: Trace): ZIO[Any, Nothing, Response] =
          Exit.succeed(response)

        override def decodeZIO[Env1 <: Any, Err1 >: Nothing](
          zio: ZIO[Env1, Err1, Response],
        )(implicit trace: Trace): ZIO[Env1, Err1, Response] =
          zio
      }
  }
  trait BodyEncoder[-Env, +Err, -In]  { self =>
    final def contramapZIO[Env1 <: Env, Err1 >: Err, In2](f: In2 => ZIO[Env1, Err1, In]): BodyEncoder[Env1, Err1, In2] =
      new BodyEncoder[Env1, Err1, In2] {
        def encode(in: In2)(implicit trace: Trace): ZIO[Env1, Err1, Body] = f(in).flatMap(self.encode)
      }

    def encode(in: In)(implicit trace: Trace): ZIO[Env, Err, Body]

    final def mapError[Err2](f: Err => Err2): BodyEncoder[Env, Err2, In] =
      new BodyEncoder[Env, Err2, In] {
        def encode(in: In)(implicit trace: Trace): ZIO[Env, Err2, Body] = self.encode(in).mapError(f)
      }

    final def refineOrDie[Err2](
      pf: PartialFunction[Err, Err2],
    )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): BodyEncoder[Env, Err2, In] =
      new BodyEncoder[Env, Err2, In] {
        def encode(in: In)(implicit trace: Trace): ZIO[Env, Err2, Body] = self.encode(in).refineOrDie(pf)
      }

    final def widenError[E1](implicit ev: Err <:< E1): BodyEncoder[Env, E1, In] =
      self.asInstanceOf[BodyEncoder[Env, E1, In]]
  }
  object BodyEncoder                  {
    val identity: BodyEncoder[Any, Nothing, Body] =
      new BodyEncoder[Any, Nothing, Body] {
        def encode(body: Body)(implicit trace: Trace): ZIO[Any, Nothing, Body] = Exit.succeed(body)
      }
  }

  trait Driver[-Env, ReqEnv, +Err] { self =>
    final def apply(request: Request)(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Response] =
      self.request(request.version, request.method, request.url, request.headers, request.body, None, None)

    def disableStreaming(implicit ev1: ReqEnv =:= Scope): Driver[Env, Any, Err] =
      new Driver[Env, Any, Err] {

        private val self0 = self.asInstanceOf[Driver[Env, Scope, Err]]

        override def request(
          version: Version,
          method: Method,
          url: URL,
          headers: Headers,
          body: Body,
          sslConfig: Option[ClientSSLConfig],
          proxy: Option[Proxy],
        )(implicit trace: Trace): ZIO[Env, Err, Response] =
          ZIO.scoped[Env] {
            self0.request(version, method, url, headers, body, sslConfig, proxy).flatMap(_.collect)
          }

        // This should never be possible to invoke unless the user unsafely casted the Driver environment
        override def socket[Env1 <: Env](version: Version, url: URL, headers: Headers, app: WebSocketApp[Env1])(implicit
          trace: Trace,
          ev: Any =:= Scope,
        ): ZIO[Env1 & Any, Err, Response] =
          ZIO.die(new UnsupportedOperationException("Streaming is disabled"))
      }

    final def mapError[Err2](f: Err => Err2): Driver[Env, ReqEnv, Err2] =
      new Driver[Env, ReqEnv, Err2] {
        override def request(
          version: Version,
          method: Method,
          url: URL,
          headers: Headers,
          body: Body,
          sslConfig: Option[ClientSSLConfig],
          proxy: Option[Proxy],
        )(implicit trace: Trace): ZIO[Env & ReqEnv, Err2, Response] =
          self.request(version, method, url, headers, body, sslConfig, proxy).mapError(f)

        override def socket[Env1 <: Env](
          version: Version,
          url: URL,
          headers: Headers,
          app: WebSocketApp[Env1],
        )(implicit trace: Trace, ev: ReqEnv =:= Scope): ZIO[Env1 & ReqEnv, Err2, Response] =
          self.socket(version, url, headers, app).mapError(f)
      }

    final def refineOrDie[Err2](
      pf: PartialFunction[Err, Err2],
    )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): Driver[Env, ReqEnv, Err2] =
      new Driver[Env, ReqEnv, Err2] {
        override def request(
          version: Version,
          method: Method,
          url: URL,
          headers: Headers,
          body: Body,
          sslConfig: Option[ClientSSLConfig],
          proxy: Option[Proxy],
        )(implicit trace: Trace): ZIO[Env & ReqEnv, Err2, Response] =
          self.request(version, method, url, headers, body, sslConfig, proxy).refineOrDie(pf)

        override def socket[Env1 <: Env](
          version: Version,
          url: URL,
          headers: Headers,
          app: WebSocketApp[Env1],
        )(implicit trace: Trace, ev: ReqEnv =:= Scope): ZIO[Env1 & ReqEnv, Err2, Response] =
          self.socket(version, url, headers, app).refineOrDie(pf)
      }

    def request(
      version: Version,
      method: Method,
      url: URL,
      headers: Headers,
      body: Body,
      sslConfig: Option[ClientSSLConfig],
      proxy: Option[Proxy],
    )(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Response]

    final def request(req: Request)(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Response] =
      request(req.version, req.method, req.url, req.headers, req.body, None, None)

    final def retry[Env1 <: Env, Err1 >: Err](policy: zio.Schedule[Env1, Err1, Any]) =
      new Driver[Env1, ReqEnv, Err1] {
        override def request(
          version: Version,
          method: Method,
          url: URL,
          headers: Headers,
          body: Body,
          sslConfig: Option[ClientSSLConfig],
          proxy: Option[Proxy],
        )(implicit trace: Trace): ZIO[Env1 & ReqEnv, Err1, Response] =
          self.request(version, method, url, headers, body, sslConfig, proxy).retry(policy)

        override def socket[Env2 <: Env1](
          version: Version,
          url: URL,
          headers: Headers,
          app: WebSocketApp[Env2],
        )(implicit trace: Trace, ev: ReqEnv =:= Scope): ZIO[Env2 & ReqEnv, Err1, Response] =
          self.socket(version, url, headers, app).retry(policy)
      }

    def socket[Env1 <: Env](
      version: Version,
      url: URL,
      headers: Headers,
      app: WebSocketApp[Env1],
    )(implicit trace: Trace, ev: ReqEnv =:= Scope): ZIO[Env1 & ReqEnv, Err, Response]

  }

  final case class Config(
    ssl: Option[ClientSSLConfig],
    proxy: Option[zio.http.Proxy],
    connectionPool: ConnectionPoolConfig,
    maxInitialLineLength: Int,
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

    def maxInitialLineLength(initialLineLength: Int): Config = self.copy(maxInitialLineLength = initialLineLength)

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
    def config: zio.Config[Config] =
      (
        ClientSSLConfig.config.nested("ssl").optional.withDefault(Config.default.ssl) ++
          zio.http.Proxy.config.nested("proxy").optional.withDefault(Config.default.proxy) ++
          ConnectionPoolConfig.config.nested("connection-pool").withDefault(Config.default.connectionPool) ++
          zio.Config.int("max-initial-line-length").withDefault(Config.default.maxInitialLineLength) ++
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
              maxInitialLineLength,
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
            maxInitialLineLength = maxInitialLineLength,
            maxHeaderSize = maxHeaderSize,
            requestDecompression = requestDecompression,
            addUserAgentHeader = addUserAgentHeader,
            idleTimeout = idleTimeout,
            connectionTimeout = connectionTimeout,
          )
      }

    val default: Config = Config(
      ssl = None,
      proxy = None,
      connectionPool = ConnectionPoolConfig.Fixed(10),
      maxInitialLineLength = 4096,
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
  ) extends ZClient.Driver[Any, Scope, Throwable] { self =>

    def this(driver: ClientDriver)(connectionPool: ConnectionPool[driver.Connection])(settings: Config) =
      this(settings, driver, connectionPool.asInstanceOf[ConnectionPool[Any]])

    def request(
      version: Version,
      method: Method,
      url: URL,
      headers: Headers,
      body: Body,
      sslConfig: Option[ClientSSLConfig],
      proxy: Option[Proxy],
    )(implicit trace: Trace): ZIO[Scope, Throwable, Response] = {
      val requestHeaders = body.mediaType match {
        case None        => headers
        case Some(value) => headers.removeHeader(Header.ContentType).addHeader(Header.ContentType(value))
      }

      val request = Request(version, method, url, requestHeaders, body, None)
      val cfg     = config.copy(ssl = sslConfig.orElse(config.ssl), proxy = proxy.orElse(config.proxy))

      requestAsync(request, cfg, () => WebSocketApp.unit, None)
    }

    def socket[Env1](
      version: Version,
      url: URL,
      headers: Headers,
      app: WebSocketApp[Env1],
    )(implicit trace: Trace, ev: Scope =:= Scope): ZIO[Env1 & Scope, Throwable, Response] =
      for {
        env          <- ZIO.environment[Env1]
        webSocketUrl <- url.scheme match {
          case Some(Scheme.HTTP) | Some(Scheme.WS) | None => ZIO.succeed(url.scheme(Scheme.WS))
          case Some(Scheme.WSS) | Some(Scheme.HTTPS)      => ZIO.succeed(url.scheme(Scheme.WSS))
          case _ => ZIO.fail(new IllegalArgumentException("URL's scheme MUST be WS(S) or HTTP(S)"))
        }
        scope        <- ZIO.scope
        res          <- requestAsync(
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
              connectionAcquired <- Ref.make(false)
              onComplete         <- Promise.make[Throwable, ChannelState]
              onResponse         <- Promise.make[Throwable, Response]
              inChannelScope = outerScope match {
                case Some(scope) => (zio: ZIO[Scope, Throwable, Unit]) => scope.extend(zio)
                case None        => (zio: ZIO[Scope, Throwable, Unit]) => ZIO.scoped(zio)
              }
              channelFiber <- inChannelScope {
                for {
                  connection       <- restore(
                    connectionPool
                      .get(
                        location,
                        clientConfig.proxy,
                        clientConfig.ssl.getOrElse(ClientSSLConfig.Default),
                        clientConfig.maxInitialLineLength,
                        clientConfig.maxHeaderSize,
                        clientConfig.requestDecompression,
                        clientConfig.idleTimeout,
                        clientConfig.connectionTimeout,
                        clientConfig.localAddress,
                      ),
                  )
                    .zipLeft(connectionAcquired.set(true))
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
                        channelInterface.interrupt.ignore
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
                ZIO.unlessZIO(connectionAcquired.get)(channelFiber.interrupt) *>
                  onComplete.interrupt *>
                  channelFiber.await
              })
            } yield response
          }
        case Location.Relative           =>
          ZIO.fail(new IllegalArgumentException("Absolute URL is required"))
      }
  }

  private val zioHttpVersion: String                   = BuildInfo.version
  private val zioHttpVersionNormalized: Option[String] = Option(zioHttpVersion)

  private val scalaVersion: String    = BuildInfo.scalaVersion
  lazy val defaultUAHeader: UserAgent = UserAgent(
    UserAgent.ProductOrComment.Product("Zio-Http-Client", zioHttpVersionNormalized),
    List(UserAgent.ProductOrComment.Comment(s"Scala $scalaVersion")),
  )
}
