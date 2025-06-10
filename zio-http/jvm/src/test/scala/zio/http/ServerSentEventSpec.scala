package zio.http

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME

import scala.util.Try

import zio._
import zio.test._

import zio.stream.ZStream

object ServerSentEventSpec extends ZIOHttpSpec {

  val stream: ZStream[Any, Nothing, ServerSentEvent[String]] =
    ZStream.repeatWithSchedule(
      ServerSentEvent(ISO_LOCAL_TIME.format(LocalDateTime.now), retry = Some(10.milliseconds)),
      Schedule.spaced(1.second),
    )

  val routes: Routes[Any, Response] =
    Routes(
      Method.GET / "sse" ->
        handler(Response.fromServerSentEvents(stream)),
    )

  val server =
    Server.installRoutes(routes)

  def eventStream(port: Int): ZStream[Client, Throwable, ServerSentEvent[String]] =
    for {
      client <- ZStream.service[Client]
      event  <-
        client
          .url(url"http://localhost:$port")
          .addHeader(Header.Accept(MediaType.text.`event-stream`))
          .stream(
            Request(method = Method.GET, url = url"/sse", body = Body.empty),
          )(_.body.asServerSentEvents[String])
    } yield event

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ServerSentEventSpec")(
      test("Send and receive ServerSentEvent with string payload") {
        for {
          _      <- server.fork
          port   <- ZIO.serviceWithZIO[Server](_.port)
          events <- eventStream(port).take(5).runCollect
        } yield assertTrue(
          events.size == 5,
          events.forall(event => Try(ISO_LOCAL_TIME.parse(event.data)).isSuccess),
          events.forall(_.retry.contains(10.milliseconds)),
        )
      }.provide(Server.defaultWithPort(0), ZClient.default),
    ) @@ TestAspect.withLiveClock
}
