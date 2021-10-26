import zhttp.http.Endpoint.*
import zhttp.http.Method.GET
import zhttp.http._
import zhttp.service.Server
import zio._

object Endpoints extends App {
  def h1 = GET / "a" / *[Int] / "b" / *[Boolean] to { a =>
    Response.text(a.params.toString)
  }

  def h2 = GET / *[Int] / "b" / *[Boolean] to { a =>
    Response.text(a.params.toString)
  }

  def app: HttpApp[Any, Throwable] = h1 +++ h2

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
