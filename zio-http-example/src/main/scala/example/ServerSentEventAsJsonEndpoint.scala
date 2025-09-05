//> using dep "dev.zio::zio-http:3.4.1"
//> using dep "dev.zio::zio-streams:2.1.18"
//> using dep "dev.zio::zio-schema:1.7.2"

package example

import java.time.Instant

import zio._

import zio.stream.ZStream

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec._
import zio.http.endpoint._

object ServerSentEventAsJsonEndpoint extends ZIOAppDefault {

  final case class Payload(timeStamp: Instant, message: String)
  object Payload {
    implicit val schema: Schema[Payload] = DeriveSchema.gen[Payload]
  }

  private val stream: ZStream[Any, Nothing, ServerSentEvent[Payload]] =
    ZStream.repeatWithSchedule(ServerSentEvent(Payload(Instant.now(), "message")), Schedule.spaced(1.second))

  val sseEndpoint: Endpoint[Unit, Unit, ZNothing, ZStream[Any, Nothing, ServerSentEvent[Payload]], AuthType.None] =
    Endpoint(Method.GET / "sse")
      .outStream[ServerSentEvent[Payload]]
      .inCodec(HttpCodec.header(Header.Accept).const(Header.Accept(MediaType.text.`event-stream`)))

  private val sseRoute = sseEndpoint.implementHandler(Handler.succeed(stream))

  private val routes: Routes[Any, Response] = sseRoute.toRoutes

  override def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    Server.serve(routes).provide(Server.default)

}

object ServerSentEventAsJsonEndpointClient extends ZIOAppDefault {
  val locator: EndpointLocator = EndpointLocator.fromURL(url"http://localhost:8080")

  private val invocation = ServerSentEventAsJsonEndpoint.sseEndpoint(())

  override def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    (
      for {
        client <- ZIO.service[Client]
        executor = EndpointExecutor(client, locator)
        stream <- executor(invocation)
        _      <- stream.foreach(event => ZIO.logInfo(event.data.toString))
      } yield ()
    ).provideSome[Scope](ZClient.default)
}
