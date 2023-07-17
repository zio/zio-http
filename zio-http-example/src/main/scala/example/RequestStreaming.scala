package example

import zio._

import zio.http._

object RequestStreaming extends ZIOAppDefault {

  // Create HTTP route which echos back the request body
  val app = Routes(Method.POST / "echo" -> handler { (req: Request) =>
    // Returns a stream of bytes from the request
    // The stream supports back-pressure
    val stream = req.body.asStream

    // Creating HttpData from the stream
    // This works for file of any size
    val data = Body.fromStream(stream)

    Response(body = data)
  }).toHttpApp

  // Run it like any simple app
  val run: UIO[ExitCode] =
    Server.serve(app).provide(Server.default).exitCode
}
