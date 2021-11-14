import zhttp.experiment.multipart.{BodyEnd, ChunkedData}
import zhttp.http._
import zhttp.service.Server
import zio.stream.ZStream
import zio.{App, ExitCode, URIO}

/*
 * Current multipart decoder API gives you access to Queue[Message].
 * Message is a sum type and can be "MetaInfo", "ChunkedData" or "BodyEnd".
 * The following example is converting message to Bytes and returning in response.
 * */
object Multipart extends App {
  def app: HttpApp[Any, Throwable] = HttpApp.fromHttp {
    Http.collectM[Request] { case req =>
      req.decodeContent(ContentDecoder.multipartDecoder).map { content =>
        Response(data =
          HttpData.fromStream(
            ZStream
              .fromQueue(content)
              .takeUntil(_ == BodyEnd)
              .collect { case ChunkedData(chunkedData) =>
                chunkedData
              }
              .mapChunks(_.flatten),
          ),
        )
      }
    }
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
