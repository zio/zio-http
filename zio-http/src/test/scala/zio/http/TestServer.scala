package zio.http

import zio._
import zio.http.Server.ErrorCallback

/**
 * Enables tests that make calls against "localhost" with user-specified
 * Behavior/Responses.
 *
 * @param state
 *   State that can be consulted by our behavior PartialFunction
 * @param behavior
 *   Describes how the Server should behave during your test
 * @param driver
 *   The web driver that accepts our Server behavior
 * @param bindPort
 *   Port for HTTP interactions
 * @tparam State
 *   The type of state that will be mutated
 */
final case class TestServer[State](
  state: Ref[State],
  behavior: Ref[PartialFunction[Request, ZIO[Any, Nothing, Response]]],
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
   *   {{{ ZIO.serviceWithZIO[TestServer[Unit]]( _.addRequestResponse(
   *   Request.get(url = URL.root.setPort(port = ???)), Response(Status.Ok)
   *   ).install ) }}}
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
    addHandlerState(handler)
  }

  /**
   * Define stateless behavior for the Server.
   *
   * @param pf
   *   The stateless behavior
   *
   * @return
   *   The TestSever with new behavior.
   *
   * @example
   *   {{{
   *   ZIO.serviceWithZIO[TestServer[Unit]](_.addHandler { case _: Request =>
   *       Response(Status.Ok)
   *   }
   *   }}}
   */
  def addHandler(pf: PartialFunction[Request, ZIO[Any, Nothing, Response]]): ZIO[Any, Nothing, Unit] = {
    addHandlerState(pf)
  }

  /**
   * Define stateful behavior for Server
   * @param pf
   *   Stateful behavior
   * @return
   *   The TestSever with new behavior.
   *
   * @example
   *   {{{
   * ZIO.serviceWithZIO[TestServer[Int]](_.addHandlerState { case (state, _: Request) =>
   *   if (state > 0)
   *     (state + 1, Response(Status.InternalServerError))
   *   else
   *     (state + 1, Response(Status.Ok))
   * }
   *   }}}
   */
  def addHandlerState(
    pf: PartialFunction[Request, ZIO[Any, Nothing, Response]],
  ): ZIO[Any, Nothing, Unit] = {
    for {
      newBehavior <- behavior.updateAndGet(_.orElse(pf))
      app: HttpApp[Any, Nothing] = Http.fromFunctionZIO(newBehavior)
      _ <-  driver.addApp(app)
    } yield ()
  }

  def install(implicit
    trace: zio.Trace,
  ): UIO[Unit] =
    driver.addApp(
      Http.fromFunctionZIO((request: Request) =>
        for {
          behavior1 <- behavior.get
          response <- behavior1(request)
        } yield response
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
  def addHandlerState[State: Tag](
    pf: PartialFunction[Request, ZIO[Any, Nothing, Response]],
  ): ZIO[TestServer[State], Nothing, Unit] =
    ZIO.serviceWithZIO[TestServer[State]](_.addHandlerState(pf))

  def addRequestResponse[State: Tag](
    request: Request,
    response: Response,
  ): ZIO[TestServer[State], Nothing, Unit] =
    ZIO.serviceWithZIO[TestServer[State]](_.addRequestResponse(request, response))

  def addHandler[T: Tag](pf: PartialFunction[Request, ZIO[Any, Nothing, Response]]): ZIO[TestServer[T], Nothing, Unit] =
    ZIO.serviceWithZIO[TestServer[T]](_.addHandler {
      pf
    })

  val layer: ZIO[Driver with Scope, Throwable, TestServer[Unit]] =
    layer(())

  def layer[State](initial: State): ZIO[Driver with Scope, Throwable, TestServer[State]] =
    for {
      driver <- ZIO.service[Driver]
      port   <- driver.start
      state  <- Ref.make(initial)
      routes <- Ref.make[PartialFunction[Request, ZIO[Any, Nothing, Response]]](empty)
    } yield TestServer(state, routes, driver, port)

  // Ensures that we blow up quickly if we execute a test against a TestServer with no behavior defined.
  private def empty[State]: PartialFunction[Request, ZIO[Any, Nothing, Response]] = {
    case _ if false => ???
  }
}
