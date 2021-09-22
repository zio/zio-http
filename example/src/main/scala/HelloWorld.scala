import zhttp.experiment.HttpMessage._
import zhttp.experiment._
import zhttp.http.{Http, HttpData, Request}
import zhttp.service.Server
import zio._
import zio.stream._

object HelloWorld extends App {

  def h1 = HttpEndpoint.mount {
    Http.collectM[Request] { case req =>
      req.decodeContent(ContentDecoder.text).map { content =>
        AnyResponse(content = HttpData.fromText(content))
      }
    }
  }

  def h2 = HttpEndpoint.mount {
    Http.collectM[Request] { case req =>
      req.decodeContent(ContentDecoder.backPressure).map { content =>
        AnyResponse(content = HttpData.fromStream(ZStream.fromChunkQueue(content)))
      }
    }
  }

  def app: HttpEndpoint[Any, Throwable] = h1 +++ h2

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start0(8090, app).exitCode
}
