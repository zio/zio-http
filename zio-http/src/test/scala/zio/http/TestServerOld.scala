package zio.http

import zio._
import zio.http.Server.ErrorCallback
import zio.http.TestServerOld.Interaction
import zio.http.middleware.HttpMiddleware

// TODO Add Traces?
trait TestServerOld extends Server {
  def interactions: UIO[List[Interaction]]
}

object TestServerOld {
  case class Interaction(request: Request, response: Response)
  def interactions: ZIO[TestServerOld, Nothing, List[Interaction]] = ZIO.serviceWithZIO[TestServerOld](_.interactions)
  class TestOld(
    // TODO Instead of wrapping a live server, accept a list of Responses that will be returned
    live: Server,
    interactionsR: Ref[List[Interaction]],
  ) extends TestServerOld {
    def interactions: UIO[List[Interaction]] =
      interactionsR.get

    val trackingMiddleware: HttpMiddleware[Any, Nothing] = {
      new Middleware[Any, Nothing, Request, Response, Request, Response] {

        /**
         * Applies middleware on Http and returns new Http.
         */
        override def apply[R1 <: Any, E1 >: Nothing](
          http: Http[R1, E1, Request, Response],
        ): Http[R1, E1, Request, Response] =
          Http.fromOptionFunction[Request] { req =>
            for {
              response <- http(req) // TODO Handle failures here
              _        <- interactionsR.update(_ :+ Interaction(req, response))
            } yield response
          }
      }
    }

    override def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback]): URIO[R, Unit] = {
      live.install(httpApp @@ trackingMiddleware, errorCallback)
//      ZIO.succeed(unsafe.print(line)(Unsafe.unsafe)) *>
//      live.provide(Server.install(httpApp))
//        .whenZIO(debugState.get) // TOOD Restore at some point?
//        .unit
    }

    override def port: Int = live.port
  }

  def make: ZLayer[Any, Throwable, TestServerOld] =
    for {
      interactions <- ZLayer.fromZIO(Ref.make(List.empty[Interaction]))
      live         <- Server.default
    } yield ZEnvironment(new TestOld(live.get, interactions.get))

}
