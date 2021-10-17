import zhttp.experiment._
import zhttp.http._
import zhttp.service.Server
import zio._

object Endpoints extends App {

  def h2 = HttpApp.GET / "a" / Route[Int] / "b" / Route[String] to { case _ => UIO(Response.ok) }

  def h1 = HttpApp.GET / "a" / Route[Int] / "b" / Route[String] to { case (_, p) =>
    Response.text(p._1.toString)
  }

  def app: HttpApp[Any, Throwable] = h1 +++ h2

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
