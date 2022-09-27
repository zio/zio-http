package zio.http

import zio._
import zio.http.Server.ErrorCallback
//import zio.http.model.Status

final case class TestServer[State](
                                    state: Ref[State],
                                    routes: Ref[PartialFunction[(State, Request), (State, Response)]],
                                    driver: Driver,
                                    bindPort: Int,
                                  ) extends Server {

  def addHandlerExact(
                       expectedRequest: Request,
                       response: Response
                 ): ZIO[Any, Nothing, Unit] = {
    val handler: PartialFunction[(State, Request), (State, Response)] = {
      // The way that the Client breaks apart and re-assembles the request prevents a straightforward
      //    expectedRequest == realRequest
      // here, so this
      case (state, realRequest) if {
        expectedRequest.url.relative == realRequest.url &&
          expectedRequest.method == realRequest.method &&
          expectedRequest.headers.toSet.forall(expectedHeader => realRequest.headers.toSet.contains(expectedHeader))
      } => (state, response)

//      case (state, other) =>
//        (state, Response.apply(Status.NotFound))

    }
    addHandlerState(handler)
  }

  def addHandler(pf: PartialFunction[Request, Response]): ZIO[Any, Nothing, Unit] = {
    val handler: PartialFunction[(State, Request), (State, Response)] = {
      case ((state, request)) if pf.isDefinedAt(request) => (state, pf(request))
    }
    addHandlerState(handler)
  }

  def addHandlerState(
                       pf: PartialFunction[(State, Request), (State, Response)]
                     ): ZIO[Any, Nothing, Unit] = {
    val func =
      (request: Request) =>
          state.modify(state1 => pf((state1, request)).swap)

    val app: HttpApp[Any, Nothing] = Http.fromFunctionZIO(func)
    routes.update(_.orElse(pf)) *> driver.addApp(app)
  }

  override def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback]): URIO[R, Unit] =
    ZIO.environment[R].flatMap { env =>
      driver.addApp(
        if (env == ZEnvironment.empty) httpApp.asInstanceOf[HttpApp[Any, Throwable]]
        else httpApp.provideEnvironment(env),
      )

    } *> setErrorCallback(errorCallback)

  private def setErrorCallback(errorCallback: Option[ErrorCallback]): UIO[Unit] = {
    ZIO
      .environment[Any]
      .flatMap(_ => driver.setErrorCallback(errorCallback))
      .unless(errorCallback.isEmpty)
      .map(_.getOrElse(()))
  }
  override def port: Int = bindPort
}

object TestServer {
  def addHandlerState[State: Tag](
                       pf: PartialFunction[(State, Request), (State, Response)]
                     ): ZIO[TestServer[State], Nothing, Unit] =
    ZIO.serviceWithZIO[TestServer[State]](_.addHandlerState (pf))

  def addHandlerExact[State: Tag](
                  request: Request,
                  response: Response
                ): ZIO[TestServer[State], Nothing, Unit] =
    ZIO.serviceWithZIO[TestServer[State]](_.addHandlerExact(request, response))

  def addHandler[T: Tag](pf: PartialFunction[Request, Response]): ZIO[TestServer[T], Nothing, Unit] =
    ZIO.serviceWithZIO[TestServer[T]](_.addHandler {
      pf
    })

  val make: ZIO[Driver with Scope, Throwable, TestServer[Unit]] =
    make(())

  def make[State](initial: State): ZIO[Driver with Scope, Throwable, TestServer[State]] =
    for {
      driver <- ZIO.service[Driver]
      port   <- driver.start // TODO Should we be calling start here? Or is it better elsewhere?
      state  <- Ref.make(initial)
      routes <- Ref.make[PartialFunction[(State, Request), (State, Response)]](empty)
    } yield TestServer(state, routes, driver, port)

  private def empty[State]: PartialFunction[(State, Request), (State, Response)] =
    {
      case _ if false => ???
    }
}