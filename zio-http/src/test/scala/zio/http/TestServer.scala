package zio.http

import zio._
import zio.http.Server.ErrorCallback

final case class TestServer[State](
                                    state: Ref[State],
                                    routes: Ref[PartialFunction[(State, Request), (State, Response)]],
                                    driver: Driver
                                  ) extends Server {

  def addHandler(
                   request: Request,
                   response: Response
                 ): ZIO[Any, Nothing, Unit] = {
    val handler: PartialFunction[(State, Request), (State, Response)] = {
      case ((state, request0)) if request == request0 => ((state, response))
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
        for {
          state1 <- state.get
          res =  pf((state1, request))
          _ <- state.set(res._1)
        } yield res._2
    val app: HttpApp[Any, Nothing] = Http.fromFunctionZIO(func)
    routes.update(_.orElse(pf)) *> driver.addApp(app)
  }

  override def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback]): URIO[R, Unit] = ???

  override def port: Int = ???
}

object TestServer {

  val make: ZIO[Driver, Nothing, TestServer[Unit]] =
    make(())

  def make[State](initial: State): ZIO[Driver, Nothing, TestServer[State]] =
    for {
      driver <- ZIO.service[Driver]
      state  <- Ref.make(initial)
      routes <- Ref.make[PartialFunction[(State, Request), (State, Response)]](empty)
    } yield TestServer(state, routes, driver)

  private def empty[State]: PartialFunction[(State, Request), (State, Response)] =
    {
      case _ if false => ???
    }
}