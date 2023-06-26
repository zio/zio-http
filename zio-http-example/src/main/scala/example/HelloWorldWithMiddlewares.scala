package example

import java.util.concurrent.TimeUnit

import zio._

import zio.http._

object HelloWorldWithMiddlewares extends ZIOAppDefault {

  val app: App[Any] = Routes(
    // this will return result instantly
    Method.GET / "text"         -> handler(ZIO.succeed(Response.text("Hello World!"))),
    // this will return result after 5 seconds, so with 3 seconds timeout it will fail
    Method.GET / "long-running" -> handler(ZIO.succeed(Response.text("Hello World!")).delay(5 seconds)),
  ).toApp

  val serverTime: RequestHandlerMiddleware[Nothing, Any, Nothing, Any] = HttpAppMiddleware.patchZIO(_ =>
    for {
      currentMilliseconds <- Clock.currentTime(TimeUnit.MILLISECONDS)
      header = Response.Patch.addHeader("X-Time", currentMilliseconds.toString)
    } yield header,
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
