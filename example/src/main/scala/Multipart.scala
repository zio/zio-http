import zhttp.experiment.multipart.ChunkedData
import zhttp.http.{ContentDecoder, Http, HttpApp, HttpData, Request, Response}
import zhttp.service.Server
import zio.{App, ExitCode, URIO}
import zio.stream.{UStream, ZStream}

object Multipart extends App {
  def app: HttpApp[Any, Throwable] = HttpApp.fromHttp {
    Http.collectM[Request] { case req =>
      req.decodeContent(ContentDecoder.multipartDecoder("")).map { content =>
        Response(data =
          HttpData.fromStream(
            ZStream
              .fromQueue(content)
              .filter(_.isInstanceOf[ChunkedData])
              .asInstanceOf[UStream[ChunkedData]]
              .map(_.chunkedData)
              .mapChunks(_.flatten),
          ),
        )
      }
    }
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
