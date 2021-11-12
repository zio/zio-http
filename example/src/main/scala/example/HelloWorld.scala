package example

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http._
import zhttp.service.Server
import zio.stream.ZStream
import zio.{App, ExitCode, URIO}

object HelloWorld extends App {

  def h1 = HttpApp.collectM { case req @ Method.POST -> !! / "foo" =>
    req.decodeContent(ContentDecoder.text).map { content =>
      Response(data = HttpData.fromText(content))
    }
  }

  def h2 = HttpApp.collectM { case req @ Method.POST -> !! / "bar" =>
    req.decodeContent(ContentDecoder.backPressure).map { content =>
      req.getHeaderValue(HttpHeaderNames.CONTENT_LENGTH) match {
        case Some(value) => Response(data = HttpData.fromStream(ZStream.fromChunkQueue(content).take(value.toLong)))
        case None        => Response.fromHttpError(HttpError.LengthRequired())
      }
    }
  }

  def app: HttpApp[Any, Throwable] = h1 +++ h2

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
