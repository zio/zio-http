import zhttp.experiment.Route
import zhttp.http.Request.GET
import zhttp.http._
import zhttp.service.Server
import zio._

object Endpoints extends App {
  def h1 = GET / "a" / Route[Int] / "b" / Route[String] / "c" / "d" / Route[Boolean] to { a =>
    Response.text(a.params.toString)
  }

  def app: HttpApp[Any, Throwable] = h1

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
