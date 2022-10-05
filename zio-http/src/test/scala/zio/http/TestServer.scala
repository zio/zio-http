package zio.http

import zio._
import zio.http.Server.ErrorCallback

/**
 * Enables tests that make calls against "localhost" with user-specified
 * Behavior/Responses.
 *
 * @param behavior
 *   Describes how the Server should behave during your test
 * @param driver
 *   The web driver that accepts our Server behavior
 * @param bindPort
 *   Port for HTTP interactions
 */
final case class TestServer(
  behavior: Ref[PartialFunction[Request, ZIO[Any, Throwable, Response]]],
  driver: Driver,
  bindPort: Int,
) extends Server {

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
   *
   * @return
   *   The TestSever with new behavior.
   */
  def addRequestResponse(
    expectedRequest: Request,
    response: Response,
  ): ZIO[Any, Nothing, Unit] = {
    val handler: PartialFunction[Request, ZIO[Any, Nothing, Response]] = {
      case (realRequest) if {
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
   * @return
   *   The TestSever with new behavior.
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
  ): ZIO[R, Nothing, Unit] = {

    for {
      r <- ZIO.environment[R]
      newBehavior <- behavior.updateAndGet(_.orElse(pf.andThen(_.provideEnvironment(r))))
      app: HttpApp[Any, Throwable] = Http.fromFunctionZIO(newBehavior)
      _ <- driver.addApp(app)
    } yield ()
  }

  def install(implicit
    trace: zio.Trace,
  ): UIO[Unit] =
    driver.addApp(
      Http.fromFunctionZIO((request: Request) =>
        for {
          behavior1 <- behavior.get
          response  <- behavior1(request)
        } yield response,
      ),
    )

  override def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback])(implicit
    trace: zio.Trace,
  ): URIO[R, Unit] =
    ZIO.environment[R].flatMap { env =>
      driver.addApp(
        if (env == ZEnvironment.empty) httpApp.asInstanceOf[HttpApp[Any, Throwable]]
        else httpApp.provideEnvironment(env),
      ) *> {
        for {
          behavior1 <- behavior.get
          app = Http.fromFunctionZIO(behavior1)
          _ <- driver.addApp(app)
        } yield ()
      }
    } *> setErrorCallback(errorCallback)

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

  val layer: ZLayer[Driver, Throwable, TestServer] = {
    ZLayer.scoped {
      for {
        driver <- ZIO.service[Driver]
        port <- driver.start
        routes <- Ref.make[PartialFunction[Request, ZIO[Any, Throwable, Response]]](empty)
      } yield TestServer(routes, driver, port)
    }
  }

  // Ensures that we blow up quickly if we execute a test against a TestServer with no behavior defined.
  private def empty: PartialFunction[Request, ZIO[Any, Nothing, Response]] = {
    case _ if false => ???
  }
}
