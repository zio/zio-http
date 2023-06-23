package example

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME

import zio._

import zio.stream.ZStream

import zio.http._
import zio.http.codec.HttpCodec
import zio.http.endpoint.EndpointMiddleware.None
import zio.http.endpoint.{Endpoint, Routes}

object ServerSentEventEndpoint extends ZIOAppDefault {
  import HttpCodec._

  val stream: ZStream[Any, Nothing, ServerSentEvent] =
    ZStream.repeatWithSchedule(ServerSentEvent(ISO_LOCAL_TIME.format(LocalDateTime.now)), Schedule.spaced(1.second))

  val sseEndpoint: Endpoint[Unit, Unit, ZNothing, ZStream[Any, Nothing, ServerSentEvent], None] =
    Endpoint(Method.GET / "sse").outStream[ServerSentEvent]

  val sseRoute: Routes[Any, None] = sseEndpoint.implement(_ => ZIO.succeed(stream))

  val app: App[Any] = sseRoute.toApp

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    Server.serve(app).provide(Server.default).exitCode
  }

}
