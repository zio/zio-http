package example

import zhttp.http._
import zhttp.service.Server
import zio._
import zio.stream.ZStream
object HelloWorld extends App {

  // Create HTTP route
  val app = Http.collect[Request] { case req =>
    Response(data =
      HttpData.fromStream(
        ZStream.repeatEffectChunkOption(req.getBodyChunk),
      ),
    )
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
