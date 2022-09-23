package zio.http

import zio._
import zio.http.Server.ErrorCallback
import zio.http.TestServer.Interaction
import zio.http.middleware.HttpMiddleware


// TODO Add Traces?
trait TestServer extends Server {
  def interactions: UIO[List[Interaction]]
}


object TestServer {
  case class Interaction(request: Request, response: Response)
  def interactions: ZIO[TestServer, Nothing, List[Interaction]] = ZIO.serviceWithZIO[TestServer](_.interactions)
  class Test(
              live: Server,
              interactionsR: Ref[List[Interaction]]
            ) extends TestServer {
    def interactions: UIO[List[Interaction]] =
      interactionsR.get

    val trackingMiddleware: HttpMiddleware[Any, Nothing] = {
      new Middleware[Any, Nothing, Request, Response, Request, Response] {
        /**
         * Applies middleware on Http and returns new Http.
         */
        override def apply[R1 <: Any, E1 >: Nothing](http: Http[R1, E1, Request, Response]): Http[R1, E1, Request, Response] =
          Http.fromOptionFunction[Request] { req =>
            for {
              response <- http(req)
              _ <- interactionsR.update(_ :+ Interaction(req, response))
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


  def make: ZLayer[Any, Throwable, TestServer] =
    for {
      interactions <- ZLayer.fromZIO(Ref.make(List.empty[Interaction]))
      live <- Server.default
    } yield ZEnvironment(new Test(live.get, interactions.get))

}
