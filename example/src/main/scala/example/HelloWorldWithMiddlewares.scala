package example

import zhttp.http.Middleware.{addHeader, debug, patchM, timeout}
import zhttp.http.{Header, HttpApp, Method, Patch, Response, _}
import zhttp.service.Server
import zio._

import java.io.IOException
import java.util.concurrent.TimeUnit

object HelloWorldWithMiddlewares extends ZIOAppDefault {

  val app: HttpApp[Clock, Nothing] = Http.collectM[Request] {
    // this will return result instantly
    case Method.GET -> !! / "text"         => ZIO.succeed(Response.text("Hello World!"))
    // this will return result after 5 seconds, so with 3 seconds timeout it will fail
    case Method.GET -> !! / "long-running" => ZIO.succeed(Response.text("Hello World!")).delay(5 seconds)
  }

  val serverTime: Middleware[Clock, Nothing] = patchM((_, _) =>
    for {
      currentMilliseconds <- Clock.currentTime(TimeUnit.MILLISECONDS)
      withHeader = Patch.addHeaders(List(Header("X-Time", currentMilliseconds.toString)))
    } yield withHeader,
  )

  val middlewares: Middleware[Console with Clock, IOException] =
    // print debug info about request and response
    debug ++
      // close connection if request takes more than 3 seconds
      timeout(3 seconds) ++
      // add static header
      addHeader("X-Environment", "Dev") ++
      // add dynamic header
      serverTime

  // Run it like any simple app
  override val run =
    Server.start(8090, (app @@ middlewares).silent)
}
