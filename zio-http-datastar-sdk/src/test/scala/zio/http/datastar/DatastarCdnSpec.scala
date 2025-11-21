package zio.http.datastar

import zio.test._

import zio.http.template2._

object DatastarCdnSpec extends ZIOSpecDefault {

  override def spec: Spec[Any, Any] = suite("DatastarCdnSpec")(
    suite("datastarScript")(
      test("renders script tag with default version") {
        val script   = datastarScript
        val rendered = script.render
        assertTrue(
          rendered.contains("<script"),
          rendered.contains("type=\"module\""),
          rendered.contains(
            "src=\"https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.6/bundles/datastar.js\"",
          ),
          rendered.contains("</script>"),
        )
      },
      test("renders script tag with custom version") {
        val script   = datastarScript("1.0.0-RC.7")
        val rendered = script.render
        assertTrue(
          rendered.contains("<script"),
          rendered.contains("type=\"module\""),
          rendered.contains(
            "src=\"https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.7/bundles/datastar.js\"",
          ),
          rendered.contains("</script>"),
        )
      },
      test("script element is of type Dom.Element.Script") {
        val script = datastarScript
        assertTrue(script.isInstanceOf[Dom.Element.Script])
      },
    ),
    suite("mainPage")(
      test("renders complete HTML page with defaults") {
        val page     = mainPage(
          headContent = Seq(
            title("Test Page"),
          ),
          bodyContent = Seq(
            div(id := "app", "Hello, Datastar!"),
          ),
        )
        val rendered = page.render
        assertTrue(
          rendered.contains("<html>"),
          !rendered.contains("lang="), // no language by default
          rendered.contains("<head>"),
          rendered.contains("<title>Test Page</title>"),
          rendered.contains("js"),
          rendered.contains("</head>"),
          rendered.contains("<body>"),
          rendered.contains("<div id=\"app\">Hello, Datastar!</div>"),
          rendered.contains("</body>"),
          rendered.contains("</html>"),
        )
      },
      test("renders complete HTML page with custom language") {
        val page     = mainPage(
          headContent = Seq(title("Test Page")),
          bodyContent = Seq(div("Content")),
          language = Some("fr"),
        )
        val rendered = page.render
        assertTrue(
          rendered.contains("lang=\"fr\""),
          rendered.contains("<html"),
          rendered.contains("</html>"),
        )
      },
      test("renders complete HTML page without language when None") {
        val page     = mainPage(
          headContent = Seq(title("Test Page")),
          bodyContent = Seq(div("Content")),
          language = None,
        )
        val rendered = page.render
        assertTrue(
          !rendered.contains("lang="),
          rendered.contains("<html>"),
          rendered.contains("</html>"),
        )
      },
      test("renders complete HTML page with custom datastar version") {
        val page     = mainPage(
          headContent = Seq(title("Test Page")),
          bodyContent = Seq(div("Content")),
          datastarVersion = "1.0.0-RC.8",
          language = Some("de"),
        )
        val rendered = page.render
        assertTrue(
          rendered.contains("lang=\"de\""),
          rendered.contains("datastar@1.0.0-RC.8"),
          rendered.contains("<html"),
          rendered.contains("</html>"),
        )
      },
      test("includes datastar script in head before additional head content") {
        val page        = mainPage(
          headContent = Seq(
            title("Test Page"),
            script.inlineJs("console.log('test')"),
          ),
          bodyContent = Seq(div("Content")),
        )
        val rendered    = page.render
        val datastarIdx = rendered.indexOf("js")
        val customIdx   = rendered.indexOf("console.log('test')")
        assertTrue(
          datastarIdx > 0,
          customIdx > 0,
          datastarIdx < customIdx,
        )
      },
      test("handles empty head content") {
        val page     = mainPage(
          headContent = Seq.empty,
          bodyContent = Seq(div("Content")),
        )
        val rendered = page.render
        assertTrue(
          rendered.contains("<head>"),
          rendered.contains("js"),
          rendered.contains("</head>"),
        )
      },
      test("handles empty body content") {
        val page     = mainPage(
          headContent = Seq(title("Test")),
          bodyContent = Seq.empty,
        )
        val rendered = page.render
        assertTrue(
          rendered.contains("<body>"),
          rendered.contains("</body>"),
        )
      },
      test("renders page with multiple sections") {
        val page     = mainPage(
          headContent = Seq(
            title("Multi-Section Page"),
          ),
          bodyContent = Seq(
            header("Header"),
            div("Content"),
            footer("Footer"),
          ),
          language = Some("en"),
        )
        val rendered = page.render
        assertTrue(
          rendered.contains("<html"),
          rendered.contains("lang=\"en\""),
          rendered.contains("<header>Header</header>"),
          rendered.contains("<footer>Footer</footer>"),
          rendered.contains("js"),
        )
      },
    ),
    suite("integration with datastar attributes")(
      test("mainPage works with datastar attributes") {
        val count          = Signal[Int]("count")
        val page           = mainPage(
          headContent = Seq(title("Counter")),
          bodyContent = Seq(
            div(
              dataSignals := (count := 0).toExpression,
              button(dataOn.click := js"count++")(
                dataText := count,
              ),
            ),
          ),
        )
        val rendered       = page.render
        val renderedSignal = rendered.contains(s"$$count")
        assertTrue(
          rendered.contains("data-signals"),
          rendered.contains("data-on:click"),
          rendered.contains("data-text"),
          renderedSignal,
          rendered.contains("js"),
        )
      },
    ),
  )
}
