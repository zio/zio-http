package zio.http

import zio._
import zio.http.model.{Headers, Method, Scheme, Status, Version}
import zio.http.socket.SocketApp

/**
 * Enables tests that use a client without needing a live Server
 *
 * @param behavior
 *   Contains the user-specified behavior that takes the place of the usual
 *   Server
 */
case class TestClient(behavior: Ref[HttpApp[Any, Throwable]]) extends Client {

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
    val handler: PartialFunction[Request, ZIO[Any, Throwable, Response]] = {

      case realRequest if {
            // The way that the Client breaks apart and re-assembles the request prevents a straightforward
            //    expectedRequest == realRequest
            expectedRequest.url.relative == realRequest.url &&
            expectedRequest.method == realRequest.method &&
            expectedRequest.headers.toSet.forall(expectedHeader => realRequest.headers.toSet.contains(expectedHeader))
          } =>
        ZIO.succeed(response)
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
    handler: PartialFunction[Request, ZIO[R, Throwable, Response]],
  ): ZIO[R, Nothing, Unit] =
    for {
      r                <- ZIO.environment[R]
      previousBehavior <- behavior.get
      newBehavior                  = handler.andThen(_.provideEnvironment(r))
      app: HttpApp[Any, Throwable] = Http.collectZIO(newBehavior)
      _ <- behavior.set(previousBehavior ++ app)
    } yield ()

  val headers: Headers                   = Headers.empty
  val hostOption: Option[String]         = None
  val pathPrefix: Path                   = Path.empty
  val portOption: Option[Int]            = None
  val queries: QueryParams               = QueryParams.empty
  val schemeOption: Option[Scheme]       = None
  val sslConfig: Option[ClientSSLConfig] = None

  override protected def requestInternal(
    body: Body,
    headers: Headers,
    hostOption: Option[String],
    method: Method,
    pathPrefix: Path,
    portOption: Option[Int],
    queries: QueryParams,
    schemeOption: Option[Scheme],
    sslConfig: Option[ClientSSLConfig],
    version: Version,
  )(implicit trace: Trace): ZIO[Any, Throwable, Response] =
    for {
      currentBehavior <- behavior.get
      request = Request(
        body = body,
        headers = headers,
        method = method,
        url = URL(pathPrefix, kind = URL.Location.Relative),
        version = version,
        remoteAddress = None,
      )
      response <- currentBehavior(request).catchAll {
        case Some(value) => ZIO.succeed(Response(status = Status.BadRequest, body = Body.fromString(value.toString)))
        case None        => ZIO.succeed(Response.status(Status.NotFound))
      }
    } yield response

  override protected def socketInternal[Env1 <: Any](
    app: SocketApp[Env1],
    headers: Headers,
    hostOption: Option[String],
    pathPrefix: Path,
    portOption: Option[Int],
    queries: QueryParams,
    schemeOption: Option[Scheme],
    version: Version,
  )(implicit trace: Trace): ZIO[Env1 with Scope, Throwable, Response] = ???
}

object TestClient {

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
    handler: PartialFunction[Request, ZIO[R, Throwable, Response]],
  ): ZIO[R with TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.addHandler(handler))

  val layer: ZLayer[Any, Throwable, TestClient] =
    ZLayer.scoped {
      for {
        behavior <- Ref.make[HttpApp[Any, Throwable]](Http.empty)
      } yield TestClient(behavior)
    }
}
