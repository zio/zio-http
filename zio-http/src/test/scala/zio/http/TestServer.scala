package zio.http

import zio._
import zio.internal.stacktracer.Tracer

// TODO Add Trace to all signatures when closer to completion
trait TestServer extends Server {
  def responses: ZIO[Any, Nothing, List[Response]]
  def requests: ZIO[Any, Nothing, List[Request]]
  def feedRequests(responses: Request*): ZIO[Any, Nothing, Unit]
}


object TestServer {
  class Test(
            live: Server
            )

  def make: ZIO[Any, Nothing, TestServer] = ???
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
