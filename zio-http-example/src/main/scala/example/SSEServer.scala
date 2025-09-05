//> using dep "dev.zio::zio-http:3.4.1"
//> using dep "dev.zio::zio-streams:2.1.18"

package example

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME

import zio._

import zio.stream.ZStream

import zio.http._

import example.SSEServer.Environment

object SSEServer extends ZIOAppDefault {

  val stream: ZStream[Any, Nothing, ServerSentEvent[String]] =
    ZStream.repeatWithSchedule(ServerSentEvent(ISO_LOCAL_TIME.format(LocalDateTime.now)), Schedule.spaced(1.second))

  val routes: Routes[Any, Response] =
    Routes(
      Method.GET / "sse" ->
        handler(Response.fromServerSentEvents(stream)),
    )

  override val run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    Server.serve(routes).provide(Server.default)
}

object SSEClient extends ZIOAppDefault {

  override def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    (
      for {
        client <- ZIO.service[Client]
        _      <-
          client
            .url(url"http://localhost:8080")
            .batched(
              Request(method = Method.GET, url = url"http://localhost:8080/sse", body = Body.empty)
                .addHeader(Header.Accept(MediaType.text.`event-stream`)),
            )
            .flatMap { response =>
              response.body.asServerSentEvents[String].foreach { event =>
                ZIO.logInfo(event.data)
              }
            }
      } yield ()
    ).provide(ZClient.default)
}
