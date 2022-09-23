package zio.http

import zio._
import zio.http.Server.ErrorCallback
import zio.http.middleware.HttpMiddleware




// TODO Add Trace to all signatures when closer to completion
trait TestServer extends Server {
  def responses: ZIO[Any, Nothing, List[Response]]
  def requests: ZIO[Any, Nothing, List[Request]]
  def feedRequests(responses: Request*): ZIO[Any, Nothing, Unit]

  // TODO What all do we want here?
  def feedResponses(responses: Request*): ZIO[Any, Nothing, Unit] = ???
}


object TestServer {
  def responses = ZIO.serviceWithZIO[TestServer](_.responses)
  def requests = ZIO.serviceWithZIO[TestServer](_.requests)
  def feedRequests(requests: Request*) = ZIO.serviceWithZIO[TestServer](_.feedRequests(requests:_*))
  class Test(
              //            live: HttpLive,
              live: Server,
              requestsR: Ref[List[Request]],
              responsesR: Ref[List[Response]]
            ) extends TestServer {
    override def responses: ZIO[Any, Nothing, List[Response]] =
      responsesR.get

    override def requests: ZIO[Any, Nothing, List[Request]] =
      requestsR.get

    override def feedRequests(requests: Request*): ZIO[Any, Nothing, Unit] =
      requestsR.update(_ ++ requests)

    val trackingMiddleware: HttpMiddleware[Any, Nothing] = {
      new Middleware[Any, Nothing, Request, Response, Request, Response] {
        /**
         * Applies middleware on Http and returns new Http.
         */
        override def apply[R1 <: Any, E1 >: Nothing](http: Http[R1, E1, Request, Response]): Http[R1, E1, Request, Response] =
          Http.fromOptionFunction[Request] { req =>
            for {
              _ <- requestsR.update(_ :+ req)
              response <- http(req)
            } yield response
          }
      }
    }

    override def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback]): URIO[R, Unit] = {
      ZIO.debug("TestServer.install") *>
      live.install(httpApp @@ trackingMiddleware, errorCallback)
//      ZIO.succeed(unsafe.print(line)(Unsafe.unsafe)) *>
//      live.provide(Server.install(httpApp))
//        .whenZIO(debugState.get) // TOOD Restore at some point
//        .unit
    }

    override def port: Int = live.port
  }


  def make: ZLayer[Any, Nothing, TestServer] =
    for {
      requests <- ZLayer.fromZIO(Ref.make(List.empty[Request]))
      responses <- ZLayer.fromZIO(Ref.make(List.empty[Response]))
      live <- Server.default.orDie
      //      live = Server.ServerLiveHardcoded
    } yield ZEnvironment(new Test(live.get, requests.get, responses.get))
}
