package example

import zhttp.http._
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio._
object RequestStreaming extends ZIOAppDefault {

  // Create HTTP route which echos back the request body
  val app = Http.collectZIO[Request] { case req @ Method.POST -> !! / "echo" =>
    // Returns a stream of bytes from the request
    // The stream supports back-pressure
    val stream = req.body.asStream

    // Creating Body from the stream
    // This works for file of any size
    val data = Body.fromStream(stream)

    // ZIO.succeed(Response(body = req.body))

    ZIO.succeed(Response(body = data))

//    for {
//      _ <- req.body.asString
//      _ <- req.body.asString
//    } yield Response.ok
  }

  // Run it like any simple app
  val run: UIO[ExitCode] = {
    val server = Server.app(app).withPort(8090)
    server.start.exitCode.provideLayer(EventLoopGroup.auto() ++ ServerChannelFactory.auto)
  }
}
