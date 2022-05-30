package example

import zhttp.http._
import zhttp.http.middleware.HttpMiddleware
import zhttp.service.Server
import zio.clock.{Clock, currentTime}
import zio.console.Console
import zio.duration._
import zio.{App, ExitCode, URIO, ZIO}

import java.io.IOException
import java.util.concurrent.TimeUnit

object HelloWorldWithMiddlewares extends App {

  val app: HttpApp[Clock, Nothing] = Http.collectZIO[Request] {
    // this will return result instantly
    case Method.GET -> !! / "text"         => ZIO.succeed(Response.text("Hello World!"))
    // this will return result after 5 seconds, so with 3 seconds timeout it will fail
    case Method.GET -> !! / "long-running" => ZIO.succeed(Response.text("Hello World!")).delay(5 seconds)
  }

  val serverTime: HttpMiddleware[Clock, Nothing] = Middleware.patchZIO(_ =>
    for {
      currentMilliseconds <- currentTime(TimeUnit.MILLISECONDS)
      withHeader = Patch.addHeader("X-Time", currentMilliseconds.toString)
    } yield withHeader,
  )

  val middlewares: HttpMiddleware[Console with Clock, IOException] =
    // print debug info about request and response
    Middleware.debug ++
      // close connection if request takes more than 3 seconds
      Middleware.timeout(3 seconds) ++
      // add static header
      Middleware.addHeader("X-Environment", "Dev") ++
      // add dynamic header
      serverTime

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app @@ middlewares).exitCode
}
