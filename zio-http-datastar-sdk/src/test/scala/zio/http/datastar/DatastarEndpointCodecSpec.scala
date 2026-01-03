package zio.http.datastar

import zio.test._

import zio.http._
import zio.http.endpoint._
import zio.http.template2._

/**
 * Tests for the datastarEventCodec to ensure proper encoding/decoding of
 * DatastarEvent responses, especially for ExecuteScript events where JavaScript
 * should not be wrapped in double quotes.
 */
object DatastarEndpointCodecSpec extends ZIOSpecDefault {

  override def spec = suite("DatastarEndpointCodecSpec")(
    suite("executeScriptCodec")(
      test("should encode single-line JavaScript without wrapping in quotes") {
        val scriptContent = "console.log('Hello, World!');"
        val event         = DatastarEvent.executeScript(scriptContent)
        val endpoint      = Endpoint.datastarEvent(Method.GET / "script")
        val routes        = Routes(endpoint.implementHandler(Handler.succeed(event)))
        val request       = Request.get("/script")

        for {
          response <- routes.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          response.headers.get(Header.ContentType).contains(Header.ContentType(MediaType.text.`javascript`)),
          body == scriptContent,
          !body.startsWith("\""),
          !body.endsWith("\""),
        )
      },
      test("should encode multi-line JavaScript without wrapping in quotes") {
        val scriptContent = """const x = 1;
                              |const y = 2;
                              |console.log(x + y);""".stripMargin
        val event         = DatastarEvent.executeScript(scriptContent)
        val endpoint      = Endpoint.datastarEvent(Method.GET / "script")
        val routes        = Routes(endpoint.implementHandler(Handler.succeed(event)))
        val request       = Request.get("/script")

        for {
          response <- routes.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          response.headers.get(Header.ContentType).contains(Header.ContentType(MediaType.text.`javascript`)),
          body == scriptContent,
          !body.startsWith("\""),
          !body.endsWith("\""),
          body.contains("const x = 1;"),
          body.contains("const y = 2;"),
          body.contains("console.log(x + y);"),
        )
      },
      test("should encode JavaScript with quotes without double-encoding") {
        val scriptContent = """alert("Hello 'World'!");"""
        val event         = DatastarEvent.executeScript(scriptContent)
        val endpoint      = Endpoint.datastarEvent(Method.GET / "script")
        val routes        = Routes(endpoint.implementHandler(Handler.succeed(event)))
        val request       = Request.get("/script")

        for {
          response <- routes.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          body == scriptContent,
          body.contains("""alert("Hello 'World'!");"""),
          // Verify it's not JSON-encoded (which would escape the quotes)
          !body.contains("""\"Hello"""),
        )
      },
      test("should encode JavaScript with special characters") {
        val scriptContent = """const message = "Line 1\nLine 2\tTabbed";
                              |console.log(message);""".stripMargin
        val event         = DatastarEvent.executeScript(scriptContent)
        val endpoint      = Endpoint.datastarEvent(Method.GET / "script")
        val routes        = Routes(endpoint.implementHandler(Handler.succeed(event)))
        val request       = Request.get("/script")

        for {
          response <- routes.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          body == scriptContent,
          body.contains("Line 1\\nLine 2\\tTabbed"),
        )
      },
      test("should encode JavaScript function without quotes") {
        val scriptContent = "function greet(name) {\n  return 'Hello, ' + name + '!';\n}\nconsole.log(greet('World'));"
        val event         = DatastarEvent.executeScript(scriptContent)
        val endpoint      = Endpoint.datastarEvent(Method.GET / "script")
        val routes        = Routes(endpoint.implementHandler(Handler.succeed(event)))
        val request       = Request.get("/script")

        for {
          response <- routes.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          body == scriptContent,
          body.contains("function greet(name)"),
          body.contains("Hello,"),
        )
      },
      test("should encode empty JavaScript") {
        val scriptContent = ""
        val event         = DatastarEvent.executeScript(scriptContent)
        val endpoint      = Endpoint.datastarEvent(Method.GET / "script")
        val routes        = Routes(endpoint.implementHandler(Handler.succeed(event)))
        val request       = Request.get("/script")

        for {
          response <- routes.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          body == scriptContent,
          body.isEmpty,
        )
      },
      test("should preserve whitespace in JavaScript") {
        val scriptContent = """  const x = 1;
                              |    const y = 2;
                              |  console.log(x + y);""".stripMargin
        val event         = DatastarEvent.executeScript(scriptContent)
        val endpoint      = Endpoint.datastarEvent(Method.GET / "script")
        val routes        = Routes(endpoint.implementHandler(Handler.succeed(event)))
        val request       = Request.get("/script")

        for {
          response <- routes.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          body == scriptContent,
          body.startsWith("  const x = 1;"),
        )
      },
    ),
    suite("patchSignalsCodec")(
      test("should encode signals as JSON string") {
        val event    = DatastarEvent.patchSignals(Seq("user" -> "John", "count" -> "42"))
        val endpoint = Endpoint.datastarEvent(Method.GET / "signals")
        val routes   = Routes(endpoint.implementHandler(Handler.succeed(event)))
        val request  = Request.get("/signals")

        for {
          response <- routes.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          response.headers.get(Header.ContentType).contains(Header.ContentType(MediaType.application.`json`)),
          body.contains("user"),
          body.contains("John"),
          body.contains("count"),
          body.contains("42"),
        )
      },
    ),
    suite("patchElementsCodec")(
      test("should encode HTML elements") {
        val element  = div(id := "test")("Hello")
        val event    = DatastarEvent.patchElements(element)
        val endpoint = Endpoint.datastarEvent(Method.GET / "elements")
        val routes   = Routes(endpoint.implementHandler(Handler.succeed(event)))
        val request  = Request.get("/elements")

        for {
          response <- routes.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          response.headers.get(Header.ContentType).contains(Header.ContentType(MediaType.text.`html`)),
          body.contains("Hello"),
        )
      },
      test("should encode multi-line HTML elements") {
        val element  = div(
          script("""console.log('line 1');
                   |console.log('line 2');""".stripMargin),
        )
        val event    = DatastarEvent.patchElements(element)
        val endpoint = Endpoint.datastarEvent(Method.GET / "elements")
        val routes   = Routes(endpoint.implementHandler(Handler.succeed(event)))
        val request  = Request.get("/elements")

        for {
          response <- routes.runZIO(request)
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          body.contains("console.log('line 1');"),
          body.contains("console.log('line 2');"),
        )
      },
    ),
    suite("datastarEventCodec alternatives")(
      test("should route ExecuteScript to javascript content type") {
        val scriptEvent = DatastarEvent.executeScript("console.log('test');")
        val endpoint    = Endpoint.datastarEvent(Method.GET / "event")
        val routes      = Routes(endpoint.implementHandler(Handler.succeed(scriptEvent)))
        val request     = Request.get("/event")

        for {
          response <- routes.runZIO(request)
          _        <- response.body.asString
        } yield assertTrue(
          response.headers.get(Header.ContentType).contains(Header.ContentType(MediaType.text.`javascript`)),
        )
      },
      test("should route PatchSignals to json content type") {
        val signalEvent = DatastarEvent.patchSignals("test" -> "true")
        val endpoint    = Endpoint.datastarEvent(Method.GET / "event")
        val routes      = Routes(endpoint.implementHandler(Handler.succeed(signalEvent)))
        val request     = Request.get("/event")

        for {
          response <- routes.runZIO(request)
          _        <- response.body.asString
        } yield assertTrue(
          response.headers.get(Header.ContentType).contains(Header.ContentType(MediaType.application.`json`)),
        )
      },
      test("should route PatchElements to html content type") {
        val elementEvent = DatastarEvent.patchElements(div("test"))
        val endpoint     = Endpoint.datastarEvent(Method.GET / "event")
        val routes       = Routes(endpoint.implementHandler(Handler.succeed(elementEvent)))
        val request      = Request.get("/event")

        for {
          response <- routes.runZIO(request)
          _        <- response.body.asString
        } yield assertTrue(
          response.headers.get(Header.ContentType).contains(Header.ContentType(MediaType.text.`html`)),
        )
      },
    ),
  )

}
