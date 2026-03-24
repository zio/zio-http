package zio.http.datastar

import zio._
import zio.json.ast.Json
import zio.test._

import zio.stream._

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.template2._

object DatastarEventSpec extends ZIOSpecDefault {
  case class CountUpdate(count: Int)
  implicit val schema: Schema[CountUpdate] = DeriveSchema.gen[CountUpdate]
  case class Inner(x: Int, y: String)
  implicit val innerSchema: Schema[Inner]  = DeriveSchema.gen[Inner]
  case class Outer(inner: Inner, flag: Boolean)
  implicit val outerSchema: Schema[Outer]  = DeriveSchema.gen[Outer]
  override def spec                        = suite("DatastarEventSpec")(
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
          DatastarEvent.patchSignals(CountUpdate(1)),
          DatastarEvent.patchSignals(CountUpdate(2)),
        )

        for {
          sseStream <- events(eventStream)
          events    <- sseStream.runCollect
        } yield assertTrue(
          events.length == 2,
          events.head.eventType.contains("datastar-patch-signals"),
          events.head.data == """signals {"count":1}""" + "\n",
          events(1).eventType.contains("datastar-patch-signals"),
          events(1).data == """signals {"count":2}""" + "\n",
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
          events.head.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">console.log('test1')</script>\n",
          events(1).eventType.contains("datastar-patch-elements"),
          events(
            1,
          ).data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">console.log('test2')</script>\n",
        )
      },
      test("should handle mixed DatastarEvent types in stream") {
        val eventStream = ZStream(
          DatastarEvent.patchElements(div("Element")),
          DatastarEvent.patchSignals("signal" -> "value"),
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
          events(1).data == """signals {"signal":"value"}""" + "\n",
          events(2).eventType.contains("datastar-patch-elements"),
          events(
            2,
          ).data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">console.log('script')</script>\n",
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
              DatastarEvent.patchSignals("progress" -> "50"),
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
          sseEvents(1).data == """signals {"progress":"50"}""",
          sseEvents(2).eventType.contains("datastar-patch-elements"),
          sseEvents(2).data == "elements <div>Step 2</div>",
          sseEvents(3).eventType.contains("datastar-patch-elements"),
          sseEvents(
            3,
          ).data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">console.log('done')</script>",
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
          Dom.fragment(div("Content1"), div("Content2")),
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
          sse.data == "selector #target\nmode append\nuseViewTransition true\nelements <div>Content1</div><div>Content2</div>\n",
        )
      },
      test("patchSignals with onlyIfMissing parameter") {
        val event = DatastarEvent.patchSignals(
          "key" -> "value",
          onlyIfMissing = true,
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == """onlyIfMissing true""" + "\n" + """signals {"key":"value"}""" + "\n",
        )
      },
      test("patchSignals with multiple signals and options") {
        val event = DatastarEvent.patchSignals(
          Seq("a" -> "1", "b" -> "2"),
          onlyIfMissing = true,
          Some("sig-123"),
          2000.millis,
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.id.contains("sig-123"),
          sse.retry.contains(2000.millis),
          sse.data == """onlyIfMissing true""" + "\n" + """signals {"a":"1","b":"2"}""" + "\n",
        )
      },
      test("executeScript with Js parameter") {
        val event = DatastarEvent.executeScript(
          Js("console.log('test')"),
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">console.log('test')</script>\n",
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
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\" data-custom=\"value\" data-foo=\"bar\">console.log('test')</script>\n",
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
    suite("PatchElements with namespace")(
      test("PatchElements with namespace generates correct SSE") {
        val event = DatastarEvent.PatchElements(
          elements = div(id := "svg-content")("Hello SVG"),
          namespace = Some("http://www.w3.org/2000/svg"),
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data.contains("namespace http://www.w3.org/2000/svg"),
          sse.data.contains("elements"),
        )
      },
      test("PatchElements without namespace omits namespace line") {
        val event = DatastarEvent.patchElements(
          div("Simple content"),
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == "elements <div>Simple content</div>\n",
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
          Json.Obj(
            "user"   -> Json.Obj("id" -> Json.Num(1)),
            "count"  -> Json.Num(42),
            "status" -> Json.Str("active"),
          ),
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == """signals {"user":{"id":1},"count":42,"status":"active"}""" + "\n",
        )
      },
      test("ExecuteScript handles script with special characters") {
        val scriptContent = """alert('Hello "World"!\nNew Line');"""
        val event         = DatastarEvent.executeScript(scriptContent)

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">" + scriptContent + "</script>\n",
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
    suite("PatchElements with multi-line content")(
      test("handles script tags with multi-line JavaScript") {
        val scriptContent = """console.log('line 1');
                              |console.log('line 2');
                              |console.log('line 3');""".stripMargin
        val event         = DatastarEvent.patchElements(
          Dom.script(scriptContent),
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == """elements <script>console.log('line 1');
                        |elements console.log('line 2');
                        |elements console.log('line 3');</script>
                        |""".stripMargin,
        )
      },
      test("handles style tags with multi-line CSS") {
        val cssContent = """.button {
                           |  color: red;
                           |  background: blue;
                           |}""".stripMargin
        val event      = DatastarEvent.patchElements(
          Dom.style(cssContent),
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == """elements <style>.button {
                        |elements   color: red;
                        |elements   background: blue;
                        |elements }</style>
                        |""".stripMargin,
        )
      },
      test("handles inline style attribute with newlines") {
        val styleContent = """color: red;
                             |background: blue;
                             |padding: 10px;""".stripMargin
        val event        = DatastarEvent.patchElements(
          div(Dom.attr("style", styleContent))("Content"),
        )

        val sse = event.toServerSentEvent

        // The minified output should handle newlines in attribute values
        assertTrue(
          sse.data.startsWith("elements ") &&
            sse.data.contains("style=") &&
            sse.data.contains("Content"),
        )
      },
      test("handles complex HTML with embedded script and style") {
        val event = DatastarEvent.patchElements(
          div(
            Dom.style(""".test {
                        |  color: red;
                        |}""".stripMargin),
            Dom.script("""console.log('test');
                         |alert('hello');""".stripMargin),
            p("Content"),
          ),
        )

        val sse = event.toServerSentEvent

        // Should split on newlines and prefix each line with "elements "
        val lines = sse.data.split('\n').filter(_.nonEmpty)
        assertTrue(
          lines.forall(_.startsWith("elements ")),
          lines.length > 1, // Multi-line content
          sse.data.contains(".test"),
          sse.data.contains("console.log"),
          sse.data.contains("Content"),
        )
      },
      test("handles single-line content without extra splitting") {
        val event = DatastarEvent.patchElements(
          div("Simple content"),
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data == "elements <div>Simple content</div>\n",
        )
      },
      test("handles script with selector and mode options") {
        val scriptContent = """function init() {
                              |  console.log('initialized');
                              |}""".stripMargin
        val event         = DatastarEvent.patchElements(
          Dom.script(scriptContent),
          Some(selector"#app"),
          ElementPatchMode.Append,
        )

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data.contains("selector #app\n"),
          sse.data.contains("mode append\n"),
          sse.data.contains("elements <script>function init() {\n"),
          sse.data.contains("elements   console.log('initialized');\n"),
          sse.data.contains("elements }</script>\n"),
        )
      },
      test("handles CSS with minification and newlines") {
        val cssContent = """.container {
                           |  display: flex;
                           |  justify-content: center;
                           |}
                           |.item {
                           |  margin: 5px;
                           |}""".stripMargin
        val event      = DatastarEvent.patchElements(
          Dom.style(cssContent),
        )

        val sse = event.toServerSentEvent

        // Each line should be prefixed with "elements "
        val lines = sse.data.split('\n').filter(_.nonEmpty)
        assertTrue(
          lines.forall(_.startsWith("elements ")),
          lines.length >= 5, // Multiple lines from CSS
          sse.data.contains("container"),
          sse.data.contains("display"),
          sse.data.contains("item"),
        )
      },
      test("ExecuteScript should also handle multi-line correctly") {
        val scriptContent = """const x = 1;
                              |const y = 2;
                              |console.log(x + y);""".stripMargin
        val event         = DatastarEvent.executeScript(scriptContent)

        val sse = event.toServerSentEvent

        assertTrue(
          sse.data.contains("selector <body></body>\n"),
          sse.data.contains("mode append\n"),
          sse.data.contains("elements <script data-effect=\"el.remove()\">const x = 1;\n"),
          sse.data.contains("elements const y = 2;\n"),
          sse.data.contains("elements console.log(x + y);</script>\n"),
        )
      },
      test("executeScript escapes </script> in JS content") {
        val event = DatastarEvent.executeScript("var x = '</script><div>xss</div>'; alert(x)")
        val sse   = event.toServerSentEvent
        assertTrue(
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">var x = '<\\/script><div>xss</div>'; alert(x)</script>\n",
        )
      },
    ),
    suite("dispatchEvent")(
      test("basic dispatch with default options") {
        val event = DatastarEvent.dispatchEvent("test-event", CountUpdate(42))
        val sse   = event.toServerSentEvent
        assertTrue(
          sse.eventType.contains("datastar-patch-elements"),
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">document.dispatchEvent(new CustomEvent('test-event',{detail:{\"count\":42},bubbles:true,cancelable:false,composed:false}))</script>\n",
        )
      },
      test("dispatch with custom selector") {
        val event = DatastarEvent.dispatchEvent(
          "my-event",
          CountUpdate(1),
          DispatchEventOptions(source = Some(selector"#my-el")),
        )
        val sse   = event.toServerSentEvent
        assertTrue(
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">(function(){var el=document.querySelector('#my-el');if(el)el.dispatchEvent(new CustomEvent('my-event',{detail:{\"count\":1},bubbles:true,cancelable:false,composed:false}))})()</script>\n",
        )
      },
      test("dispatch with all event options") {
        val event = DatastarEvent.dispatchEvent(
          "custom",
          CountUpdate(5),
          DispatchEventOptions(bubbles = false, cancelable = true, composed = true),
        )
        val sse   = event.toServerSentEvent
        assertTrue(
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">document.dispatchEvent(new CustomEvent('custom',{detail:{\"count\":5},bubbles:false,cancelable:true,composed:true}))</script>\n",
        )
      },
      test("event name escaping") {
        val event = DatastarEvent.dispatchEvent("it's-an-event", CountUpdate(0))
        val sse   = event.toServerSentEvent
        assertTrue(
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">document.dispatchEvent(new CustomEvent('it\\'s-an-event',{detail:{\"count\":0},bubbles:true,cancelable:false,composed:false}))</script>\n",
        )
      },
      test("complex nested payload") {
        val event = DatastarEvent.dispatchEvent("nested", Outer(Inner(1, "hello"), true))
        val sse   = event.toServerSentEvent
        assertTrue(
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">document.dispatchEvent(new CustomEvent('nested',{detail:{\"inner\":{\"x\":1,\"y\":\"hello\"},\"flag\":true},bubbles:true,cancelable:false,composed:false}))</script>\n",
        )
      },
      test("raw Js payload") {
        val event = DatastarEvent.dispatchEvent("raw", Js("myExpression"))
        val sse   = event.toServerSentEvent
        assertTrue(
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">document.dispatchEvent(new CustomEvent('raw',{detail:myExpression,bubbles:true,cancelable:false,composed:false}))</script>\n",
        )
      },
      test("dispatch with source convenience overload") {
        val event = DatastarEvent.dispatchEvent("ev", CountUpdate(7), Some(selector".cls"))
        val sse   = event.toServerSentEvent
        assertTrue(
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">(function(){var el=document.querySelector('.cls');if(el)el.dispatchEvent(new CustomEvent('ev',{detail:{\"count\":7},bubbles:true,cancelable:false,composed:false}))})()</script>\n",
        )
      },
      test("SSE format verification") {
        val event = DatastarEvent.dispatchEvent("fmt-test", CountUpdate(99))
        val sse   = event.toServerSentEvent
        assertTrue(
          sse.eventType.contains("datastar-patch-elements"),
          sse.data == "selector <body></body>\nmode append\nelements <script data-effect=\"el.remove()\">document.dispatchEvent(new CustomEvent('fmt-test',{detail:{\"count\":99},bubbles:true,cancelable:false,composed:false}))</script>\n",
        )
      },
    ),
  )
}
