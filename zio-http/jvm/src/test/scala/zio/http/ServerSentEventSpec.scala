package zio.http

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME

import scala.util.Try

import zio._
import zio.test._

import zio.stream.ZStream

object ServerSentEventSpec extends ZIOSpecDefault {

  val stream: ZStream[Any, Nothing, ServerSentEvent[String]] =
    ZStream.repeatWithSchedule(ServerSentEvent(ISO_LOCAL_TIME.format(LocalDateTime.now)), Schedule.spaced(1.second))

  val app: Routes[Any, Response] =
    Routes(
      Method.GET / "sse" ->
        handler(Response.fromServerSentEvents(stream)),
    )

  val server =
    Server.install(app)

  def client(port: Int): ZIO[Any, Throwable, Chunk[ServerSentEvent[String]]] = ZIO
    .scoped(for {
      client   <- ZIO.service[Client]
      response <- client
        .url(url"http://localhost:$port")
        .request(
          Request(method = Method.GET, url = url"http://localhost:$port/sse", body = Body.empty)
            .addHeader(Header.Accept(MediaType.text.`event-stream`)),
        )
      events   <- response.body.asServerSentEvents[String].take(5).runCollect
    } yield events)
    .provide(ZClient.default)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ServerSentEventSpec")(
      test("Send and receive ServerSentEvent with string payload") {
        for {
          _      <- server.fork
          port   <- ZIO.serviceWithZIO[Server](_.port)
          events <- client(port)
        } yield assertTrue(events.size == 5 && events.forall(event => Try(ISO_LOCAL_TIME.parse(event.data)).isSuccess))
      }.provide(Server.defaultWithPort(0)),
    ) @@ TestAspect.withLiveClock
}
