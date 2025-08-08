package zio.http.datastar

import zio._
import zio.test._

import zio.http.ServerSentEvent
import zio.http.template2._

object ServerSentEventGeneratorSpec extends ZIOSpecDefault {

  override def spec = suite("ServerSentEventGeneratorSpec")(
    suite("EventType")(
      test("PatchElements should have correct toString") {
        assertTrue(EventType.PatchElements.toString == "datastar-patch-elements")
      },
      test("PatchSignals should have correct toString") {
        assertTrue(EventType.PatchSignals.toString == "datastar-patch-signals")
      },
    ),
    suite("ElementPatchMode")(
      test("should have correct toString for all modes") {
        assertTrue(
          ElementPatchMode.Outer.toString == "outer",
          ElementPatchMode.Inner.toString == "inner",
          ElementPatchMode.Replace.toString == "replace",
          ElementPatchMode.Prepend.toString == "prepend",
          ElementPatchMode.Append.toString == "append",
          ElementPatchMode.Before.toString == "before",
          ElementPatchMode.After.toString == "after",
          ElementPatchMode.Remove.toString == "remove",
        )
      },
    ),
    suite("patchElements")(
      test("should send minimal patch elements event with string") {
        for {
          datastar <- Datastar.make
          queue = datastar.queue
          _     <- ServerSentEventGenerator
            .patchElements("<div id=\"feed\"><span>1</span></div>")
            .provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data == "elements <div id=\"feed\"><span>1</span></div>\n",
          event.id.isEmpty,
          event.retry.isEmpty,
        )
      },
      test("should send patch elements event with Dom") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          element = div(id := "feed")(span("1"))
          _     <- ServerSentEventGenerator.patchElements(element).provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data == "elements <div id=\"feed\"><span>1</span></div>\n",
          event.id.isEmpty,
          event.retry.isEmpty,
        )
      },
      test("should send patch elements event with multiple Dom elements") {
        for {
          datastar <- Datastar.make
          queue    = datastar.queue
          elements = Seq(
            div(id := "feed1")(span("1")),
            div(id := "feed2")(span("2")),
          )
          _ <- ServerSentEventGenerator.patchElements(elements).provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data == "elements <div id=\"feed1\"><span>1</span></div>\nelements <div id=\"feed2\"><span>2</span></div>\n",
          event.id.isEmpty,
          event.retry.isEmpty,
        )
      },
      test("should include selector when provided") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchElementOptions(selector = Some(selector"#mycontainer"))
          _     <- ServerSentEventGenerator
            .patchElements("<div>New content</div>", options)
            .provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data == "selector #mycontainer\nelements <div>New content</div>\n",
        )
      },
      test("should include mode when not default (outer)") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchElementOptions(mode = ElementPatchMode.Inner)
          _     <- ServerSentEventGenerator.patchElements("<span>1</span>", options).provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data == "mode inner\nelements <span>1</span>\n",
        )
      },
      test("should not include mode when default (outer)") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchElementOptions(mode = ElementPatchMode.Outer)
          _     <- ServerSentEventGenerator.patchElements("<span>1</span>", options).provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data == "elements <span>1</span>\n",
        )
      },
      test("should include useViewTransition when true") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchElementOptions(useViewTransition = true)
          _ <- ServerSentEventGenerator.patchElements("<div>content</div>", options).provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data == "useViewTransition true\nelements <div>content</div>\n",
        )
      },
      test("should include all options when provided") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchElementOptions(
            selector = Some(selector"#feed"),
            mode = ElementPatchMode.Inner,
            useViewTransition = true,
            eventId = Some("123"),
            retryDuration = 2000.millis,
          )
          _     <- ServerSentEventGenerator
            .patchElements("<div><span>1</span></div>", options)
            .provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data == "selector #feed\nmode inner\nuseViewTransition true\nelements <div><span>1</span></div>\n",
          event.id.contains("123"),
          event.retry.contains(2000.millis),
        )
      },
      test("should handle append mode with selector") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchElementOptions(
            selector = Some(selector"#mycontainer"),
            mode = ElementPatchMode.Append,
          )
          _     <- ServerSentEventGenerator
            .patchElements("<div>New content</div>", options)
            .provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data == "selector #mycontainer\nmode append\nelements <div>New content</div>\n",
        )
      },
      test("should handle append mode with css selector API") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchElementOptions(
            selector = Some(id("mycontainer")),
            mode = ElementPatchMode.Append,
          )
          _     <- ServerSentEventGenerator
            .patchElements("<div>New content</div>", options)
            .provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data == "selector #mycontainer\nmode append\nelements <div>New content</div>\n",
        )
      },
      test("should handle remove mode with selector and no elements") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchElementOptions(
            selector = Some(selector"#feed, #otherid"),
            mode = ElementPatchMode.Remove,
          )
          _     <- ServerSentEventGenerator.patchElements("", options).provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data == "selector #feed, #otherid\nmode remove\nelements \n",
        )
      },
      test("should handle remove mode with selector API and no elements") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchElementOptions(
            selector = Some(id("feed") | id("otherid")),
            mode = ElementPatchMode.Remove,
          )
          _     <- ServerSentEventGenerator.patchElements("", options).provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data == "selector #feed, #otherid\nmode remove\nelements \n",
        )
      },
      test("should handle remove mode without selector") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchElementOptions(mode = ElementPatchMode.Remove)
          _     <- ServerSentEventGenerator
            .patchElements("<div id=\"first\"></div><div id=\"second\"></div>", options)
            .provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data == "mode remove\nelements <div id=\"first\"></div><div id=\"second\"></div>\n",
        )
      },
    ),
    suite("patchSignals")(
      test("should send minimal patch signals event") {
        for {
          datastar <- Datastar.make
          queue = datastar.queue
          _     <- ServerSentEventGenerator
            .patchSignals("""{"output":"Patched Output Test","show":true}""")
            .provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-signals"),
          event.data == """signals {"output":"Patched Output Test","show":true}""" + "\n",
          event.id.isEmpty,
          event.retry.isEmpty,
        )
      },
      test("should send patch signals event with multiple signals") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          signals = Seq(
            """{"output":"Test1"}""",
            """{"show":true}""",
          )
          _     <- ServerSentEventGenerator.patchSignals(signals).provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-signals"),
          event.data == """signals {"output":"Test1"}""" + "\n" + """signals {"show":true}""" + "\n",
        )
      },
      test("should include onlyIfMissing when true") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchSignalOptions(onlyIfMissing = true)
          _     <- ServerSentEventGenerator
            .patchSignals("""{"newSignal":"value"}""", options)
            .provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-signals"),
          event.data == """onlyIfMissing true""" + "\n" + """signals {"newSignal":"value"}""" + "\n",
        )
      },
      test("should not include onlyIfMissing when false") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchSignalOptions(onlyIfMissing = false)
          _     <- ServerSentEventGenerator
            .patchSignals("""{"signal":"value"}""", options)
            .provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-signals"),
          event.data == """signals {"signal":"value"}""" + "\n",
        )
      },
      test("should include all options when provided") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchSignalOptions(
            onlyIfMissing = true,
            eventId = Some("123"),
            retryDuration = 2000.millis,
          )
          _     <- ServerSentEventGenerator
            .patchSignals("""{"user":{"name":"Johnny"}}""", options)
            .provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-signals"),
          event.data == """onlyIfMissing true""" + "\n" + """signals {"user":{"name":"Johnny"}}""" + "\n",
          event.id.contains("123"),
          event.retry.contains(2000.millis),
        )
      },
    ),
    suite("executeScript")(
      test("should send minimal execute script event") {
        for {
          datastar <- Datastar.make
          queue = datastar.queue
          _     <- ServerSentEventGenerator.executeScript("console.log('Here')").provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data.contains("elements <script"),
          event.data.contains("console.log('Here')"),
          event.data.contains("data-effect=\"el.remove\""),
          event.id.isEmpty,
          event.retry.isEmpty,
        )
      },
      test("should not include data-effect when autoRemove is false") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = ExecuteScriptOptions(autoRemove = false)
          _ <- ServerSentEventGenerator.executeScript("console.log('Here')", options).provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data.contains("elements <script"),
          event.data.contains("console.log('Here')"),
          !event.data.contains("data-effect"),
        )
      },
      test("should include custom attributes") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = ExecuteScriptOptions(
            attributes = Seq("type" -> "application/javascript", "defer" -> "true"),
          )
          _ <- ServerSentEventGenerator.executeScript("console.log('Here')", options).provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data.contains("type=\"application/javascript\""),
          event.data.contains("defer=\"true\""),
          event.data.contains("console.log('Here')"),
        )
      },
      test("should include all options when provided") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = ExecuteScriptOptions(
            autoRemove = false,
            attributes = Seq("type" -> "application/javascript"),
            eventId = Some("123"),
            retryDuration = 2000.millis,
          )
          _ <- ServerSentEventGenerator.executeScript("alert('test')", options).provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.eventType.contains("datastar-patch-elements"),
          event.data.contains("type=\"application/javascript\""),
          event.data.contains("alert('test')"),
          !event.data.contains("data-effect"),
          event.id.contains("123"),
          event.retry.contains(2000.millis),
        )
      },
    ),
    suite("retry duration handling")(
      test("should not include retry when default (1000ms)") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchElementOptions(retryDuration = 1000.millis)
          _     <- ServerSentEventGenerator.patchElements("<div>test</div>", options).provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.retry.isEmpty,
        )
      },
      test("should include retry when non-default") {
        for {
          datastar <- Datastar.make
          queue   = datastar.queue
          options = PatchElementOptions(retryDuration = 5000.millis)
          _     <- ServerSentEventGenerator.patchElements("<div>test</div>", options).provide(ZLayer.succeed(datastar))
          event <- queue.take
        } yield assertTrue(
          event.retry.contains(5000.millis),
        )
      },
    ),
  )

}
