package zio.http

import zio._

import zio.http.ChannelEvent.{Unregistered, UserEvent}
import zio.http.{Headers, Method, Scheme, Status, Version}

/**
 * Enables tests that use a client without needing a live Server
 *
 * @param behavior
 *   Contains the user-specified behavior that takes the place of the usual
 *   Server
 */
final case class TestClient(
  behavior: Ref[PartialFunction[Request, ZIO[Any, Response, Response]]],
  serverSocketBehavior: Ref[SocketApp[Any]],
) extends ZClient.Driver[Any, Throwable] {

  /**
   * Adds an exact 1-1 behavior
   * @param expectedRequest
   *   The request that will trigger the response
   * @param response
   *   The response to be returned when a user submits the response
   *
   * @example
   *   {{{
   *    TestClient.addRequestResponse(Request.get(URL.root), Response.ok)
   *   }}}
   */
  def addRequestResponse(
    expectedRequest: Request,
    response: Response,
  ): ZIO[Any, Nothing, Unit] = {
    val handler = new PartialFunction[Request, ZIO[Any, Response, Response]] {

      def isDefinedAt(realRequest: Request): Boolean = {
        // The way that the Client breaks apart and re-assembles the request prevents a straightforward
        //    expectedRequest == realRequest
        val defined = expectedRequest.url.relative == realRequest.url &&
          expectedRequest.method == realRequest.method &&
          expectedRequest.headers.toSet.forall(expectedHeader => realRequest.headers.toSet.contains(expectedHeader))

        defined
      }

      def apply(request: Request): ZIO[Any, Response, Response] =
        if (!isDefinedAt(request))
          throw new MatchError(s"TestClient received unexpected request: $request (expected: $expectedRequest)")
        else ZIO.succeed(response)
    }
    addHandler(handler)
  }

  /**
   * Adds a flexible handler for requests that are submitted by test cases
   * @param handler
   *   New behavior to be added to the TestClient
   * @tparam R
   *   Environment of the new handler's effect.
   *
   * @example
   *   {{{
   *    TestClient.addHandler{case request  if request.method == Method.GET => ZIO.succeed(Response.ok)}
   *   }}}
   */
  def addHandler[R](
    handler: PartialFunction[Request, ZIO[R, Response, Response]],
  ): ZIO[R, Nothing, Unit] =
    for {
      r <- ZIO.environment[R]
      newBehavior = handler.andThen(_.provideEnvironment(r))
      _ <- behavior.update(_.orElse(newBehavior))
    } yield ()

  def headers: Headers = Headers.empty

  def method: Method = Method.GET

  def sslConfig: Option[ClientSSLConfig] = None

  def url: URL = URL(Path.root)

  def version: Version = Version.Http_1_1

  override def request(
    version: Version,
    method: Method,
    url: URL,
    headers: Headers,
    body: Body,
    sslConfig: Option[zio.http.ClientSSLConfig],
  )(implicit trace: Trace): ZIO[Any, Throwable, Response] = {
    val notFound: PartialFunction[Request, ZIO[Any, Response, Response]] = { case _: Request =>
      ZIO.succeed(Response.notFound)
    }

    for {
      currentBehavior <- behavior.get.map(_.orElse(notFound))
      request = Request(
        body = body,
        headers = headers,
        method = if (method == Method.ANY) Method.GET else method,
        url = url.relative,
        version = version,
        remoteAddress = None,
      )
      _        <- ZIO.when(!currentBehavior.isDefinedAt(request)) {
        ZIO.fail(new Throwable(s"TestClient does not have a handler for $request"))
      }
      response <- currentBehavior(request).merge
    } yield response
  }

  def socket[Env1](
    version: Version,
    url: URL,
    headers: Headers,
    app: SocketApp[Env1],
  )(implicit trace: Trace): ZIO[Env1 with Scope, Throwable, Response] = {
    for {
      env                   <- ZIO.environment[Env1]
      currentSocketBehavior <- serverSocketBehavior.get
      in                    <- Queue.unbounded[WebSocketChannelEvent]
      out                   <- Queue.unbounded[WebSocketChannelEvent]
      promise               <- Promise.make[Nothing, Unit]
      testChannelClient     <- TestChannel.make(in, out, promise)
      testChannelServer     <- TestChannel.make(out, in, promise)
      _                     <- currentSocketBehavior.runZIO(testChannelClient).forkDaemon
      _                     <- app.provideEnvironment(env).runZIO(testChannelServer).forkDaemon
    } yield Response.status(Status.SwitchingProtocols)
  }

  def installSocketApp[Env1](
    app: Handler[Any, Throwable, WebSocketChannel, Unit],
  ): ZIO[Env1, Nothing, Unit] =
    for {
      env <- ZIO.environment[Env1]
      _   <- serverSocketBehavior.set(
        app
          .provideEnvironment(env),
      )
    } yield ()
}

object TestClient {

  /**
   * Adds an exact 1-1 behavior
   * @param request
   *   The request that will trigger the response
   * @param response
   *   The response to be returned when a user submits the response
   *
   * @example
   *   {{{
   *    TestClient.addRequestResponse(Request.get(URL.root), Response.ok)
   *   }}}
   */
  def addRequestResponse(
    request: Request,
    response: Response,
  ): ZIO[TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.addRequestResponse(request, response))

  /**
   * Adds a flexible handler for requests that are submitted by test cases
   * @param handler
   *   New behavior to be added to the TestClient
   * @tparam R
   *   Environment of the new handler's effect.
   *
   * @example
   *   {{{
   *    TestClient.addHandler{case request  if request.method == Method.GET => ZIO.succeed(Response.ok)}
   *   }}}
   */
  def addHandler[R](
    handler: PartialFunction[Request, ZIO[R, Response, Response]],
  ): ZIO[R with TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.addHandler(handler))

  def installSocketApp(
    app: Handler[Any, Throwable, WebSocketChannel, Unit],
  ): ZIO[TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.installSocketApp(app))

  val layer: ZLayer[Any, Nothing, TestClient & Client] =
    ZLayer.scopedEnvironment {
      for {
        behavior       <- Ref.make[PartialFunction[Request, ZIO[Any, Response, Response]]](PartialFunction.empty)
        socketBehavior <- Ref.make[SocketApp[Any]](Handler.unit)
        driver = TestClient(behavior, socketBehavior)
      } yield ZEnvironment[TestClient, Client](driver, ZClient.fromDriver(driver))
    }

}
