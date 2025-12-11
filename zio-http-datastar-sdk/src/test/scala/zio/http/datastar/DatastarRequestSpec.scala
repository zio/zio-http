package zio.http.datastar

import scala.annotation.nowarn

import zio.test._

import zio.http._
import zio.http.endpoint._
import zio.http.template2._

@nowarn
object DatastarRequestSpec extends ZIOSpecDefault {

  val testEvent: DatastarEvent                               =
    DatastarEvent.patchSignals(Seq("signal1" -> "value1", "signal2" -> "value2"), onlyIfMissing = false)
  val handler: Handler[Any, Response, Unit, DatastarEvent]   = Handler.succeed(testEvent)
  val wrappedHandler: Handler[Any, Response, Unit, Response] = event(handler)

  override def spec = suite("DatastarRequestSpec")(
    suite("datastarRequest with no parameters")(
      test("should convert endpoint with no path params using value") {
        val endpoint = Endpoint(Method.GET / "api" / "data")
        val request  = endpoint.datastarRequest(())

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/api/data",
        )
      },
      test("should convert endpoint with no path params using signal") {
        val endpoint = Endpoint(Method.POST / "submit")
        val signal   = Signal[Unit]("mySignal")
        val request  = endpoint.datastarRequest(ValueOrSignal.SignalValue(signal))

        assertTrue(
          request.method == Method.POST,
          request.renderUrl == "/submit",
        )
      },
    ),
    suite("datastarRequest with single parameter")(
      test("should convert endpoint with single int path param using value") {
        val endpoint = Endpoint(Method.GET / "users" / int("userId"))
        val request  = endpoint.datastarRequest(42)

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/users/42",
        )
      },
      test("should convert endpoint with single string path param using value") {
        val endpoint = Endpoint(Method.GET / "posts" / string("postId"))
        val request  = endpoint.datastarRequest("abc-123")

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/posts/abc-123",
        )
      },
      test("should convert endpoint with single path param using signal") {
        val endpoint = Endpoint(Method.GET / "items" / int("itemId"))
        val signal   = Signal[Int]("selectedItem")
        val request  = endpoint.datastarRequest(ValueOrSignal.SignalValue(signal))

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/items/$selectedItem",
        )
      },
    ),
    suite("datastarRequest with two parameters")(
      test("should convert endpoint with two int path params using values") {
        val endpoint = Endpoint(Method.GET / "users" / int("userId") / "posts" / int("postId"))
        val request  = endpoint.datastarRequest(
          42,
          100,
        )

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/users/42/posts/100",
        )
      },
      test("should convert endpoint with mixed types using values") {
        val endpoint = Endpoint(Method.GET / "users" / int("userId") / "posts" / string("slug"))
        val request  = endpoint.datastarRequest(
          42,
          "my-post",
        )

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/users/42/posts/my-post",
        )
      },
      test("should convert endpoint with first param as signal") {
        val endpoint   = Endpoint(Method.GET / "users" / int("userId") / "posts" / int("postId"))
        val userSignal = Signal[Int]("currentUser")
        val request    = endpoint.datastarRequest(
          userSignal,
          100,
        )

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/users/$currentUser/posts/100",
        )
      },
      test("should convert endpoint with second param as signal") {
        val endpoint   = Endpoint(Method.GET / "users" / int("userId") / "posts" / int("postId"))
        val postSignal = Signal[Int]("selectedPost")
        val request    = endpoint.datastarRequest(
          42,
          postSignal,
        )

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/users/42/posts/$selectedPost",
        )
      },
      test("should convert endpoint with both params as signals") {
        val endpoint   = Endpoint(Method.GET / "users" / int("userId") / "posts" / int("postId"))
        val userSignal = Signal[Int]("currentUser")
        val postSignal = Signal[Int]("selectedPost")
        val request    = endpoint.datastarRequest(userSignal, postSignal)

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/users/$currentUser/posts/$selectedPost",
        )
      },
    ),
    suite("datastarRequest with three parameters")(
      test("should convert endpoint with three path params using all values") {
        val endpoint =
          Endpoint(Method.GET / "orgs" / string("org") / "repos" / string("repo") / "issues" / int("issueId"))
        val request  = endpoint.datastarRequest(
          "zio",
          "zio-http",
          123,
        )

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/orgs/zio/repos/zio-http/issues/123",
        )
      },
      test("should convert endpoint with mixed values and signals") {
        val endpoint   =
          Endpoint(Method.GET / "orgs" / string("org") / "repos" / string("repo") / "issues" / int("issueId"))
        val repoSignal = Signal[String]("selectedRepo", "")
        val request    = endpoint.datastarRequest("zio", repoSignal, 123)

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/orgs/zio/repos/$selectedRepo/issues/123",
        )
      },
      test("should convert endpoint with all params as signals") {
        val endpoint = Endpoint(Method.GET / "a" / int("x") / "b" / int("y") / "c" / int("z"))
        val xSignal  = Signal[Int]("x")
        val ySignal  = Signal[Int]("y")
        val zSignal  = Signal[Int]("z")
        val request  = endpoint.datastarRequest(xSignal, ySignal, zSignal)

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/a/$x/b/$y/c/$z",
        )
      },
    ),
    suite("datastarRequest with four parameters")(
      test("should convert endpoint with four path params using all values") {
        val endpoint = Endpoint(Method.GET / "a" / int("a") / "b" / int("b") / "c" / int("c") / "d" / int("d"))
        val request  = endpoint.datastarRequest(
          1,
          2,
          3,
          4,
        )

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/a/1/b/2/c/3/d/4",
        )
      },
      test("should convert endpoint with mixed values and signals (4 params)") {
        val endpoint = Endpoint(
          Method.PUT / "level1" / string("l1") / "level2" / string("l2") / "level3" / string("l3") / "level4" / string(
            "l4",
          ),
        )
        val l2Signal = Signal[String]("level2", "")
        val l4Signal = Signal[String]("level4", "")
        val request  = endpoint.datastarRequest("a", l2Signal, "c", l4Signal)

        assertTrue(
          request.method == Method.PUT,
          request.renderUrl == "/level1/a/level2/$level2/level3/c/level4/$level4",
        )
      },
    ),
    suite("datastarRequest with five parameters")(
      test("should convert endpoint with five path params using all values") {
        val endpoint = Endpoint(
          Method.DELETE / "p1" / int("p1") / "p2" / int("p2") / "p3" / int("p3") / "p4" / int("p4") / "p5" / int("p5"),
        )
        val request  = endpoint.datastarRequest(
          1, 2, 3, 4, 5,
        )

        assertTrue(
          request.method == Method.DELETE,
          request.renderUrl == "/p1/1/p2/2/p3/3/p4/4/p5/5",
        )
      },
      test("should convert endpoint with all five params as signals") {
        val endpoint = Endpoint(
          Method.GET / "p1" / int("p1") / "p2" / int("p2") / "p3" / int("p3") / "p4" / int("p4") / "p5" / int("p5"),
        )
        val s1       = Signal[Int]("s1")
        val s2       = Signal[Int]("s2")
        val s3       = Signal[Int]("s3")
        val s4       = Signal[Int]("s4")
        val s5       = Signal[Int]("s5")
        val request  = endpoint.datastarRequest(
          s1,
          s2,
          s3,
          s4,
          s5,
        )

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/p1/$s1/p2/$s2/p3/$s3/p4/$s4/p5/$s5",
        )
      },
    ),
    suite("datastarRequest with six parameters")(
      test("should convert endpoint with six path params using all values") {
        val endpoint = Endpoint(
          Method.GET / "p1" / int("p1") / "p2" / int("p2") / "p3" / int("p3") / "p4" / int("p4") / "p5" / int(
            "p5",
          ) / "p6" / int("p6"),
        )
        val request  = endpoint.datastarRequest(
          1, 2, 3, 4, 5, 6,
        )

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/p1/1/p2/2/p3/3/p4/4/p5/5/p6/6",
        )
      },
      test("should handle alternating values and signals (6 params)") {
        val endpoint = Endpoint(
          Method.GET / "p1" / int("p1") / "p2" / int("p2") / "p3" / int("p3") / "p4" / int("p4") / "p5" / int(
            "p5",
          ) / "p6" / int("p6"),
        )
        val s2       = Signal[Int]("s2")
        val s4       = Signal[Int]("s4")
        val s6       = Signal[Int]("s6")
        val request  = endpoint.datastarRequest(1, s2, 3, s4, 5, s6)
        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/p1/1/p2/$s2/p3/3/p4/$s4/p5/5/p6/$s6",
        )
      },
    ),
    suite("datastarRequest with higher parameter counts")(
      test("should convert endpoint with 10 path params using all values") {
        val endpoint = Endpoint(
          Method.GET /
            "p1" / int("p1") / "p2" / int("p2") / "p3" / int("p3") / "p4" / int("p4") / "p5" / int("p5") /
            "p6" / int("p6") / "p7" / int("p7") / "p8" / int("p8") / "p9" / int("p9") / "p10" / int("p10"),
        )
        val request  = endpoint.datastarRequest(
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
        )

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/p1/1/p2/2/p3/3/p4/4/p5/5/p6/6/p7/7/p8/8/p9/9/p10/10",
        )
      },
      test("should convert endpoint with 16 path params using all values") {
        val endpoint = Endpoint(
          Method.POST /
            "p1" / int("p1") / "p2" / int("p2") / "p3" / int("p3") / "p4" / int("p4") /
            "p5" / int("p5") / "p6" / int("p6") / "p7" / int("p7") / "p8" / int("p8") /
            "p9" / int("p9") / "p10" / int("p10") / "p11" / int("p11") / "p12" / int("p12") /
            "p13" / int("p13") / "p14" / int("p14") / "p15" / int("p15") / "p16" / int("p16"),
        )
        val request  = endpoint.datastarRequest(
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        )

        assertTrue(
          request.method == Method.POST,
          request.renderUrl == "/p1/1/p2/2/p3/3/p4/4/p5/5/p6/6/p7/7/p8/8/p9/9/p10/10/p11/11/p12/12/p13/13/p14/14/p15/15/p16/16",
        )
      },
      test("should handle 16 params with all signals") {
        val endpoint = Endpoint(
          Method.GET /
            "p1" / int("p1") / "p2" / int("p2") / "p3" / int("p3") / "p4" / int("p4") /
            "p5" / int("p5") / "p6" / int("p6") / "p7" / int("p7") / "p8" / int("p8") /
            "p9" / int("p9") / "p10" / int("p10") / "p11" / int("p11") / "p12" / int("p12") /
            "p13" / int("p13") / "p14" / int("p14") / "p15" / int("p15") / "p16" / int("p16"),
        )

        val signals = (1 to 16).map(i => Signal[Int](s"s$i"))

        val request = endpoint.datastarRequest(
          signals(0),
          signals(1),
          signals(2),
          signals(3),
          signals(4),
          signals(5),
          signals(6),
          signals(7),
          signals(8),
          signals(9),
          signals(10),
          signals(11),
          signals(12),
          signals(13),
          signals(14),
          signals(15),
        )

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/p1/$s1/p2/$s2/p3/$s3/p4/$s4/p5/$s5/p6/$s6/p7/$s7/p8/$s8/p9/$s9/p10/$s10/p11/$s11/p12/$s12/p13/$s13/p14/$s14/p15/$s15/p16/$s16",
        )
      },
    ),
    suite("DatastarRequest rendering")(
      test("should render GET request with simple path") {
        val request = DatastarRequest(Method.GET, url"/api/users")

        assertTrue(
          request.method == Method.GET,
          request.renderUrl == "/api/users",
        )
      },
      test("should render POST request with path containing signals") {
        val request = DatastarRequest(Method.POST, URL.decode("/users/$userId/posts/$postId").toOption.get)

        assertTrue(
          request.method == Method.POST,
          request.renderUrl == "/users/$userId/posts/$postId",
        )
      },
      test("should render PUT request") {
        val request = DatastarRequest(Method.PUT, url"/items/42")

        assertTrue(
          request.method == Method.PUT,
          request.renderUrl == "/items/42",
        )
      },
      test("should render DELETE request") {
        val request = DatastarRequest(Method.DELETE, URL(Path("/items/$itemId")))

        assertTrue(
          request.method == Method.DELETE,
          request.renderUrl == "/items/$itemId",
        )
      },
      test("should preserve query parameters in URL") {
        val request = DatastarRequest(Method.GET, url"/api/search?q=test&page=1")

        assertTrue(
          request.renderUrl == "/api/search?q=test&page=1",
        )
      },
      test("should handle complex paths with multiple segments") {
        val request = DatastarRequest(
          Method.GET,
          URL.decode("/orgs/$org/repos/$repo/issues/$issueId/comments/$commentId").toOption.get,
        )

        assertTrue(
          request.renderUrl == "/orgs/$org/repos/$repo/issues/$issueId/comments/$commentId",
        )
      },
    ),
    suite("DatastarRequest with different HTTP methods")(
      test("should support GET method") {
        val endpoint = Endpoint(Method.GET / "data")
        val request  = endpoint.datastarRequest(())

        assertTrue(request.method == Method.GET)
      },
      test("should support POST method") {
        val endpoint = Endpoint(Method.POST / "data")
        val request  = endpoint.datastarRequest(())

        assertTrue(request.method == Method.POST)
      },
      test("should support PUT method") {
        val endpoint = Endpoint(Method.PUT / "data")
        val request  = endpoint.datastarRequest(())

        assertTrue(request.method == Method.PUT)
      },
      test("should support DELETE method") {
        val endpoint = Endpoint(Method.DELETE / "data")
        val request  = endpoint.datastarRequest(())

        assertTrue(request.method == Method.DELETE)
      },
      test("should support PATCH method") {
        val endpoint = Endpoint(Method.PATCH / "data")
        val request  = endpoint.datastarRequest(())

        assertTrue(request.method == Method.PATCH)
      },
    ),
    suite("Edge cases and special scenarios")(
      test("should handle endpoint with long parameter") {
        val endpoint = Endpoint(Method.GET / "items" / long("itemId"))
        val request  = endpoint.datastarRequest(9876543210L)

        assertTrue(request.renderUrl == "/items/9876543210")
      },
      test("should handle endpoint with UUID parameter") {
        val endpoint = Endpoint(Method.GET / "items" / uuid("itemId"))
        val itemId   = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val request  = endpoint.datastarRequest(itemId)

        assertTrue(request.renderUrl == "/items/550e8400-e29b-41d4-a716-446655440000")
      },
      test("should handle string with special characters (URL encoded)") {
        val endpoint = Endpoint(Method.GET / "search" / string("query"))
        val request  = endpoint.datastarRequest("hello world")

        assertTrue(request.renderUrl == "/search/hello%20world")
      },
      test("should properly handle empty path segments") {
        val endpoint = Endpoint(Method.GET / "api" / "v1" / "users")
        val request  = endpoint.datastarRequest(())

        assertTrue(request.renderUrl == "/api/v1/users")
      },
    ),
    suite("render method with default options")(
      test("should render GET request with default options") {
        val request = DatastarRequest(Method.GET, url"/api/users")

        assertTrue(
          request.render == "@get('/api/users')",
        )
      },
      test("should render POST request with default options") {
        val request = DatastarRequest(Method.POST, url"/api/data")

        assertTrue(
          request.render == "@post('/api/data')",
        )
      },
      test("should render PUT request with signal in path") {
        val request = DatastarRequest(Method.PUT, URL(Path("/users/$userId")))

        assertTrue(
          request.render == "@put('/users/$userId')",
        )
      },
      test("should render DELETE request with query params") {
        val request = DatastarRequest(Method.DELETE, url"/items/42?force=true")

        assertTrue(
          request.render == "@delete('/items/42?force=true')",
        )
      },
    ),
    suite("render method with custom options")(
      test("should render request with custom headers") {
        val options = DatastarRequestOptions.default.copy(
          hdrs = Headers(Header.Authorization.Bearer("token123")),
        )
        val request = DatastarRequest(Method.GET, url"/api/users", options)

        println(request.render)

        assertTrue(
          request.render.contains("@get"),
          request.render.contains("/api/users"),
          request.render.contains("headers"),
        )
      },
      test("should render request with custom selector") {
        val options = DatastarRequestOptions(
          selector = Some(id("content")),
        )
        val request = DatastarRequest(Method.POST, url"/api/submit", options)

        assertTrue(
          request.render.contains("@post"),
          request.render.contains("/api/submit"),
          request.render.contains("selector"),
        )
      },
      test("should render request with openWhenHidden enabled") {
        val options = DatastarRequestOptions.default.copy(
          openWhenHidden = true,
        )
        val request = DatastarRequest(Method.GET, url"/api/data", options)

        assertTrue(
          request.render.contains("@get"),
          request.render.contains("/api/data"),
          request.render.contains("openWhenHidden"),
        )
      },
      test("should render request with custom retry settings") {
        val options = DatastarRequestOptions.default.copy(
          retryInterval = 2000,
          retryScaler = 3,
          retryMaxWaitMs = 60000,
          retryMaxCount = 5,
        )
        val request = DatastarRequest(Method.POST, url"/api/upload", options)

        assertTrue(
          request.render.contains("@post"),
          request.render.contains("/api/upload"),
          request.render.contains("retryInterval"),
        )
      },
      test("should render request with signal filter") {
        val options = DatastarRequestOptions.default.copy(
          filterSignals = Some(DatastarSignalFilter(include = "user.*", exclude = Some("user.password"))),
        )
        val request = DatastarRequest(Method.PUT, url"/api/update", options)

        assertTrue(
          request.render.contains("@put"),
          request.render.contains("/api/update"),
          request.render.contains("filterSignals"),
        )
      },
      test("should render request with disabled cancellation") {
        val options = DatastarRequestOptions.default.copy(
          requestCancellation = DatastarRequestCancellation.Disabled,
        )
        val request = DatastarRequest(Method.DELETE, url"/api/delete/123", options)

        assertTrue(
          request.render.contains("@delete"),
          request.render.contains("/api/delete/123"),
          request.render.contains("Disabled"),
        )
      },
      test("should render request with multiple custom options") {
        val options = DatastarRequestOptions.default.copy(
          selector = Some(id("target")),
          hdrs = Headers(Header.ContentType(MediaType.application.json)),
          openWhenHidden = true,
          retryMaxCount = 3,
        )
        val request = DatastarRequest(Method.PATCH, url"/api/partial/update", options)

        assertTrue(
          request.render.contains("@patch"),
          request.render.contains("/api/partial/update"),
          request.render.contains("selector"),
          request.render.contains("headers"),
        )
      },
    ),
    suite("render method with query parameters")(
      test("should render request with query parameters") {
        val request = DatastarRequest(Method.GET, url"/api/search?q=test&page=2")

        assertTrue(
          request.render == "@get('/api/search?q=test&page=2')",
        )
      },
    ),
    suite("render method with special characters and encoding")(
      test("should render request with URL-encoded path") {
        val request = DatastarRequest(Method.GET, url"/api/search/hello%20world")

        assertTrue(
          request.render.contains("/api/search/hello"),
        )
      },
      test("should render request with spaces in query params") {
        val request = DatastarRequest(Method.GET, url"/api/search?q=hello%20world")

        assertTrue(
          request.render.contains("/api/search?q=hello"),
        )
      },
      test("should handle absolute URLs") {
        val request = DatastarRequest(Method.GET, url"https://api.example.com/data")

        assertTrue(
          request.render.contains("https://api.example.com/data"),
        )
      },
      test("should handle absolute URLs with port") {
        val request = DatastarRequest(Method.POST, url"http://localhost:8080/api/submit")

        assertTrue(
          request.render.contains("http://localhost:8080/api/submit"),
        )
      },
    ),
    suite("renderUrl consistency tests")(
      test("renderUrl should handle trailing slash correctly") {
        val request = DatastarRequest(Method.GET, url"/api/users/")

        assertTrue(
          request.renderUrl.endsWith("/"),
        )
      },
      test("renderUrl should preserve signal placeholders") {
        val request = DatastarRequest(Method.GET, URL.decode("/users/$userId/posts/$postId").toOption.get)

        assertTrue(
          request.renderUrl.contains("$userId"),
          request.renderUrl.contains("$postId"),
        )
      },
      test("renderUrl should handle fragment identifiers") {
        val request = DatastarRequest(Method.GET, url"/page#section")

        assertTrue(
          request.renderUrl.contains("#section"),
        )
      },
      test("renderUrl should handle complex paths with signals and values") {
        val request = DatastarRequest(Method.GET, URL.decode("/orgs/zio/repos/$repo/issues/123").toOption.get)

        assertTrue(
          request.renderUrl == "/orgs/zio/repos/$repo/issues/123",
        )
      },
    ),
    suite("event method tests")(
      suite("PatchSignals")(
        test("should encode PatchSignals event with multiple signals") {
          for {
            response <- wrappedHandler(())
            bodyStr  <- response.body.asString
          } yield assertTrue(
            response.status == Status.Ok,
            bodyStr == """{"signal1":"value1","signal2":"value2"}""",
          )
        },
        test("should encode empty PatchSignals event") {
          val emptyEvent: DatastarEvent = DatastarEvent.patchSignals(Iterable.empty, onlyIfMissing = true)
          val emptyHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(emptyEvent)
          val wrappedEmpty: Handler[Any, Response, Unit, Response]      = event(emptyHandler)
          for {
            response <- wrappedEmpty(())
            bodyStr  <- response.body.asString
          } yield assertTrue(
            response.status == Status.Ok,
            bodyStr == "{}",
          )
        },
        test("should encode single signal") {
          val singleEvent: DatastarEvent                                 = DatastarEvent.patchSignals("name" -> "John")
          val singleHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(singleEvent)
          val wrappedSingle: Handler[Any, Response, Unit, Response]      = event(singleHandler)
          for {
            response <- wrappedSingle(())
            bodyStr  <- response.body.asString
          } yield assertTrue(
            response.status == Status.Ok,
            bodyStr == """{"name":"John"}""",
          )
        },
        test("should encode PatchSignals with onlyIfMissing flag") {
          val eventWithFlag: DatastarEvent                             =
            DatastarEvent.patchSignals(Seq("flag" -> "true"), onlyIfMissing = true)
          val flagHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(eventWithFlag)
          val wrappedFlag: Handler[Any, Response, Unit, Response]      = event(flagHandler)
          for {
            response <- wrappedFlag(())
            bodyStr  <- response.body.asString
            hasHeader = response.header[String]("datastar-only-if-missing").isRight
          } yield assertTrue(
            response.status == Status.Ok,
            hasHeader,
            bodyStr == """{"flag":"true"}""",
          )
        },
      ),
      suite("PatchElements")(
        test("should encode PatchElements event with simple HTML") {
          val htmlEvent: DatastarEvent                                 =
            DatastarEvent.patchElements(div("Hello World"))
          val htmlHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(htmlEvent)
          val wrappedHtml: Handler[Any, Response, Unit, Response]      = event(htmlHandler)
          for {
            response <- wrappedHtml(())
            bodyStr  <- response.body.asString
          } yield assertTrue(
            response.status == Status.Ok,
            response.header(Header.ContentType).contains(Header.ContentType(MediaType.text.`html`)),
            bodyStr.contains("Hello World"),
          )
        },
        test("should encode PatchElements with selector") {
          val withSelector: DatastarEvent                                  =
            DatastarEvent.patchElements(
              span(text("Updated")),
              Some(id("target").selector),
            )
          val selectorHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(withSelector)
          val wrappedSelector: Handler[Any, Response, Unit, Response]      = event(selectorHandler)
          for {
            response <- wrappedSelector(())
            bodyStr  <- response.body.asString
          } yield assertTrue(
            response.status == Status.Ok,
            response.header[String]("datastar-selector").isRight,
            bodyStr.contains("Updated"),
          )
        },
        test("should encode PatchElements with Inner mode") {
          val innerMode: DatastarEvent                                  =
            DatastarEvent.patchElements(
              p("Inner content"),
              Some(selector".container"),
              ElementPatchMode.Inner,
            )
          val innerHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(innerMode)
          val wrappedInner: Handler[Any, Response, Unit, Response]      = event(innerHandler)
          for {
            response <- wrappedInner(())
            bodyStr  <- response.body.asString
            hasMode = response.header[String]("datastar-mode").isRight
          } yield assertTrue(
            response.status == Status.Ok,
            hasMode,
            bodyStr.contains("Inner content"),
          )
        },
        test("should encode PatchElements with Append mode") {
          val appendMode: DatastarEvent                                  =
            DatastarEvent.patchElements(
              li("New item"),
              Some(selector"ul"),
              ElementPatchMode.Append,
            )
          val appendHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(appendMode)
          val wrappedAppend: Handler[Any, Response, Unit, Response]      = event(appendHandler)
          for {
            response <- wrappedAppend(())
            bodyStr  <- response.body.asString
            modeValue = response.header[String]("datastar-mode").toOption
          } yield assertTrue(
            response.status == Status.Ok,
            modeValue.contains("append"),
            bodyStr.contains("New item"),
          )
        },
        test("should encode PatchElements with Prepend mode") {
          val prependMode: DatastarEvent                                  =
            DatastarEvent.patchElements(
              li("First item"),
              Some(selector"ul"),
              ElementPatchMode.Prepend,
            )
          val prependHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(prependMode)
          val wrappedPrepend: Handler[Any, Response, Unit, Response]      = event(prependHandler)
          for {
            response <- wrappedPrepend(())
            bodyStr  <- response.body.asString
            modeValue = response.header[String]("datastar-mode").toOption
          } yield assertTrue(
            response.status == Status.Ok,
            modeValue.contains("prepend"),
            bodyStr.contains("First item"),
          )
        },
        test("should encode PatchElements with Replace mode") {
          val replaceMode: DatastarEvent                                  =
            DatastarEvent.patchElements(
              div("Replacement"),
              Some(selector"#old"),
              ElementPatchMode.Replace,
            )
          val replaceHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(replaceMode)
          val wrappedReplace: Handler[Any, Response, Unit, Response]      = event(replaceHandler)
          for {
            response <- wrappedReplace(())
            bodyStr  <- response.body.asString
            modeValue = response.header[String]("datastar-mode").toOption
          } yield assertTrue(
            response.status == Status.Ok,
            modeValue.contains("replace"),
            bodyStr.contains("Replacement"),
          )
        },
        test("should encode PatchElements with useViewTransition") {
          val withTransition: DatastarEvent                                  =
            DatastarEvent.patchElements(
              div("Animated"),
              Some(selector"#animated"),
              ElementPatchMode.Outer,
              useViewTransition = true,
            )
          val transitionHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(withTransition)
          val wrappedTransition: Handler[Any, Response, Unit, Response]      = event(transitionHandler)
          for {
            response <- wrappedTransition(())
            bodyStr  <- response.body.asString
            hasTransition = response.header[String]("datastar-use-view-transition").isRight
          } yield assertTrue(
            response.status == Status.Ok,
            hasTransition,
            bodyStr.contains("Animated"),
          )
        },
        test("should encode PatchElements with complex nested HTML") {
          val complexHtml: DatastarEvent                                  =
            DatastarEvent.patchElements(
              div(
                `class` := "card",
                h2("Title"),
                p("Description"),
                button("Click me"),
              ),
            )
          val complexHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(complexHtml)
          val wrappedComplex: Handler[Any, Response, Unit, Response]      = event(complexHandler)
          for {
            response <- wrappedComplex(())
            bodyStr  <- response.body.asString
          } yield assertTrue(
            response.status == Status.Ok,
            bodyStr.contains("Title"),
            bodyStr.contains("Description"),
            bodyStr.contains("Click me"),
          )
        },
      ),
      suite("ExecuteScript")(
        test("should encode ExecuteScript event with JavaScript string") {
          val scriptEvent: DatastarEvent                                 =
            DatastarEvent.executeScript("console.log('Hello from Datastar')")
          val scriptHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(scriptEvent)
          val wrappedScript: Handler[Any, Response, Unit, Response]      = event(scriptHandler)
          for {
            response <- wrappedScript(())
            bodyStr  <- response.body.asString
          } yield assertTrue(
            response.status == Status.Ok,
            response.header(Header.ContentType).contains(Header.ContentType(MediaType.text.`javascript`)),
            bodyStr.contains("console.log"),
          )
        },
        test("should encode ExecuteScript with autoRemove enabled") {
          val autoRemoveScript: DatastarEvent                                =
            DatastarEvent.executeScript("alert('Temporary alert')", autoRemove = true)
          val autoRemoveHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(autoRemoveScript)
          val wrappedAutoRemove: Handler[Any, Response, Unit, Response]      = event(autoRemoveHandler)
          for {
            response <- wrappedAutoRemove(())
            hasAttrs = response.header[String]("datastar-script-attributes").isRight
          } yield assertTrue(
            response.status == Status.Ok,
            hasAttrs,
          )
        },
        test("should encode ExecuteScript with Js type") {
          val jsScript: DatastarEvent                                =
            DatastarEvent.executeScript(Js("document.getElementById('test').textContent = 'Updated'"))
          val jsHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(jsScript)
          val wrappedJs: Handler[Any, Response, Unit, Response]      = event(jsHandler)
          for {
            response <- wrappedJs(())
            bodyStr  <- response.body.asString
          } yield assertTrue(
            response.status == Status.Ok,
            bodyStr.contains("getElementById"),
          )
        },
        test("should encode ExecuteScript with multiline script") {
          val multilineScript: DatastarEvent                                =
            DatastarEvent.executeScript(
              """const x = 10;
                |const y = 20;
                |console.log(x + y);""".stripMargin,
            )
          val multilineHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(multilineScript)
          val wrappedMultiline: Handler[Any, Response, Unit, Response]      = event(multilineHandler)
          for {
            response <- wrappedMultiline(())
            bodyStr  <- response.body.asString
          } yield assertTrue(
            response.status == Status.Ok,
            bodyStr.contains("const x"),
            bodyStr.contains("const y"),
          )
        },
        test("should encode ExecuteScript with function definition") {
          val functionScript: DatastarEvent                                =
            DatastarEvent.executeScript(
              """function greet(name) {
                |  return 'Hello, ' + name;
                |}
                |greet('World');""".stripMargin,
            )
          val functionHandler: Handler[Any, Response, Unit, DatastarEvent] = Handler.succeed(functionScript)
          val wrappedFunction: Handler[Any, Response, Unit, Response]      = event(functionHandler)
          for {
            response <- wrappedFunction(())
            bodyStr  <- response.body.asString
          } yield assertTrue(
            response.status == Status.Ok,
            bodyStr.contains("function greet"),
          )
        },
      ),
    ),
  )
}
