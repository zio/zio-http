package example

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME

import zio._

import zio.stream.ZStream

import zio.http._

object SSEServer extends ZIOAppDefault {

  val stream: ZStream[Any, Nothing, ServerSentEvent[String]] =
    ZStream.repeatWithSchedule(ServerSentEvent(ISO_LOCAL_TIME.format(LocalDateTime.now)), Schedule.spaced(1.second))

  val app: Routes[Any, Response] =
    Routes(
      Method.GET / "sse" ->
        handler(Response.fromServerSentEvents(stream)),
    )

  val run: URIO[Any, ExitCode] = {
    Server.serve(app).provide(Server.default).exitCode
  }
}

object SSEClient extends ZIOAppDefault {

  override def run =
    (for {
      client <- ZIO.service[Client]
      _      <-
        client
          .url(url"http://localhost:8080")
          .simple(
            Request(method = Method.GET, url = url"http://localhost:8080/sse", body = Body.empty)
              .addHeader(Header.Accept(MediaType.text.`event-stream`)),
          )
          .flatMap { response =>
            response.body.asServerSentEvents[String].foreach { event =>
              ZIO.logInfo(event.data)
            }
          }
    } yield ()).provide(ZClient.default)
}
