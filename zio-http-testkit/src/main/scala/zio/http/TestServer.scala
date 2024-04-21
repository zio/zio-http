package zio.http

import zio._

/**
 * Enables tests that make calls against "localhost" with user-specified
 * Behavior/Responses.
 *
 * @param driver
 *   The web driver that accepts our Server behavior
 * @param bindPort
 *   Port for HTTP interactions
 */
final case class TestServer(driver: Driver, bindPort: Int) extends Server {

  /**
   * Define 1-1 mappings between incoming Requests and outgoing Responses
   *
   * @param expectedRequest
   *   Request that will trigger the provided Response
   * @param response
   *   Response that the Server will send
   *
   * @example
   *   {{{
   *   TestServer.addRequestResponse(Request.get(url = URL.root.port(port = ???)), Response(Status.Ok))
   *   }}}
   */
  def addRequestResponse(
    expectedRequest: Request,
    response: Response,
  ): ZIO[Any, Nothing, Unit] = {
    addRoute(RoutePattern(expectedRequest.method, expectedRequest.path) -> handler { (realRequest: Request) =>
      if (
        // The way that the Client breaks apart and re-assembles the request prevents a straightforward
        //    expectedRequest == realRequest
        expectedRequest.url.relative == realRequest.url &&
        expectedRequest.method == realRequest.method &&
        expectedRequest.headers.forall(expectedHeader => realRequest.hasHeader(expectedHeader))
      ) response
      else Response.notFound
    })
  }

  /**
   * Adds a new route to the Server
   *
   * @param route
   *   New route
   * @example
   *   {{{
   * TestServer.addRoute {
   *   Method.ANY / trailing -> handler { (_: Path, _: Request) =>
   *     for {
   *       curState <- state.getAndUpdate(_ + 1)
   *     } yield {
   *       if (curState > 0)
   *         Response(Status.InternalServerError)
   *       else
   *         Response(Status.Ok)
   *     }
   *   }
   * }
   *   }}}
   */
  def addRoute[R](
    route: Route[R, Response],
  ): ZIO[R, Nothing, Unit] =
    for {
      r <- ZIO.environment[R]
      provided          = route.provideEnvironment(r)
      app: HttpApp[Any, Response] = provided.toHttpApp
      _ <- driver.addApp(app, r)
    } yield ()

  /**
   * Add new routes to the Server
   * @example
   *   {{{
   *   TestServer.addRoutes {
   *     Routes(
   *       Method.ANY / trailing -> handler { (_: Path, _: Request) => Response.text("Fallback handler") },
   *       Method.GET / "hello" / "world" -> handler { (_: Path, _: Request) => Response.text("Hello world!") },
   *     )
   *   }
   *   }}}
   */

  def addRoutes[R](
    routes: Routes[R, Response],
  ): ZIO[R, Nothing, Unit] =
    for {
      r <- ZIO.environment[R]
      provided          = routes.provideEnvironment(r)
      app: HttpApp[Any, Response] = provided.toHttpApp
      _ <- driver.addApp(app, r)
    } yield ()

  override def install[R](httpApp: HttpApp[R, Response])(implicit
    trace: zio.Trace,
  ): URIO[R, Unit] =
    ZIO
      .environment[R]
      .flatMap(
        driver.addApp(
          httpApp,
          _,
        ),
      )

  override def port: Int = bindPort
}

object TestServer {
  def addRoute[R](
    route: Route[R, Response],
  ): ZIO[R with TestServer, Nothing, Unit] =
    ZIO.serviceWithZIO[TestServer](_.addRoute(route))

  def addRoutes[R](
    routes: Routes[R, Response],
  ): ZIO[R with TestServer, Nothing, Unit] =
    ZIO.serviceWithZIO[TestServer](_.addRoutes(routes))

  def addRequestResponse(
    request: Request,
    response: Response,
  ): ZIO[TestServer, Nothing, Unit] =
    ZIO.serviceWithZIO[TestServer](_.addRequestResponse(request, response))

  val layer: ZLayer[Driver, Throwable, TestServer] =
    ZLayer.scoped {
      for {
        driver <- ZIO.service[Driver]
        result <- driver.start
      } yield TestServer(driver, result.port)
    }

}
