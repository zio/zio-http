import zhttp.http._
import zhttp.service.Server
import zio._
import zio.duration._

/**
 * The following example depicts thread stickiness. The way it works is — once a request is received on the server, a
 * thread is associated with it permanently. Any ZIO execution within the context of that request is guaranteed to be
 * done on the same thread. This level of thread stickiness improves the performance characteristics of the server
 * dramatically.
 */
object StickyThread extends App {

  /**
   * A simple utility function that prints the fiber with the current thread.
   */
  private def printThread(tag: String): ZIO[Any, Nothing, Unit] = {
    for {
      id <- ZIO.fiberId
      _  <- UIO(println(s"${tag.padTo(6, ' ')}: Fiber(${id.seqNumber}) Thread(${Thread.currentThread().getName})"))
    } yield ()
  }

  /**
   * The expected behaviour is that all the `printThread` output different fiber ids with the same thread name.
   */
  val app = HttpApp.collectM { case Method.GET -> Root / "text" =>
    for {

      _  <- printThread("Start")
      f1 <- ZIO.sleep(1 second).zipLeft(printThread("First")).fork
      f2 <- ZIO.sleep(1 second).zipLeft(printThread("Second")).fork
      _  <- f1.join <*> f2.join
    } yield Response.text("Hello World!")
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
