import zhttp.experiment.HttpMessage._
import zhttp.experiment._
import zhttp.http.{Http, Request}
import zhttp.service.Server
import zio._
import zio.stream._

object HelloWorld extends App {

  def h1 = HttpEndpoint.mount {
    Http.collectM[Request] { case req =>
      req.decodeContent(ContentDecoder.text).map { content =>
        CompleteResponse(content = content)
      }
    }
  }

  def h2 = HttpEndpoint.mount {
    Http.collectM[Request] { case req =>
      req.decodeContent(ContentDecoder.backPressure).map { content =>
        BufferedResponse(content = ZStream.fromChunkQueue(content))
      }
    }
  }

  def app: HttpEndpoint[Any, Throwable] = h1 +++ h2

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start0(8090, app).exitCode
}
