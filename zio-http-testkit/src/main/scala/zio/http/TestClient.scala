package zio.http

import zio._
import zio.http.model.{Headers, Method, Scheme, Status, Version}
import zio.http.socket.SocketApp

case class TestClient(behavior: Ref[HttpApp[Any, Throwable]]) extends Client {
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

  def addHandler[R](
    pf: PartialFunction[Request, ZIO[R, Throwable, Response]],
  ): ZIO[R, Nothing, Unit] =
    for {
      r                <- ZIO.environment[R]
      previousBehavior <- behavior.get
      newBehavior                  = pf.andThen(_.provideEnvironment(r))
      app: HttpApp[Any, Throwable] = Http.collectZIO(newBehavior)
      _ <- behavior.set(previousBehavior ++ app)
    } yield ()

  // TODO Use these in request/socket methods?
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
      request = Request(body = body, headers = headers, method = method, url = URL(pathPrefix, kind = URL.Location.Relative), version = version, remoteAddress = None)
      response <- currentBehavior(request)
        .catchAll {
          case Some(value) => ZIO.succeed(Response.status(Status.BadRequest))
          case None => ZIO.succeed(Response.status(Status.NotFound)) // new Exception("Unhandled request: " + request)
        }
    } yield  response

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
  def addRequestResponse(
                          request: Request,
                          response: Response,
                        ): ZIO[TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.addRequestResponse(request, response))

  def addHandler[R](
    pf: PartialFunction[Request, ZIO[R, Throwable, Response]],
  ): ZIO[R with TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.addHandler(pf))

  val layer: ZLayer[Any, Throwable, TestClient] =
    ZLayer.scoped {
      for {
        behavior <- Ref.make[HttpApp[Any, Throwable]](Http.empty)
      } yield TestClient(behavior)
    }
}
