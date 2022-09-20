package zio.http

import zio._
import zio.http.Server.ErrorCallback
import zio.http.middleware.HttpMiddleware
import zio.internal.stacktracer.Tracer

// TODO Add Trace to all signatures when closer to completion
trait TestServer extends Server {
  def responses: ZIO[Any, Nothing, List[Response]]
  def requests: ZIO[Any, Nothing, List[Request]]
  def feedRequests(responses: Request*): ZIO[Any, Nothing, Unit]
}


object TestServer {
  def responses = ZIO.serviceWithZIO[TestServer](_.responses)
  def requests = ZIO.serviceWithZIO[TestServer](_.requests)
  def feedRequests(requests: Request*) = ZIO.serviceWithZIO[TestServer](_.feedRequests(requests:_*))
  class Test(
              //            live: HttpLive,
              live: Server.ServerLive,
              requestsR: Ref[List[Request]],
              responsesR: Ref[List[Response]]
            ) extends TestServer {
    override def responses: ZIO[Any, Nothing, List[Response]] =
      responsesR.get

    override def requests: ZIO[Any, Nothing, List[Request]] =
      requestsR.get

    override def feedRequests(responses: Request*): ZIO[Any, Nothing, Unit] =
      requestsR.update(_ ++ responses)

    val trackingMiddleware: HttpMiddleware[Any, Nothing] = {
      new Middleware[Any, Nothing, Request, Response, Request, Response] {
        /**
         * Applies middleware on Http and returns new Http.
         */
        override def apply[R1 <: Any, E1 >: Nothing](http: Http[R1, E1, Request, Response]): Http[R1, E1, Request, Response] =
          Http.fromOptionFunction[Request] { req =>
            for {
              _ <- requestsR.update(req :: _) // TODO decide if prepending is the right move
              response <- http(req)
            } yield response
          }
      }
    }

    override def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback]): URIO[R, Unit] = {
      live.install(httpApp @@ trackingMiddleware, errorCallback)
//      ZIO.succeed(unsafe.print(line)(Unsafe.unsafe)) *>
//      live.provide(Server.install(httpApp))
//        .whenZIO(debugState.get) // TOOD Restore at some point
//        .unit
    }

    override def port: RuntimeFlags = live.port
  }

  def make: ZIO[Any, Nothing, TestServer] =
    for {
      requests <- Ref.make(List.empty[Request])
      responses <- Ref.make(List.empty[Response])
      live = Server.ServerLiveHardcoded
    } yield new Test(live, requests, responses)
}

trait HttpLive {
  def provide[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]
}

object HttpLive {

  val tag: Tag[HttpLive] = Tag[HttpLive]

  final case class Test(zenv: ZEnvironment[Server]) extends HttpLive {
    def provide[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      DefaultServices.currentServices.locallyWith(_.unionAll(zenv))(zio)
  }

  /**
   * Constructs a new `HttpLive` service that implements the `HttpLive` interface. This
   * typically should not be necessary as the `TestEnvironment` already includes
   * the `HttpLive` service but could be useful if you are mixing in interfaces to
   * create your own environment type.
   */
  val default: ZLayer[Server, Nothing, HttpLive] = {
    implicit val trace = Tracer.newTrace
    ZLayer.scoped {
      for {
        zenv <- ZIO.environment[Server]
        live  = Test(zenv)
        _    <- withLiveScoped(live)
      } yield live
    }
  }

  /**
   * Provides a workflow with the "live" default ZIO services.
   */
  def live[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    liveWith(_.provide(zio))

  def liveWith[R, E, A](f: HttpLive => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    DefaultServices.currentServices.getWith(services => f(services.asInstanceOf[ZEnvironment[HttpLive]].get))

  /**
   * Runs a transformation function with the live default ZIO services while
   * ensuring that the workflow itself is run with the test services.
   */
  def withLive[R, E, E1, A, B](
                                zio: ZIO[R, E, A]
                              )(f: ZIO[R, E, A] => ZIO[R, E1, B])(implicit trace: Trace): ZIO[R, E1, B] =
    DefaultServices.currentServices.getWith(services => live(f(DefaultServices.currentServices.locally(services)(zio))))

  def withLiveScoped[A <: HttpLive](live: => A)(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit] =
    DefaultServices.currentServices.locallyScopedWith(_.add(live))
}

object DefaultServices {

  /**
   * The default ZIO services.
   */
  val live: ZEnvironment[Server] =
    ZEnvironment[Server](
      Server.ServerLiveHardcoded,
    )(Server.tag)

  private[zio] val currentServices: FiberRef.WithPatch[ZEnvironment[
    Server
  ], ZEnvironment.Patch[Server, Server]] = {
    FiberRef.unsafe.makeEnvironment(live)(Unsafe.unsafe)
  }
}
