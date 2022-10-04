package zio.http

import zio._
import zio.http.Server.ErrorCallback

final case class TestServer[State](
  state: Ref[State],
  routes: PartialFunction[(State, Request), (State, Response)],
  driver: Driver,
  bindPort: Int,
) extends Server {

  def addRequestResponse(
    expectedRequest: Request,
    response: Response,
  ): TestServer[State] = {
    val handler: PartialFunction[(State, Request), (State, Response)] = {
      case (state, realRequest) if {
            // The way that the Client breaks apart and re-assembles the request prevents a straightforward
            //    expectedRequest == realRequest
            expectedRequest.url.relative == realRequest.url &&
            expectedRequest.method == realRequest.method &&
            expectedRequest.headers.toSet.forall(expectedHeader => realRequest.headers.toSet.contains(expectedHeader))
          } =>
        (state, response)
    }
    addHandlerState(handler)
  }

  def addHandler(pf: PartialFunction[Request, Response]): TestServer[State] = {
    val handler: PartialFunction[(State, Request), (State, Response)] = {
      case (state, request) if pf.isDefinedAt(request) => (state, pf(request))
    }
    addHandlerState(handler)
  }

  def addHandlerState(
    pf: PartialFunction[(State, Request), (State, Response)],
  ): TestServer[State] =
    copy(routes = routes.orElse(pf))

  def install(implicit
    trace: zio.Trace,
  ): UIO[Unit] =
    driver.addApp(
      Http.fromFunctionZIO((request: Request) => state.modify(state1 => routes((state1, request)).swap)),
    )

  override def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback])(implicit
    trace: zio.Trace,
  ): URIO[R, Unit] =
    ZIO.environment[R].flatMap { env =>
      driver.addApp(
        if (env == ZEnvironment.empty) httpApp.asInstanceOf[HttpApp[Any, Throwable]]
        else httpApp.provideEnvironment(env),
      ) *> {
        val func =
          (request: Request) => state.modify(state1 => routes((state1, request)).swap)

        val app: HttpApp[Any, Nothing] = Http.fromFunctionZIO(func)
        driver.addApp(app)
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

  val make: ZIO[Driver with Scope, Throwable, TestServer[Unit]] =
    make(())

  def make[State](initial: State): ZIO[Driver with Scope, Throwable, TestServer[State]] =
    for {
      driver <- ZIO.service[Driver]
      port   <- driver.start
      state  <- Ref.make(initial)
    } yield TestServer(state, empty, driver, port)

  private def empty[State]: PartialFunction[(State, Request), (State, Response)] = {
    case _ if false => ???
  }
}
