import zhttp.http._
import zhttp.http.middleware.HttpMiddleware._
import zhttp.http.middleware.{HttpMiddleware, Patch}
import zhttp.service.Server
import zio._
import zio.clock.{Clock, currentTime}
import zio.console.Console
import zio.duration.durationInt

import java.io.IOException
import java.util.concurrent.TimeUnit

object HelloWorldWithMiddlewares extends App {

  val app: HttpApp[Clock, Nothing] = HttpApp.collectM {
    // this will return result instantly
    case Method.GET -> !! / "text"         => ZIO.succeed(Response.text("Hello World!"))
    // this will return result after 5 seconds, so with 3 seconds timeout it will fail
    case Method.GET -> !! / "long-running" => ZIO.succeed(Response.text("Hello World!")).delay(5 seconds)
  }

  val middlewares: HttpMiddleware[Console with Clock, IOException] =
    // print debug info about request and response
    debug ++
      // close connection if request takes more than 3 seconds
      timeout(3 seconds) ++
      // add static header
      addHeader("X-Environment", "Dev") ++
      // add dynamic header
      patchM((_, _) =>
        for {
          currentMilliseconds <- currentTime(TimeUnit.MILLISECONDS)
          withHeader = Patch.addHeaders(List(Header("X-Time", currentMilliseconds.toString)))
        } yield withHeader,
      )

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, (app @@ middlewares).silent).exitCode
}
