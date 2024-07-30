package example

import java.time.{Instant, LocalDateTime}

import zio._

import zio.stream.ZStream

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec.HttpCodec
import zio.http.endpoint.Endpoint
import zio.http.endpoint.EndpointMiddleware.None

object ServerSentEventAsJsonEndpoint extends ZIOAppDefault {
  import HttpCodec._

  case class Payload(timeStamp: Instant, message: String)

  object Payload {
    implicit val schema: Schema[Payload] = DeriveSchema.gen[Payload]
  }

  val stream: ZStream[Any, Nothing, ServerSentEvent[Payload]] =
    ZStream.repeatWithSchedule(ServerSentEvent(Payload(Instant.now(), "message")), Schedule.spaced(1.second))

  val sseEndpoint: Endpoint[Unit, Unit, ZNothing, ZStream[Any, Nothing, ServerSentEvent[Payload]], None] =
    Endpoint(Method.GET / "sse").outStream[ServerSentEvent[Payload]]

  val sseRoute = sseEndpoint.implementHandler(Handler.succeed(stream))

  val routes: Routes[Any, Response] = sseRoute.toRoutes

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    Server.serve(routes).provide(Server.default).exitCode
  }

}
