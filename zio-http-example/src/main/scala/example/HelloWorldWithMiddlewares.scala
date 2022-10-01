package example

import zio._
import zio.http._
import zio.http.middleware.HttpMiddleware
import zio.http.model.Method

import java.io.IOException
import java.util.concurrent.TimeUnit

object HelloWorldWithMiddlewares extends ZIOAppDefault {

  val app: HttpApp[Any, Nothing] = Http.collectZIO[Request] { request =>
    request match {
      // this will return result instantly
      case Method.GET -> !! / "text"         => ZIO.succeed(Response.text("Hello World!"))
      // this will return result after 5 seconds, so with 3 seconds timeout it will fail
      case Method.GET -> !! / "long-running" => ZIO.succeed(Response.text("Hello World!")).delay(5 seconds)
    }
  }

  val serverTime: HttpMiddleware[Any, Nothing] = Middleware.patchZIO(_ =>
    for {
      currentMilliseconds <- Clock.currentTime(TimeUnit.MILLISECONDS)
      withHeader = Patch.addHeader("X-Time", currentMilliseconds.toString)
    } yield withHeader,
  )

  val middlewares: HttpMiddleware[Any, IOException] =
    // add static header
    Middleware.addHeader("X-Environment", "Dev")

  // Run it like any simple app
  val run = Server.serve(app @@ middlewares).provide(Server.default)
}
