package example

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME

import zio._

import zio.stream.ZStream

import zio.http._
import zio.http.codec.HttpCodec
import zio.http.endpoint.Endpoint
import zio.http.endpoint.EndpointMiddleware.None

object ServerSentEventEndpoint extends ZIOAppDefault {
  import HttpCodec._

  val stream: ZStream[Any, Nothing, ServerSentEvent[String]] =
    ZStream.repeatWithSchedule(ServerSentEvent(ISO_LOCAL_TIME.format(LocalDateTime.now)), Schedule.spaced(1.second))

  val sseEndpoint: Endpoint[Unit, Unit, ZNothing, ZStream[Any, Nothing, ServerSentEvent[String]], None] =
    Endpoint(Method.GET / "sse").outStream[ServerSentEvent[String]]

  val sseRoute = sseEndpoint.implementHandler(Handler.succeed(stream))

  val routes: Routes[Any, Response] = sseRoute.toRoutes

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    Server.serve(routes).provide(Server.default).exitCode
  }

}
