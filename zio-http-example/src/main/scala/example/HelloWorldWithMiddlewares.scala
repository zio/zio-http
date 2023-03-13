package example

import java.io.IOException
import java.util.concurrent.TimeUnit

import zio._

import zio.http._
import zio.http.model.Method

object HelloWorldWithMiddlewares extends ZIOAppDefault {

  val app: HttpApp[Any, Nothing] = Http.collectZIO[Request] {
    // this will return result instantly
    case Method.GET -> !! / "text"         => ZIO.succeed(Response.text("Hello World!"))
    // this will return result after 5 seconds, so with 3 seconds timeout it will fail
    case Method.GET -> !! / "long-running" => ZIO.succeed(Response.text("Hello World!")).delay(5 seconds)
  }

  val serverTime: RequestHandlerMiddleware[Nothing, Any, Nothing, Any] = HttpAppMiddleware.patchZIO(_ =>
    for {
      currentMilliseconds <- Clock.currentTime(TimeUnit.MILLISECONDS)
      withHeader = Patch.addHeader("X-Time", currentMilliseconds.toString)
    } yield withHeader,
  )
  val middlewares =
    // print debug info about request and response
    HttpAppMiddleware.debug ++
      // close connection if request takes more than 3 seconds
      HttpAppMiddleware.timeout(3 seconds) ++
      // add static header
      HttpAppMiddleware.addHeader("X-Environment", "Dev") ++
      // add dynamic header
      serverTime

  // Run it like any simple app
  val run = Server.serve((app @@ middlewares).withDefaultErrorResponse).provide(Server.default)
}
