package example

import zhttp.http._
import zhttp.service.Server
import zio._
object RequestStreaming extends App {

  // Create HTTP route which echos back the request body as Stream
  val app: HttpApp[Any, Nothing] = Http.collect[Request] { case req @ Method.POST -> !! / "echo" =>
    Response(data = HttpData.fromStream(req.bodyAsStream))
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
