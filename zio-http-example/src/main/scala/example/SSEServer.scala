package example

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME

import zio.{ExitCode, Schedule, URIO, ZIOAppDefault, durationInt}

import zio.stream.ZStream

import zio.http._

object SSEServer extends ZIOAppDefault {

  val stream: ZStream[Any, Nothing, ServerSentEvent] =
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
