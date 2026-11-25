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

import scala.annotation.nowarn

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.stream.ZStream

import zio.http.Header.UserAgent
import zio.http.URL.Location
import zio.http.internal._

import com.lihaoyi.unroll

/**
 * A `ZClient` sends HTTP requests. Most code uses the type alias `Client`, which is `ZClient[Any, Scope, Body, Throwable, Response]`: it takes a [[Body]], fails with a `Throwable`, and returns a [[Response]] whose body is streamed while the `Scope` of the request is open.
 *
 * A client holds a base URL, headers, an SSL configuration and a proxy that are added to every request, a body encoder and decoder that convert the input to a `Body` and the `Response` to the output (`contramap`, `map`), and a driver that sends the request. Operators such as `url`, `addPath`, `addHeader` and `@@` return a new client. `batched` reads the whole response body before it returns, so it needs no `Scope`; requests made with `apply` or `ZClient.streaming` need one.
 *
 * Get a `Client` from a layer such as `Client.default`, `Client.configured()` or `Client.customized`.
 *
 * {{{
 * import zio._
 *
 * val userName: ZIO[Client, Throwable, String] =
 *   ZIO.serviceWithZIO[Client] { client =>
 *     client
 *       .url(url"http://localhost:8080")
 *       .addPath("users")
 *       .batched(Request.get(URL(Path("/42"))))
 *       .flatMap(_.body.asString)
 *   }
 * }}}
 *
 * @see [[https://zio.dev/zio-http/reference/client/ Client]]
 */
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
  /**
   * Sends `request`, after merging it with the client's settings, and decodes the response. The request URL is appended to the client's URL with `URL#++`, the request headers are added to the client's headers, and the client's SSL configuration and proxy are used. For a `Client`, the response body is streamed, and the effect needs a `Scope` that keeps the connection open.
   */
  @nowarn("msg=deprecated")
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

  /**
   * Returns a client whose URL has `path` appended to its path.
   */
  def addPath(path: String): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.copy(path = url.path ++ Path(path)))

  /**
   * Returns a client whose URL has `path` appended to its path.
   */
  def addPath(path: Path): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.copy(path = url.path ++ path))

  /**
   * Returns a client whose URL path starts with a slash.
   */
  def addLeadingSlash: ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.addLeadingSlash)

  /**
   * Returns a client whose URL has the query parameter `key` with `value` added.
   */
  def addQueryParam(key: String, value: String): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.copy(queryParams = url.queryParams.addQueryParam(key, value)))

  /**
   * Returns a client whose URL has `params` added to its query parameters.
   */
  def addQueryParams(params: QueryParams): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.copy(queryParams = url.queryParams ++ params))

  /**
   * Returns a client whose URL path ends with a slash.
   */
  def addTrailingSlash: ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.addTrailingSlash)

  /**
   * Returns a client whose URL is its current URL combined with `url` by `URL#++`.
   */
  def addUrl(url: URL): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = self.url ++ url)

  /**
   * Sends `request` like `apply`, and reads the whole response body before the effect completes, so no `Scope` is needed. Same as `batched.apply(request)`.
   */
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

  /**
   * Returns a client that takes inputs of type `In2` and converts them with `f` before encoding them.
   */
  def contramap[In2](f: In2 => In)(implicit trace: Trace): ZClient[Env, ReqEnv, In2, Err, Out] =
    contramapZIO(in => ZIO.succeed(f(in)))

  /**
   * Returns a client that takes inputs of type `In2` and converts them with the effect `f` before encoding them. A failure of `f` fails the request.
   */
  def contramapZIO[Err1 >: Err, In2](f: In2 => IO[Err1, In]): ZClient[Env, ReqEnv, In2, Err1, Out] =
    transform(
      self.bodyEncoder.contramapZIO(f),
      self.bodyDecoder,
      self.driver,
    )

  /**
   * Sends a `DELETE` request with an empty body to the client's URL with `suffix` appended to its path (`URL#addPath`), and decodes the response.
   */
  @nowarn("msg=deprecated")
  def delete(suffix: String)(implicit ev: Body <:< In, trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    request(Method.DELETE, suffix)(ev(Body.empty))

  /**
   * Returns a client that turns the errors for which `f` is true into defects.
   */
  def dieOn(
    f: Err => Boolean,
  )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): ZClient[Env, ReqEnv, In, Err, Out] =
    refineOrDie { case e if !f(e) => e }

  /**
   * Same as `batched`.
   */
  @deprecated("use `batched` instead", since = "3.0.0")
  def disableStreaming(implicit ev1: ReqEnv =:= Scope): ZClient[Env, Any, In, Err, Out] =
    batched

  /**
   * Sends a `GET` request with an empty body to the client's URL with `suffix` appended to its path (`URL#addPath`), and decodes the response.
   */
  @nowarn("msg=deprecated")
  def get(suffix: String)(implicit ev: Body <:< In, trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    request(Method.GET, suffix)(ev(Body.empty))

  /**
   * Sends a `HEAD` request with an empty body to the client's URL with `suffix` appended to its path (`URL#addPath`), and decodes the response.
   */
  @nowarn("msg=deprecated")
  def head(suffix: String)(implicit ev: Body <:< In, trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    request(Method.HEAD, suffix)(ev(Body.empty))

  /**
   * Returns a client whose URL has the host `host`.
   */
  def host(host: String): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.host(host))

  /**
   * Returns a client whose output is the output of this client mapped with `f`.
   */
  def map[Out2](f: Out => Out2)(implicit trace: Trace): ZClient[Env, ReqEnv, In, Err, Out2] =
    mapZIO(out => ZIO.succeed(f(out)))

  /**
   * Returns a client whose errors are mapped with `f`, in encoding, sending and decoding.
   */
  def mapError[Err2](f: Err => Err2): ZClient[Env, ReqEnv, In, Err2, Out] =
    transform(
      bodyEncoder.mapError(f),
      bodyDecoder.mapError(f),
      driver.mapError(f),
    )

  /**
   * Returns a client whose output is the output of this client mapped with the effect `f`. A failure of `f` fails the request.
   */
  def mapZIO[Env1 <: Env, Err1 >: Err, Out2](f: Out => ZIO[Env1, Err1, Out2]): ZClient[Env1, ReqEnv, In, Err1, Out2] =
    transform(
      bodyEncoder,
      bodyDecoder.mapZIO(f),
      driver,
    )

  /**
   * Returns a client whose URL has the path `path`, replacing the current path.
   */
  def path(path: String): ZClient[Env, ReqEnv, In, Err, Out] = self.path(Path(path))

  /**
   * Returns a client whose URL has the path `path`, replacing the current path.
   */
  def path(path: Path): ZClient[Env, ReqEnv, In, Err, Out] = updatePath(_ => path)

  /**
   * Returns a client whose URL path is updated with `f`.
   */
  def updatePath(f: Path => Path): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.copy(path = f(url.path)))

  /**
   * Sends a `PATCH` request with an empty body to the client's URL with `suffix` appended to its path (`URL#addPath`), and decodes the response.
   */
  @nowarn("msg=deprecated")
  def patch(suffix: String)(implicit ev: Body <:< In, trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    request(Method.PATCH, suffix)(ev(Body.empty))

  /**
   * Returns a client whose URL has the port `port`.
   */
  def port(port: Int): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.port(port))

  /**
   * Sends a `POST` request with the encoded `body` to the client's URL with `suffix` appended to its path (`URL#addPath`), and decodes the response.
   */
  @nowarn("msg=deprecated")
  def post(suffix: String)(body: In)(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    request(Method.POST, suffix)(body)

  /**
   * Sends a `PUT` request with the encoded `body` to the client's URL with `suffix` appended to its path (`URL#addPath`), and decodes the response.
   */
  @nowarn("msg=deprecated")
  def put(suffix: String)(body: In)(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    request(Method.PUT, suffix)(body)

  /**
   * Returns a client that keeps the errors for which `pf` is defined, mapped by `pf`, and turns the other errors into defects.
   */
  def refineOrDie[Err2](
    pf: PartialFunction[Err, Err2],
  )(implicit
    ev1: Err IsSubtypeOfError Throwable,
    ev2: CanFail[Err],
    trace: Trace,
  ): ZClient[Env, ReqEnv, In, Err2, Out] =
    transform(bodyEncoder.refineOrDie(pf), bodyDecoder.refineOrDie(pf), driver.refineOrDie(pf))

  /**
   * Sends `request`; same as `apply`. Use `batched`, or `ZClient.streaming` for a streamed body, instead.
   */
  @deprecated("Use `batched` or `streaming` instead", since = "3.0.0")
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

  /**
   * Sends a request with `method` and the encoded `body` to the client's URL with `suffix` appended to its path (`URL#addPath`). Use `batched`, or `ZClient.streaming` for a streamed body, instead.
   */
  @deprecated("Use `batched` or `streaming` instead", since = "3.0.0")
  def request(method: Method, suffix: String)(body: In)(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Out] =
    bodyEncoder
      .encode(body)
      .flatMap(body => bodyDecoder.decodeZIO[Env & ReqEnv, Err](requestRaw(method, suffix, body)))

  /**
   * Returns a client that sends a request again, following `policy`, when sending it fails. `Schedule.recurs(2)` allows up to three attempts.
   */
  def retry[Env1 <: Env](policy: Schedule[Env1, Err, Any]): ZClient[Env1, ReqEnv, In, Err, Out] =
    transform[Env1, ReqEnv, In, Err, Out](bodyEncoder, bodyDecoder, self.driver.retry(policy))

  /**
   * Returns a client whose URL has the scheme `scheme`.
   */
  def scheme(scheme: Scheme): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(url = url.scheme(scheme))

  /**
   * Opens a WebSocket connection to the client's URL (an `http` scheme becomes `ws`, and `https` becomes `wss`), runs `app` on the connection in a separate fiber, and returns the handshake response. The connection stays open until either side closes it or the `Scope` is closed.
   */
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
   * Connects to a WebSocket server and returns the `WebSocketChannel` of the
   * connection, so it can be used directly from the calling fiber, unlike
   * `socket`, which runs a `WebSocketApp` in a separate fiber. The connection
   * stays open until it is closed by either side or the scope is closed.
   * Incoming events are buffered until they are received.
   */
  def socketChannel(implicit trace: Trace, ev: ReqEnv =:= Scope): ZIO[Env & ReqEnv, Err, WebSocketChannel] =
    driver.socketChannel(Version.Default, url, headers, None).map(_._2)

  /**
   * Executes an HTTP request and transforms the response into a `ZStream` using
   * the provided function
   */
  @nowarn("msg=deprecated")
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

  /**
   * Returns a client that uses `ssl` as its SSL configuration, instead of the one from its [[ZClient.Config]].
   */
  def ssl(ssl: ClientSSLConfig): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(sslConfig = Some(ssl))

  /**
   * Returns a client that sends requests through `proxy`, instead of the proxy from its [[ZClient.Config]].
   */
  def proxy(proxy: Proxy): ZClient[Env, ReqEnv, In, Err, Out] =
    copy(proxy = Some(proxy))

  /**
   * Returns a client with the same URL, headers, SSL configuration and proxy, and the given body encoder, body decoder and driver.
   */
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

  /**
   * Returns a client whose URL is `uri`. When `uri` can't be converted to a [[URL]], the URL becomes `URL.empty`.
   */
  def uri(uri: URI): ZClient[Env, ReqEnv, In, Err, Out] = url(URL.fromURI(uri).getOrElse(URL.empty))

  /**
   * Returns a client whose URL is `url`, replacing the current URL.
   */
  def url(url: URL): ZClient[Env, ReqEnv, In, Err, Out] = copy(url = url)

  /**
   * Returns a client whose URL is updated with `f`.
   */
  def updateURL(f: URL => URL): ZClient[Env, ReqEnv, In, Err, Out] = copy(url = f(url))

  private def requestRaw(method: Method, suffix: String, body: Body)(implicit
    trace: Trace,
  ): ZIO[Env & ReqEnv, Err, Response] =
    driver.request(version, method, if (suffix.nonEmpty) url.addPath(suffix) else url, headers, body, sslConfig, proxy)
}

/**
 * Accessors for the `Client` service (`batched`, `streaming`, `streamingWith`, `socket`), the layers that create a `Client`, and the parts of a client: [[ZClient.Config]], [[ZClient.Driver]], [[ZClient.BodyEncoder]] and [[ZClient.BodyDecoder]].
 *
 * @see [[https://zio.dev/zio-http/reference/client/ Client]]
 */
object ZClient extends ZClientPlatformSpecific {

  /**
   * The `User-Agent` header that the client layers add when `Config.addUserAgentHeader` is true: the product `Zio-Http-Client` with the zio-http version, and the Scala version as a comment.
   */
  lazy val defaultUAHeader: UserAgent                  = UserAgent(
    UserAgent.ProductOrComment.Product("Zio-Http-Client", zioHttpVersionNormalized),
    List(UserAgent.ProductOrComment.Comment(s"Scala $scalaVersion")),
  )
  private val zioHttpVersion: String                   = BuildInfo.version
  private val zioHttpVersionNormalized: Option[String] = Option(zioHttpVersion)
  private val scalaVersion: String                     = BuildInfo.scalaVersion

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
  @nowarn("msg=deprecated")
  def batched(request: Request)(implicit trace: Trace): ZIO[Client, Throwable, Response] =
    ZIO.serviceWithZIO[Client](_.batched.request(request))

  /**
   * Creates a client that sends requests with `driver`, with an empty URL, no headers, no SSL configuration or proxy, and identity body encoder and decoder.
   */
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

  /**
   * Sends `request` with the `Client` from the environment; same as `streaming`.
   */
  @deprecated("Use `batched` or `streaming` instead", since = "3.0.0")
  @nowarn("msg=deprecated")
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
  @nowarn("msg=deprecated")
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

  /**
   * Opens a WebSocket connection with the `Client` from the environment, runs `socketApp` on it, and returns the handshake response. See `ZClient#socket`.
   */
  def socket[R](socketApp: WebSocketApp[R])(implicit trace: Trace): ZIO[R with Client & Scope, Throwable, Response] =
    ZIO.serviceWithZIO[Client](c => c.socket(socketApp))

  /**
   * Opens a WebSocket connection with the `Client` from the environment and returns its channel. See `ZClient#socketChannel`.
   */
  def socketChannel(implicit trace: Trace): ZIO[Client & Scope, Throwable, WebSocketChannel] =
    ZIO.serviceWithZIO[Client](_.socketChannel)

  /**
   * Converts the [[Response]] that a client receives into the client's output type `Out`.
   */
  trait BodyDecoder[-Env, +Err, +Out] { self =>
    /**
     * Converts `response` into an output.
     */
    def decode(response: Response)(implicit trace: Trace): ZIO[Env, Err, Out]

    /**
     * Runs `zio` and converts the response it produces into an output.
     */
    def decodeZIO[Env1 <: Env, Err1 >: Err](zio: ZIO[Env1, Err1, Response])(implicit
      trace: Trace,
    ): ZIO[Env1, Err1, Out] =
      zio.flatMap(decode)

    /**
     * Returns a decoder whose errors are mapped with `f`.
     */
    final def mapError[Err2](f: Err => Err2): BodyDecoder[Env, Err2, Out] =
      new BodyDecoder[Env, Err2, Out] {
        def decode(response: Response)(implicit trace: Trace): ZIO[Env, Err2, Out] = self.decode(response).mapError(f)
      }

    /**
     * Returns a decoder whose output is mapped with the effect `f`.
     */
    final def mapZIO[Env1 <: Env, Err1 >: Err, Out2](f: Out => ZIO[Env1, Err1, Out2]): BodyDecoder[Env1, Err1, Out2] =
      new BodyDecoder[Env1, Err1, Out2] {
        def decode(response: Response)(implicit trace: Trace): ZIO[Env1, Err1, Out2] = self.decode(response).flatMap(f)
      }

    /**
     * Returns a decoder that keeps the errors for which `pf` is defined, mapped by `pf`, and turns the others into defects.
     */
    final def refineOrDie[Err2](
      pf: PartialFunction[Err, Err2],
    )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): BodyDecoder[Env, Err2, Out] =
      new BodyDecoder[Env, Err2, Out] {
        def decode(response: Response)(implicit trace: Trace): ZIO[Env, Err2, Out] =
          self.decode(response).refineOrDie(pf)
      }

    /**
     * Changes the error type to `E1`, given evidence that `Err` is a subtype of `E1`.
     */
    final def widenError[E1](implicit ev: Err <:< E1): BodyDecoder[Env, E1, Out] =
      self.asInstanceOf[BodyDecoder[Env, E1, Out]]
  }

  /**
   * Converts the client's input type `In` into the [[Body]] of the request.
   */
  trait BodyEncoder[-Env, +Err, -In] { self =>
    /**
     * Returns an encoder that converts its input with the effect `f` before encoding it.
     */
    final def contramapZIO[Env1 <: Env, Err1 >: Err, In2](f: In2 => ZIO[Env1, Err1, In]): BodyEncoder[Env1, Err1, In2] =
      new BodyEncoder[Env1, Err1, In2] {
        def encode(in: In2)(implicit trace: Trace): ZIO[Env1, Err1, Body] = f(in).flatMap(self.encode)
      }

    /**
     * Converts `in` into a body.
     */
    def encode(in: In)(implicit trace: Trace): ZIO[Env, Err, Body]

    /**
     * Returns an encoder whose errors are mapped with `f`.
     */
    final def mapError[Err2](f: Err => Err2): BodyEncoder[Env, Err2, In] =
      new BodyEncoder[Env, Err2, In] {
        def encode(in: In)(implicit trace: Trace): ZIO[Env, Err2, Body] = self.encode(in).mapError(f)
      }

    /**
     * Returns an encoder that keeps the errors for which `pf` is defined, mapped by `pf`, and turns the others into defects.
     */
    final def refineOrDie[Err2](
      pf: PartialFunction[Err, Err2],
    )(implicit ev1: Err IsSubtypeOfError Throwable, ev2: CanFail[Err], trace: Trace): BodyEncoder[Env, Err2, In] =
      new BodyEncoder[Env, Err2, In] {
        def encode(in: In)(implicit trace: Trace): ZIO[Env, Err2, Body] = self.encode(in).refineOrDie(pf)
      }

    /**
     * Changes the error type to `E1`, given evidence that `Err` is a subtype of `E1`.
     */
    final def widenError[E1](implicit ev: Err <:< E1): BodyEncoder[Env, E1, In] =
      self.asInstanceOf[BodyEncoder[Env, E1, In]]
  }

  /**
   * Sends requests and opens WebSocket connections for a [[ZClient]]. The client layers provide a driver that uses the network; a custom driver passed to `ZClient.fromDriver` can answer requests in another way, for example in tests.
   */
  trait Driver[-Env, ReqEnv, +Err] { self =>
    /**
     * Sends `request` as it is, without an SSL configuration or proxy of its own.
     */
    final def apply(request: Request)(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Response] =
      self.request(request.version, request.method, request.url, request.headers, request.body, None, None)

    /**
     * Returns a driver that reads the whole response body before it returns, so it needs no `Scope`. Its `socket` and `socketChannel` die with an `UnsupportedOperationException`.
     */
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

        override def socketChannel(
          version: Version,
          url: URL,
          headers: Headers,
          config: Option[WebSocketConfig],
        )(implicit trace: Trace, ev: Any =:= Scope): ZIO[Env, Err, (Response, WebSocketChannel)] =
          ZIO.die(new UnsupportedOperationException("Streaming is disabled"))
      }

    /**
     * Returns a driver whose errors are mapped with `f`.
     */
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

        override def socketChannel(
          version: Version,
          url: URL,
          headers: Headers,
          config: Option[WebSocketConfig],
        )(implicit trace: Trace, ev: ReqEnv =:= Scope): ZIO[Env & ReqEnv, Err2, (Response, WebSocketChannel)] =
          self.socketChannel(version, url, headers, config).mapError(f)
      }

    /**
     * Returns a driver that keeps the errors for which `pf` is defined, mapped by `pf`, and turns the others into defects.
     */
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

        override def socketChannel(
          version: Version,
          url: URL,
          headers: Headers,
          config: Option[WebSocketConfig],
        )(implicit trace: Trace, ev: ReqEnv =:= Scope): ZIO[Env & ReqEnv, Err2, (Response, WebSocketChannel)] =
          self.socketChannel(version, url, headers, config).refineOrDie(pf)
      }

    /**
     * Sends a request built from its parts and returns the response. A [[ZClient]] passes its own SSL configuration and proxy; the network driver of the client layers uses them, when they are defined, instead of the ones from its [[ZClient.Config]].
     */
    def request(
      version: Version,
      method: Method,
      url: URL,
      headers: Headers,
      body: Body,
      sslConfig: Option[ClientSSLConfig],
      proxy: Option[Proxy],
    )(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Response]

    /**
     * Sends `req` as it is, without an SSL configuration or proxy of its own. Same as `apply`.
     */
    final def request(req: Request)(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Response] =
      request(req.version, req.method, req.url, req.headers, req.body, None, None)

    /**
     * Returns a driver that sends a request, or opens a WebSocket connection, again following `policy` when it fails.
     */
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

        override def socketChannel(
          version: Version,
          url: URL,
          headers: Headers,
          config: Option[WebSocketConfig],
        )(implicit trace: Trace, ev: ReqEnv =:= Scope): ZIO[Env1 & ReqEnv, Err1, (Response, WebSocketChannel)] =
          self.socketChannel(version, url, headers, config).retry(policy)
      }

    /**
     * Opens a WebSocket connection to `url`, runs `app` on it, and returns the handshake response.
     */
    def socket[Env1 <: Env](
      version: Version,
      url: URL,
      headers: Headers,
      app: WebSocketApp[Env1],
    )(implicit trace: Trace, ev: ReqEnv =:= Scope): ZIO[Env1 & ReqEnv, Err, Response]

    /**
     * Connects to a WebSocket server and returns the handshake response along
     * with the `WebSocketChannel` of the connection, which stays open until
     * either side closes it or the scope is closed. `config` overrides the
     * client's WebSocket configuration.
     *
     * The default implementation is based on `socket` and only exists for
     * binary compatibility with drivers that don't implement this method.
     */
    def socketChannel(
      version: Version,
      url: URL,
      headers: Headers,
      config: Option[WebSocketConfig],
    )(implicit trace: Trace, ev: ReqEnv =:= Scope): ZIO[Env & ReqEnv, Err, (Response, WebSocketChannel)] =
      Promise.make[Nothing, WebSocketChannel].flatMap { channelPromise =>
        val app = WebSocketApp(
          Handler
            .fromFunctionZIO[WebSocketChannel](channel => channelPromise.succeed(channel) *> channel.awaitShutdown),
          config,
        )
        socket[Env](version, url, headers, app).flatMap(response => channelPromise.await.map((response, _)))
      }

  }

  /**
   * Configuration of the client that the layers `Client.default`, `Client.fromConfig`, `Client.live`, `Client.configured` and `Client.customized` create. `Config.default` has no SSL configuration or proxy, a fixed connection pool of 10 connections, a maximum initial line length of 4096 and a maximum header size of 8192, no response decompression, the `User-Agent` header, an idle timeout of 50 seconds, and no connection or body read timeout.
   *
   * On Scala.js, the client sends requests with the Fetch API, and only `addUserAgentHeader` is used.
   */
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
    @unroll
    bodyReadTimeout: Option[Duration] = None,
  ) {
    self =>

    /**
     * Returns a config that adds the `User-Agent` header `ZClient.defaultUAHeader` to requests when `addUserAgentHeader` is true.
     */
    def addUserAgentHeader(addUserAgentHeader: Boolean): Config =
      self.copy(addUserAgentHeader = addUserAgentHeader)

    /**
     * Configure the timeout for reading HTTP response bodies. This timeout is
     * specifically for the time spent reading the body after headers are
     * received. If not set, defaults to idleTimeout.
     *
     * This is separate from:
     *   - connectionTimeout: Time to establish TCP connection
     *   - idleTimeout: Time channel can be idle without any data
     *
     * @param timeout
     *   The duration to wait for body data
     */
    def bodyReadTimeout(timeout: Duration): Config =
      self.copy(bodyReadTimeout = Some(timeout))

    /**
     * Returns a config with `timeout` as the time allowed to establish a connection.
     */
    def connectionTimeout(timeout: Duration): Config =
      self.copy(connectionTimeout = Some(timeout))

    /**
     * Returns a config with `timeout` as the time a connection may go without reading data before it fails.
     */
    def idleTimeout(timeout: Duration): Config =
      self.copy(idleTimeout = Some(timeout))

    /**
     * Returns a config that opens a new connection for each request instead of using a pool.
     */
    def disabledConnectionPool: Config =
      self.copy(connectionPool = ConnectionPoolConfig.Disabled)

    /**
     * Returns a config with `initialLineLength` as the maximum length of the status line of a response.
     */
    def maxInitialLineLength(initialLineLength: Int): Config = self.copy(maxInitialLineLength = initialLineLength)

    /**
     * Configure the client to use `maxHeaderSize` value when encode/decode
     * headers.
     */
    def maxHeaderSize(headerSize: Int): Config = self.copy(maxHeaderSize = headerSize)

    /**
     * Returns a config without a body read timeout.
     */
    def noBodyReadTimeout: Config = self.copy(bodyReadTimeout = None)

    /**
     * Returns a config without a connection timeout.
     */
    def noConnectionTimeout: Config = self.copy(connectionTimeout = None)

    /**
     * Returns a config without an idle timeout.
     */
    def noIdleTimeout: Config = self.copy(idleTimeout = None)

    /**
     * Returns a config that sends requests through `proxy`.
     */
    def proxy(proxy: zio.http.Proxy): Config = self.copy(proxy = Some(proxy))

    /**
     * Returns a config that decompresses response bodies: with `Decompression.Strict` when `isStrict` is true, and `Decompression.NonStrict` otherwise.
     */
    def requestDecompression(isStrict: Boolean): Config =
      self.copy(requestDecompression = if (isStrict) Decompression.Strict else Decompression.NonStrict)

    /**
     * Returns a config that uses `ssl` for HTTPS connections.
     */
    def ssl(ssl: ClientSSLConfig): Config = self.copy(ssl = Some(ssl))

    /**
     * Returns a config with a connection pool of `size` connections for each location (scheme, host and port).
     */
    def fixedConnectionPool(size: Int): Config =
      self.copy(connectionPool = ConnectionPoolConfig.Fixed(size))

    /**
     * Returns a config with a connection pool of `minimum` to `maximum` connections for each location (scheme, host and port). Connections above `minimum` are closed after they stay unused for `ttl`.
     */
    def dynamicConnectionPool(minimum: Int, maximum: Int, ttl: Duration): Config =
      self.copy(connectionPool = ConnectionPoolConfig.Dynamic(minimum = minimum, maximum = maximum, ttl = ttl))

    /**
     * Returns a config that uses `webSocketConfig` for WebSocket connections.
     */
    def webSocketConfig(webSocketConfig: WebSocketConfig): Config =
      self.copy(webSocketConfig = webSocketConfig)
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
      val cfg     =
        if (sslConfig.isDefined || proxy.isDefined)
          config.copy(ssl = sslConfig.orElse(config.ssl), proxy = proxy.orElse(config.proxy))
        else config

      requestAsync(request, cfg, None, None)
    }

    def socket[Env1](
      version: Version,
      url: URL,
      headers: Headers,
      app: WebSocketApp[Env1],
    )(implicit trace: Trace, ev: Scope =:= Scope): ZIO[Env1 & Scope, Throwable, Response] =
      for {
        env    <- ZIO.environment[Env1]
        result <- socketChannel(version, url, headers, app.customConfig)
        (response, channel) = result
        _ <- app.provideEnvironment(env).handler.runZIO(channel).ignoreLogged.interruptible.forkScoped
      } yield response

    override def socketChannel(
      version: Version,
      url: URL,
      headers: Headers,
      customConfig: Option[WebSocketConfig],
    )(implicit trace: Trace, ev: Scope =:= Scope): ZIO[Scope, Throwable, (Response, WebSocketChannel)] =
      for {
        webSocketUrl   <- url.scheme match {
          case Some(Scheme.HTTP) | Some(Scheme.WS) | None => ZIO.succeed(url.scheme(Scheme.WS))
          case Some(Scheme.WSS) | Some(Scheme.HTTPS)      => ZIO.succeed(url.scheme(Scheme.WSS))
          case _ => ZIO.fail(new IllegalArgumentException("URL's scheme MUST be WS(S) or HTTP(S)"))
        }
        scope          <- ZIO.scope
        channelPromise <- Promise.make[Nothing, WebSocketChannel]
        response       <- requestAsync(
          Request(version = version, method = Method.GET, url = webSocketUrl, headers = headers),
          customConfig.fold(config)(c => config.webSocketConfig(c)),
          Some(channelPromise),
          Some(scope),
        )
        channel        <- channelPromise.await
      } yield (response, channel)

    private def requestAsync(
      request: Request,
      clientConfig: Config,
      onWebSocketChannel: Option[Promise[Nothing, WebSocketChannel]],
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
                  channelInterface <- (onWebSocketChannel match {
                    case Some(channelPromise) =>
                      driver
                        .requestWebSocketOnChannel(
                          connection,
                          location,
                          request,
                          onResponse,
                          onComplete,
                          clientConfig.webSocketConfig,
                        )
                        .flatMap { case (channelInterface, channel) =>
                          channelPromise.succeed(channel).as(channelInterface)
                        }
                    case None                 =>
                      driver.requestOnChannel(
                        connection,
                        location,
                        request,
                        onResponse,
                        onComplete,
                        connectionPool.enableKeepAlive,
                        () => WebSocketApp.unit,
                        clientConfig.webSocketConfig,
                        clientConfig.bodyReadTimeout.map(_.toMillis),
                      )
                  }).tapErrorCause(cause => onResponse.failCause(cause))
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
              _        <- ZIO.addFinalizer(onComplete.interrupt)
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

  /**
   * Instances of [[ZClient.BodyDecoder]].
   */
  object BodyDecoder {
    /**
     * The decoder that returns the response unchanged.
     */
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

  /**
   * Instances of [[ZClient.BodyEncoder]].
   */
  object BodyEncoder {
    /**
     * The encoder that uses its input body unchanged.
     */
    val identity: BodyEncoder[Any, Nothing, Body] =
      new BodyEncoder[Any, Nothing, Body] {
        def encode(body: Body)(implicit trace: Trace): ZIO[Any, Nothing, Body] = Exit.succeed(body)
      }
  }

  /**
   * Default values and the `zio.Config` description of [[ZClient.Config]].
   */
  object Config {
    /**
     * The default client configuration. See [[ZClient.Config]] for its values.
     */
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
      bodyReadTimeout = None, // Defaults to idleTimeout when None
    )

    /**
     * Reads a [[ZClient.Config]] with ZIO Config, from the keys `ssl`, `proxy`, `connection-pool`, `max-initial-line-length`, `max-header-size`, `request-decompression`, `add-user-agent-header`, `idle-timeout`, `connection-timeout` and `body-read-timeout`. A missing key takes its value from `Config.default`, and `localAddress` and `webSocketConfig` always do.
     */
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
          zio.Config.duration("connection-timeout").optional.withDefault(Config.default.connectionTimeout) ++
          zio.Config.duration("body-read-timeout").optional.withDefault(Config.default.bodyReadTimeout)
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
              bodyReadTimeout,
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
            bodyReadTimeout = bodyReadTimeout,
          )
      }
  }
}
