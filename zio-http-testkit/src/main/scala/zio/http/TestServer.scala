package zio.http

import zio._
import zio.http.Server.ErrorCallback

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
   *   TestServer.addRequestResponse(Request.get(url = URL.root.setPort(port = ???)), Response(Status.Ok))
   *   }}}
   */
  def addRequestResponse(
    expectedRequest: Request,
    response: Response,
  ): ZIO[Any, Nothing, Unit] = {
    val handler: PartialFunction[Request, ZIO[Any, Nothing, Response]] = {
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
   * Add new behavior to Server
   * @param pf
   *   New behavior
   *
   * @example
   *   {{{
   *  for {
   *    state <- Ref.make(0)
   *    testRequest <- requestToCorrectPort
   *    _           <- TestServer.addHandler{ case (_: Request) =>
   *      for {
   *        curState <- state.getAndUpdate(_ + 1)
   *      } yield {
   *        if (curState > 0)
   *          Response(Status.InternalServerError)
   *        else
   *          Response(Status.Ok)
   *      }
   *    }
   *   }}}
   */
  def addHandler[R](
    pf: PartialFunction[Request, ZIO[R, Throwable, Response]],
  ): ZIO[R, Nothing, Unit] =
    for {
      r <- ZIO.environment[R]
      behavior                     = pf.andThen(_.provideEnvironment(r))
      app: HttpApp[Any, Throwable] = Http.fromFunctionZIO(behavior)
      _ <- driver.addApp(app, r)
    } yield ()

  override def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback])(implicit
    trace: zio.Trace,
  ): URIO[R, Unit] = {
    ZIO.environment[R].flatMap(driver.addApp(
      httpApp
        // TODO Remove this if I can't get Netty errors reported more nicely
        .tapErrorZIO(e => ZIO.debug("??? " + e))
        .catchAll( error => Http.fromZIO(
          ZIO.debug("Error: " + error.getMessage) *> ZIO.succeed(Response.ok)

        ))
        .catchAllDefect(error => Http.fromZIO(
          ZIO.debug("Defect: " + error.getMessage) *> ZIO.succeed(Response.ok)

        )), _
    )) *> setErrorCallback(errorCallback) <*
      ZIO.debug("Installed app")
  }

  private def setErrorCallback(errorCallback: Option[ErrorCallback]): UIO[Unit] =
    driver
      .setErrorCallback(errorCallback)
      .unless(errorCallback.isEmpty)
      .map(_.getOrElse(()))

  override def port: Int = bindPort
}

object TestServer {
  def addHandler[R](
    pf: PartialFunction[Request, ZIO[R, Throwable, Response]],
  ): ZIO[R with TestServer, Nothing, Unit] =
    ZIO.serviceWithZIO[TestServer](_.addHandler(pf))

  def addRequestResponse(
    request: Request,
    response: Response,
  ): ZIO[TestServer, Nothing, Unit] =
    ZIO.serviceWithZIO[TestServer](_.addRequestResponse(request, response))

  val layer: ZLayer[Driver, Throwable, TestServer] =
    ZLayer.scoped {
      for {
        driver <- ZIO.service[Driver]
        port   <- driver.start
      } yield TestServer(driver, port)
    }

}
