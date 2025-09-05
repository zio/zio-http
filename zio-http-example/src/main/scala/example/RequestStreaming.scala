//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio._

import zio.http._

object RequestStreaming extends ZIOAppDefault {

  // Create HTTP route which echos back the request body
  private val app = Routes(Method.POST / "echo" -> handler { (req: Request) =>
    // Returns a stream of bytes from the request
    // The stream supports back-pressure
    val stream = req.body.asStream

    // Creating HttpData from the stream
    // This works for file of any size
    val data = Body.fromStreamChunked(stream)

    Response(body = data)
  })

  // Run it like any simple app
  override val run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    Server.serve(app).provide(Server.default)
}
