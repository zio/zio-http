package zio.http

import zio._

final case class TestServer[State](state: Ref[State], routes: Ref[PartialFunction[(State, Request), (State, Response)]]) {

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
                 ): ZIO[Any, Nothing, Unit] =
    routes.update(_.orElse(pf))

}

object TestServer {

  val make: UIO[TestServer[Unit]] =
    make(())

  def make[State](initial: State): UIO[TestServer[State]] =
    for {
      state  <- Ref.make(initial)
      routes <- Ref.make[PartialFunction[(State, Request), (State, Response)]]()
    } yield TestServer(state, routes)

  private def empty[State]: PartialFunction[(State, Request), (State, Response)] =
    {
      case x if false => ???
    }
}