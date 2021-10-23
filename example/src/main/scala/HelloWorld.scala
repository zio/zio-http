import zhttp.experiment.Part
import zhttp.http.ContentDecoder.multipartDecoder
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
  def h3 = HttpApp.fromHttp {
    Http.collectM[Request] { case req =>
      req
        .decodeContent(ContentDecoder.multipart(multipartDecoder(req)))
        .map(content => {
          Response(data =
            HttpData.fromStream(
              ZStream
                .fromQueue(content)
                .map(Part.fromHTTPData)
                .map {
                  case Part.FileData(content, _) => content
                  case Part.Attribute(_, _)      => ???
                  case Part.Empty                => ???
                }
                .mapChunks(_.flatten),
            ),
          )

        })
    }
  }

  def app: HttpApp[Any, Throwable] = h3

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
