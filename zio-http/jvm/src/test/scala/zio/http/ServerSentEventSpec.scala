package zio.http

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME

import scala.util.Try

import zio._
import zio.test._

import zio.stream.ZStream

import zio.schema.codec.BinaryCodec

import zio.http.codec.HttpContentCodec

object ServerSentEventSpec extends ZIOHttpSpec {
  implicit val stringCodec: BinaryCodec[String] = HttpContentCodec.text.only[String].defaultCodec

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

  private val encodeSpec =
    suite("::encode")(
      test("single string data") {
        val event  = ServerSentEvent(data = "hello world")
        val result = event.encode
        assertTrue(
          result ==
            """data: hello world
              |
              |""".stripMargin,
        )
      },
      test("multiline string data") {
        val event  = ServerSentEvent(data = "line1\nline2\nline3")
        val result = event.encode
        assertTrue(
          result ==
            """data: line1
              |data: line2
              |data: line3
              |
              |""".stripMargin,
        )
      },
      test("data with eventType") {
        val event  = ServerSentEvent(data = "test data", eventType = Some("message"))
        val result = event.encode
        assertTrue(
          result ==
            """event: message
              |data: test data
              |
              |""".stripMargin,
        )
      },
      test("data with id") {
        val event  = ServerSentEvent(data = "test data", id = Some("123"))
        val result = event.encode
        assertTrue(
          result ==
            """data: test data
              |id: 123
              |
              |""".stripMargin,
        )
      },
      test("data with retry") {
        val event  = ServerSentEvent(data = "test data", retry = Some(5000.milliseconds))
        val result = event.encode
        assertTrue(
          result ==
            """data: test data
              |retry: 5000
              |
              |""".stripMargin,
        )
      },
      test("all fields") {
        val event  = ServerSentEvent(
          "test data",
          eventType = Some("update"),
          id = Some("456"),
          retry = Some(3000.milliseconds),
        )
        val result = event.encode
        assertTrue(
          result ==
            """event: update
              |data: test data
              |id: 456
              |retry: 3000
              |
              |""".stripMargin,
        )
      },
      test("eventType with newlines gets flattened") {
        val event  = ServerSentEvent(data = "data", eventType = Some("message\nwith\nnewlines"))
        val result = event.encode
        assertTrue(
          result ==
            """event: message with newlines
              |data: data
              |
              |""".stripMargin,
        )
      },
      test("id with newlines gets flattened") {
        val event  = ServerSentEvent(data = "data", id = Some("id\nwith\nnewlines"))
        val result = event.encode
        assertTrue(
          result ==
            """data: data
              |id: id with newlines
              |
              |""".stripMargin,
        )
      },
      test("empty string data") {
        val event  = ServerSentEvent(data = "")
        val result = event.encode
        assertTrue(
          result ==
            """data:
              |
              |""".stripMargin,
        )
      },
      test("eventType with carriage returns gets flattened") {
        val event  = ServerSentEvent(data = "data", eventType = Some("message\r\nwith\rreturns"))
        val result = event.encode
        assertTrue(
          result ==
            """event: message with returns
              |data: data
              |
              |""".stripMargin,
        )
      },
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ServerSentEventSpec")(
      encodeSpec,
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
