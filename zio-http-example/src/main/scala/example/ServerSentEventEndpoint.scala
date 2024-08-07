package example

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME

import zio._

import zio.stream.ZStream

import zio.http._
import zio.http.codec.HttpCodec
import zio.http.endpoint._

object ServerSentEventEndpoint extends ZIOAppDefault {

  val sseEndpoint: Endpoint[Unit, Unit, ZNothing, ZStream[Any, Nothing, ServerSentEvent[String]], AuthType.None] =
    Endpoint(Method.GET / "sse")
      .outStream[ServerSentEvent[String]](MediaType.text.`event-stream`)
      .inCodec(HttpCodec.header(Header.Accept).const(Header.Accept(MediaType.text.`event-stream`)))

  val stream: ZStream[Any, Nothing, ServerSentEvent[String]] =
    ZStream.repeatWithSchedule(ServerSentEvent(ISO_LOCAL_TIME.format(LocalDateTime.now)), Schedule.spaced(1.second))

  val sseRoute = sseEndpoint.implementHandler(Handler.succeed(stream))

  val routes: Routes[Any, Response] =
    sseRoute.toRoutes @@ Middleware.requestLogging(logRequestBody = true) @@ Middleware.debug

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    Server.serve(routes).provide(Server.default).exitCode
  }

}

object ServerSentEventEndpointClient extends ZIOAppDefault {
  val locator: EndpointLocator = EndpointLocator.fromURL(url"http://localhost:8080")

  private val invocation
    : Invocation[Unit, Unit, ZNothing, ZStream[Any, Nothing, ServerSentEvent[String]], AuthType.None, Unit] =
    ServerSentEventEndpoint.sseEndpoint(())

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    (for {
      client <- ZIO.service[Client]
      executor = EndpointExecutor(client, locator)
      stream <- executor(invocation)
      _      <- stream.foreach(event => ZIO.logInfo(event.data))
    } yield ()).provideSome[Scope](ZClient.default)
}
