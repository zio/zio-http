package zio.http

import zio._
import zio.test.{Spec, TestEnvironment, TestResult, assertTrue}

import zio.stream.ZStream

object ClientServerSentEventSpec extends ZIOHttpSpec {
  def eventStream(data: String): ZStream[Any, Throwable, ServerSentEvent[String]] = {
    val response = Routes(
      Method.GET / "sse" -> Handler.fromResponse(
        Response(headers = Headers(Header.ContentType(MediaType.text.`event-stream`)), body = Body.fromString(data)),
      ),
    ).runZIO(Request(method = Method.GET, url = url"/sse", body = Body.empty)).map(_.body.asServerSentEvents[String])
    ZStream.unwrapScoped(response)
  }

  def assertEvents(
    streamData: String,
    expectedEvents: Chunk[ServerSentEvent[String]],
  ): Task[TestResult] =
    eventStream(streamData).runCollect.map(actual => assertTrue(actual == expectedEvents))

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ServerSentEventClientSpec")(
      test("Line breaks only payload does not emit events") {
        val events =
          """|
             |
             |
             |""".stripMargin
        assertEvents(events, Chunk.empty)
      },
      test("Successive line breaks do not emit extra events") {
        val events =
          """|data: data1
             |event: sse1
             |
             |
             |data: data2
             |event: sse2
             |
             |
             |""".stripMargin
        assertEvents(
          events,
          Chunk(
            ServerSentEvent(data = "data1", eventType = Some("sse1")),
            ServerSentEvent(data = "data2", eventType = Some("sse2")),
          ),
        )
      },
      test("Successive data fields are concatenated") {
        val events       =
          """|data: data1
             |data: data2
             |data: data3
             |
             |""".stripMargin
        val expectedData =
          """|data1
             |data2
             |data3""".stripMargin
        assertEvents(events, Chunk.single(ServerSentEvent(data = expectedData)))
      },
      test("Out of order data fields are concatenated") {
        val events       =
          """|data: data1
             |data: data2
             |event: sse
             |data: data3
             |
             |""".stripMargin
        val expectedData =
          """|data1
             |data2
             |data3""".stripMargin
        assertEvents(events, Chunk.single(ServerSentEvent(data = expectedData, eventType = Some("sse"))))
      },
      test("Empty data fields are included") {
        // IDEs tend to trim lines on multiline strings, so build event from collection
        val events       =
          Chunk("data: data", "data: ", "data:", "data", "data: end", "event: sse").mkString("", "\n", "\n\n")
        val expectedData =
          """|data
             |
             |
             |
             |end""".stripMargin
        assertEvents(events, Chunk.single(ServerSentEvent(data = expectedData, eventType = Some("sse"))))
      },
      test("Events with empty data are emitted") {
        // IDEs tend to trim lines on multiline strings, so build event from collection
        val events = Chunk("data: ", "data:", "data").mkString("", "\n\n", "\n\n")
        assertEvents(events, Chunk.fill(3)(ServerSentEvent(data = "")))
      },
      test("Events with successive empty data fields are concatenated") {
        // IDEs tend to trim lines on multiline strings, so build event from collection
        val events       = Chunk("data: ", "data:", "data").mkString("", "\n", "\n\n")
        val expectedData =
          """|
             |
             |""".stripMargin
        assertEvents(events, Chunk.single(ServerSentEvent(data = expectedData)))
      },
      test("Event type can be overridden") {
        val events =
          """|event: sse
             |data: data
             |event: changed-sse
             |
             |""".stripMargin
        assertEvents(events, Chunk.single(ServerSentEvent(data = "data", eventType = Some("changed-sse"))))
      },
      test("Event type can be unset") {
        // IDEs tend to trim lines on multiline strings, so build event from collection
        val events = Chunk("event: ", "event:", "event").map { field =>
          s"""|event: sse
              |data: data
              |$field""".stripMargin
        }.mkString("", "\n\n", "\n\n")
        assertEvents(events, Chunk.fill(3)(ServerSentEvent(data = "data")))
      },
      test("Event type without data is not emitted") {
        val events =
          """|event: sse
             |
             |""".stripMargin
        assertEvents(events, Chunk.empty)
      },
      test("Event id can be overridden") {
        val events =
          """|id: 1
             |data: data
             |id: 2
             |
             |""".stripMargin
        assertEvents(events, Chunk.single(ServerSentEvent(data = "data", id = Some("2"))))
      },
      test("Event id can be unset") {
        // IDEs tend to trim lines on multiline strings, so build event from collection
        val events = Chunk("id: ", "id:", "id").map { field =>
          s"""|id: 1
              |data: data
              |$field""".stripMargin
        }.mkString("", "\n\n", "\n\n")
        assertEvents(events, Chunk.fill(3)(ServerSentEvent(data = "data")))
      },
      test("Event id without data is not emitted") {
        val events =
          """|id: 1
             |
             |""".stripMargin
        assertEvents(events, Chunk.empty)
      },
      test("Event id can be a string") {
        val events =
          """|id: event-id
             |data: data
             |
             |""".stripMargin
        assertEvents(events, Chunk.single(ServerSentEvent(data = "data", id = Some("event-id"))))
      },
      test("Event retry can be overridden") {
        val events =
          """|retry: 1
             |data: data
             |retry: 2
             |
             |""".stripMargin
        assertEvents(events, Chunk.single(ServerSentEvent(data = "data", retry = Some(2.milliseconds))))
      },
      test("Event retry can be unset") {
        // IDEs tend to trim lines on multiline strings, so build event from collection
        val events = Chunk("retry: ", "retry:", "retry").map { field =>
          s"""|retry: 1
              |data: data
              |$field""".stripMargin
        }.mkString("", "\n\n", "\n\n")
        assertEvents(events, Chunk.fill(3)(ServerSentEvent(data = "data")))
      },
      test("Event retry without data is emitted") {
        val events =
          """|retry: 1
             |
             |""".stripMargin
        assertEvents(events, Chunk.single(ServerSentEvent(data = "", retry = Some(1.millisecond))))
      },
      test("Event retry that is not an integer is not emitted") {
        val events =
          """|retry: 1.1
             |
             |retry: 1,1
             |
             |retry: "1"
             |
             |retry: 1s
             |
             |""".stripMargin
        assertEvents(events, Chunk.empty)
      },
      test("Event retry with negative value is not emitted") {
        val events =
          """|retry: -1
             |
             |""".stripMargin
        assertEvents(events, Chunk.empty)
      },
      test("Event retry can be set to immediate retry") {
        val events =
          """|retry: 0
             |
             |""".stripMargin
        assertEvents(events, Chunk.single(ServerSentEvent(data = "", retry = Some(0.millisecond))))
      },
      test("Event field order is not important") {
        val events = Chunk("data: data", "event: sse", "id: 1", "retry: 1").permutations
          .map(_.mkString("\n"))
          .mkString("", "\n\n", "\n\n")
        assertEvents(
          events,
          Chunk.fill(24)(
            ServerSentEvent(data = "data", eventType = Some("sse"), id = Some("1"), retry = Some(1.millisecond)),
          ),
        )
      },
      test("Comment lines are not included in events") {
        val events =
          """|: keep-alive
             |data: data
             |
             |: keep-alive
             |
             |data: data
             |: keep-alive
             |
             |data: data
             |:keep-alive
             |
             |""".stripMargin
        assertEvents(events, Chunk.fill(3)(ServerSentEvent(data = "data")))
      },
      test("Other fields are ignored") {
        val events =
          """|datanot: data
             |data: data
             |
             |byitself: should-ignore
             |
             |eventnot
             |data: data
             |
             |""".stripMargin
        assertEvents(events, Chunk.fill(2)(ServerSentEvent(data = "data")))
      },
      test("Does not emit in progress event on stream end") {
        val events =
          """|data: data
             |
             |data: in-progress""".stripMargin
        assertEvents(events, Chunk.single(ServerSentEvent(data = "data")))
      },
      test("Does not emit in progress event with single LF on stream end") {
        val events =
          """|data: data
             |
             |data: in-progress
             |""".stripMargin
        assertEvents(events, Chunk.single(ServerSentEvent(data = "data")))
      },
      test("Only first space is removed from field value") {
        val events =
          """|data:  data
             |event:  sse
             |id:  1
             |retry:  1
             |
             |""".stripMargin
        assertEvents(
          events,
          Chunk.single(
            ServerSentEvent(data = " data", eventType = Some(" sse"), id = Some(" 1"), retry = Option.empty[Duration]),
          ),
        )
      },
    )
}
