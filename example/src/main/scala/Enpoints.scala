import zhttp.experiment._
import zhttp.http._
import zhttp.service.Server
import zio._

object Endpoints extends App {

  def h3 = HttpApp.endpoint(HttpApp.GET / "a" / Route[Int] / "b") { case (req, route) =>
    Response.text(route.extract(req.path).toString)
  }

  def h4 = HttpApp.endpointM(HttpApp.GET / "a" / Route[Int] / "b") { case (req, route) =>
    UIO(Response.text(route.extract(req.path).toString))
  }

  def h5 = HttpApp.GET / "a" / Route[Int] / "b" / Route[String] to { case _ => Response.ok }

  def h6 = HttpApp.GET / "a" / Route[Int] / "b" / Route[String] to { case _ => UIO(Response.ok) }

  def app: HttpApp[Any, Throwable] = h3 +++ h4 +++ h5 +++ h6

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
