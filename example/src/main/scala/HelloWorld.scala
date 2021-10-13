import zhttp.http._
import zhttp.service.Server
import zio._
import zio.stream._

object HelloWorld extends App {

  def h1 = HttpApp.fromHttp {
    Http.collectM[Request] { case req =>
      req.decodeContent(ContentDecoder.text).map { content =>
        Response(data = HttpData.fromText(content))
      }
    }
  }

  def h2 = HttpApp.fromHttp {
    Http.collectM[Request] { case req =>
      req.decodeContent(ContentDecoder.backPressure).map { content =>
        Response(data = HttpData.fromStream(ZStream.fromChunkQueue(content)))
      }
    }
  }

  def h3 = HttpApp.endpoint(HttpApp.GET / "a" / Route[Int] / "b") { case (req, route) =>
    Response.text(route.extract(req.path).toString)
  }

  def h4 = HttpApp.endpointM(HttpApp.GET / "a" / Route[Int] / "b") { case (req, route) =>
    UIO(Response.text(route.extract(req.path).toString))
  }

  def app: HttpApp[Any, Throwable] = h1 +++ h2 +++ h3

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
