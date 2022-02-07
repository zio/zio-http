package example

import zhttp.http._
import zhttp.service.Server
import zio._

object HelloWorldWithLogging extends ZIOAppDefault {
  override def hook = slf4j

  import zio.logging.backend._

  val slf4j: RuntimeConfigAspect = SLF4J.slf4j(LogLevel.Info)

  val app: HttpApp[Clock, Nothing] = Http.collectZIO[Request] {
    // this will return result instantly
    case Method.GET -> !! / "text"         => ZIO.succeed(Response.text("Hello World!"))
    // this will return result after 5 seconds, so with 3 seconds timeout it will fail
    case Method.GET -> !! / "long-running" => ZIO.succeed(Response.text("Hello World!")).delay(5 seconds)
  }

  val logger = Middleware.log()

  // Run it like any simple app
  val run: URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, (app @@ logger).silent).exitCode
}
