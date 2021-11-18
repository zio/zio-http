package example

import zhttp.http._
import zhttp.service.Server
import zio.stream.ZStream
import zio.{App, ExitCode, URIO}

object BackPressure extends App {

  def h1 = HttpApp.collectM { case req @ Method.POST -> !! / "foo" =>
    req.getBody(ContentDecoder.text).map { content =>
      Response(data = HttpData.fromText(content))
    }
  }

  def h2 = HttpApp.collectM { case req @ Method.POST -> !! / "bar" =>
    req.getBody(ContentDecoder.backPressure).map { content =>
      req.getContentLength match {
        case Some(value) => Response(data = HttpData.fromStream(ZStream.fromChunkQueue(content).take(value)))
        case None        => Response.fromHttpError(HttpError.LengthRequired())
      }
    }
  }

  def app: HttpApp[Any, Throwable] = h1 +++ h2

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
