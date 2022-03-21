package example

import zhttp.http._
import zhttp.service.Server
import zio._
object RequestStreaming extends App {

  // Create HTTP route which echos back the request body
  val app = Http.collect[Request] { case req @ Method.POST -> !! / "echo" =>
    // Returns a stream of bytes from the request
    // The stream supports back-pressure
    val stream = req.bodyAsStream

    // Creating HttpData from the stream
    // This works for file of any size
    val data = HttpData.fromStream(stream)

    Response(data = data)
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
