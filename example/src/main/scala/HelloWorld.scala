import zhttp.http._
import zhttp.service.Server
import zio._
import zio.duration._

object HelloWorld extends App {

  def printThread(tag: String) =
    UIO(println(s"${tag.padTo(6, ' ')}: ${Thread.currentThread().getName}"))

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
