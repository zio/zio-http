package example

import zhttp.http._
import zhttp.service.Server
import zhttp.service.server.ContentDecoder
import zio._
import zio.stream.ZStream
object HelloWorld extends App {

  // Create HTTP route
  val app = Http.collectM[Request] { case req =>
    req.decodeContent(ContentDecoder.backPressure).map { content =>
      Response(data = HttpData.fromStream(ZStream.fromChunkQueue(content)))
    }
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
