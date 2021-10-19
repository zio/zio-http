import zhttp.experiment.Route._
import zhttp.experiment._
import zhttp.http._
import zhttp.service.Server
import zio._

object Endpoints extends App {
  def h1 = HttpApp.GET / "a" / Route[Int] / "b" / Route[String] / "c" / "d" / Route[Boolean] to { a =>
    Response.text(a.params.toString)
  }

  def h2 = HttpApp.GET / "a" / ![Int] / "b" / ![String] toP { case (i, j) =>
    Response.text((i, j).toString())
  }

  def app: HttpApp[Any, Throwable] = h1 +++ h2

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
