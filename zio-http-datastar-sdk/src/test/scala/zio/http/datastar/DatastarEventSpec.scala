package zio.http.datastar

import zio._
import zio.test._

import zio.stream._

import zio.http._
import zio.http.template2._

object DatastarEventSpec extends ZIOSpecDefault {
  override def spec = suite("DatastarEventSpec")(
    suite("events from ZStream[DatastarEvent]")(
      test("should convert ZStream of PatchElements events to SSE stream") {
        val eventStream = ZStream(
          DatastarEvent.patchElements(div(id := "content1")("Hello")),
          DatastarEvent.patchElements(div(id := "content2")("World")),
        )

        for {
          sseStream <- events(eventStream)
          events    <- sseStream.runCollect
        } yield assertTrue(
          events.length == 2,
          events.head.eventType.contains("datastar-patch-elements"),
          events.head.data == "elements <div id=\"content1\">Hello</div>\n",
          events(1).eventType.contains("datastar-patch-elements"),
          events(1).data == "elements <div id=\"content2\">World</div>\n",
        )
      },
      test("should convert ZStream of PatchSignals events to SSE stream") {
        val eventStream = ZStream(
          DatastarEvent.patchSignals("""{"count": 1}"""),
          DatastarEvent.patchSignals("""{"count": 2}"""),
        )

        for {
          sseStream <- events(eventStream)
          events    <- sseStream.runCollect
        } yield assertTrue(
          events.length == 2,
          events.head.eventType.contains("datastar-patch-signals"),
          events.head.data == """signals {"count": 1}""" + "\n",
          events(1).eventType.contains("datastar-patch-signals"),
          events(1).data == """signals {"count": 2}""" + "\n",
        )
      },
      test("should convert ZStream of ExecuteScript events to SSE stream") {
        val eventStream = ZStream(
          DatastarEvent.executeScript("console.log('test1')"),
          DatastarEvent.executeScript("console.log('test2')"),
        )

        for {
          sseStream <- events(eventStream)
          events    <- sseStream.runCollect
        } yield assertTrue(
          events.length == 2,
          events.head.eventType.contains("datastar-patch-elements"),
          events.head.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove\">console.log('test1')</script>\n",
          events(1).eventType.contains("datastar-patch-elements"),
          events(
            1,
          ).data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove\">console.log('test2')</script>\n",
        )
      },
      test("should handle mixed DatastarEvent types in stream") {
        val eventStream = ZStream(
          DatastarEvent.patchElements(div("Element")),
          DatastarEvent.patchSignals("""{"signal": "value"}"""),
          DatastarEvent.executeScript("console.log('script')"),
        )

        for {
          sseStream <- events(eventStream)
          events    <- sseStream.runCollect
        } yield assertTrue(
          events.length == 3,
          events(0).eventType.contains("datastar-patch-elements"),
          events(0).data == "elements <div>Element</div>\n",
          events(1).eventType.contains("datastar-patch-signals"),
          events(1).data == """signals {"signal": "value"}""" + "\n",
          events(2).eventType.contains("datastar-patch-elements"),
          events(
            2,
          ).data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove\">console.log('script')</script>\n",
        )
      },
      test("should preserve event options in SSE stream") {
        val eventStream = ZStream(
          DatastarEvent.patchElements(
            div("Content"),
            Some(selector"#container"),
            ElementPatchMode.Inner,
            useViewTransition = true,
            Some("event-123"),
            5000.millis,
          ),
        )

        for {
          sseStream <- events(eventStream)
          events    <- sseStream.runCollect
        } yield assertTrue(
          events.length == 1,
          events.head.id.contains("event-123"),
          events.head.retry.contains(5000.millis),
          events.head.data == "selector #container\nmode inner\nuseViewTransition true\nelements <div>Content</div>\n",
        )
      },
    ),
    suite("events from Handler returning ZStream[DatastarEvent]")(
      test("should convert handler returning ZStream of DatastarEvent to Response") {
        val h = handler { (_: Unit) =>
          ZIO.succeed(
            ZStream(
              DatastarEvent.patchElements(div("Test1")),
              DatastarEvent.patchElements(div("Test2")),
            ),
          )
        }

        val responseHandler = events(h)

        for {
          response  <- responseHandler(())
          sseEvents <- response.body.asServerSentEvents[String].runCollect
        } yield assertTrue(
          response.status == Status.Ok,
          response.headers.get(Header.ContentType).exists(_.renderedValue.contains("text/event-stream")),
          response.headers.get(Header.CacheControl).contains(Header.CacheControl.NoCache),
          response.headers.get(Header.Connection).contains(Header.Connection.KeepAlive),
          sseEvents.length == 2,
          sseEvents(0).eventType.contains("datastar-patch-elements"),
          sseEvents(0).data == "elements <div>Test1</div>",
          sseEvents(1).eventType.contains("datastar-patch-elements"),
          sseEvents(1).data == "elements <div>Test2</div>",
        )
      },
      test("should handle handler with input parameter") {
        case class UserAction(userId: Int, action: String)

        val h = handler { (input: UserAction) =>
          ZIO.succeed(
            ZStream(
              DatastarEvent.patchElements(
                div(id := s"user-${input.userId}")(s"Action: ${input.action}"),
              ),
            ),
          )
        }

        val responseHandler = events(h)
        val userAction      = UserAction(42, "update")

        for {
          response  <- responseHandler(userAction)
          sseEvents <- response.body.asServerSentEvents[String].runCollect
        } yield assertTrue(
          response.status == Status.Ok,
          sseEvents.length == 1,
          sseEvents.head.data == "elements <div id=\"user-42\">Action: update</div>",
        )
      },
      test("should stream multiple events from handler") {
        val h = handler { (_: Unit) =>
          ZIO.succeed(
            ZStream(
              DatastarEvent.patchElements(div("Step 1")),
              DatastarEvent.patchSignals("""{"progress": 50}"""),
              DatastarEvent.patchElements(div("Step 2")),
              DatastarEvent.executeScript("console.log('done')"),
            ),
          )
        }

        val responseHandler = events(h)

        for {
          response  <- responseHandler(())
          sseEvents <- response.body.asServerSentEvents[String].runCollect
        } yield assertTrue(
          sseEvents.length == 4,
          sseEvents(0).eventType.contains("datastar-patch-elements"),
          sseEvents(0).data == "elements <div>Step 1</div>",
          sseEvents(1).eventType.contains("datastar-patch-signals"),
          sseEvents(1).data == """signals {"progress": 50}""",
          sseEvents(2).eventType.contains("datastar-patch-elements"),
          sseEvents(2).data == "elements <div>Step 2</div>",
          sseEvents(3).eventType.contains("datastar-patch-elements"),
          sseEvents(
            3,
          ).data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove\">console.log('done')</script>",
        )
      },
      test("should handle handler with effectful stream creation") {
        val h = handler { (count: Int) =>
          ZIO.succeed(
            ZStream.range(0, count).map { i =>
              DatastarEvent.patchElements(div(s"Item $i"))
            },
          )
        }

        val responseHandler = events(h)

        for {
          response <- responseHandler(3)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          body.contains("data: elements <div>Item 0</div>"),
          body.contains("data: elements <div>Item 1</div>"),
          body.contains("data: elements <div>Item 2</div>"),
        )
      },
    ),
    suite("DatastarEvent constructors with flattened parameters")(
      test("patchElements with single selector parameter") {
        val event = DatastarEvent.patchElements(
          div("Content"),
          Some(selector"#target"),
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == "selector #target\nelements <div>Content</div>\n",
        )
      },
      test("patchElements with selector and mode parameters") {
        val event = DatastarEvent.patchElements(
          div("Content"),
          Some(selector"#target"),
          ElementPatchMode.Inner,
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == "selector #target\nmode inner\nelements <div>Content</div>\n",
        )
      },
      test("patchElements with all parameters") {
        val event = DatastarEvent.patchElements(
          List(div("Content1"), div("Content2")),
          Some(selector"#target"),
          ElementPatchMode.Append,
          useViewTransition = true,
          Some("event-id"),
          3000.millis,
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.id.contains("event-id"),
          sse.retry.contains(3000.millis),
          sse.data == "selector #target\nmode append\nuseViewTransition true\nelements <div>Content1</div>\nelements <div>Content2</div>\n",
        )
      },
      test("patchSignals with onlyIfMissing parameter") {
        val event = DatastarEvent.patchSignals(
          """{"key": "value"}""",
          onlyIfMissing = true,
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == """onlyIfMissing true""" + "\n" + """signals {"key": "value"}""" + "\n",
        )
      },
      test("patchSignals with multiple signals and options") {
        val event = DatastarEvent.patchSignals(
          List("""{"a": 1}""", """{"b": 2}"""),
          onlyIfMissing = true,
          Some("sig-123"),
          2000.millis,
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.id.contains("sig-123"),
          sse.retry.contains(2000.millis),
          sse.data == """onlyIfMissing true""" + "\n" + """signals {"a": 1}""" + "\n" + """signals {"b": 2}""" + "\n",
        )
      },
      test("executeScript with Js parameter") {
        val event = DatastarEvent.executeScript(
          Js("console.log('test')"),
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove\">console.log('test')</script>\n",
        )
      },
      test("executeScript with autoRemove false") {
        val event = DatastarEvent.executeScript(
          "alert('hello')",
          autoRemove = false,
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == "selector <body></body>\nmode append\nelements <script>alert('hello')</script>\n",
        )
      },
      test("executeScript with custom attributes") {
        val event = DatastarEvent.executeScript(
          "console.log('test')",
          autoRemove = true,
          Seq("data-custom" -> "value", "data-foo" -> "bar"),
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove\" data-custom=\"value\" data-foo=\"bar\">console.log('test')</script>\n",
        )
      },
      test("executeScript with all parameters") {
        val event = DatastarEvent.executeScript(
          Dom.script("window.location.reload()"),
          autoRemove = false,
          Seq("async" -> "true"),
          Some("script-789"),
          4000.millis,
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.id.contains("script-789"),
          sse.retry.contains(4000.millis),
          sse.data == "selector <body></body>\nmode append\nelements <script async=\"true\">window.location.reload()</script>\n",
        )
      },
    ),
    suite("DatastarEvent toServerSentEvent encoding")(
      test("PatchElements encodes HTML correctly") {
        val event = DatastarEvent.patchElements(
          div(
            h1("Title"),
            p("Paragraph"),
          ),
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == "elements <div><h1>Title</h1><p>Paragraph</p></div>\n",
        )
      },
      test("PatchSignals handles multiple signals correctly") {
        val event = DatastarEvent.patchSignals(
          List(
            """{"user": {"id": 1}}""",
            """{"count": 42}""",
            """{"status": "active"}""",
          ),
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == """signals {"user": {"id": 1}}""" + "\n" + """signals {"count": 42}""" + "\n" + """signals {"status": "active"}""" + "\n",
        )
      },
      test("ExecuteScript handles script with special characters") {
        val scriptContent = """alert('Hello "World"!\nNew Line');"""
        val event         = DatastarEvent.executeScript(scriptContent)

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove\">" + scriptContent + "</script>\n",
        )
      },
      test("default retry duration is not included in SSE") {
        val event = DatastarEvent.patchElements(div("Test"))

        val sse = event.toServerSentEvent

        assertTrue(
          sse.retry.isEmpty, // Default 1000.millis should not be included
        )
      },
      test("non-default retry duration is included in SSE") {
        val event = DatastarEvent.patchElements(
          div("Test"),
          None,
          ElementPatchMode.Outer,
          useViewTransition = false,
          None,
          5000.millis,
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.retry.contains(5000.millis),
        )
      },
    ),
  )
}
