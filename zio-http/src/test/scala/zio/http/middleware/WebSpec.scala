package zio.http.middleware

import zio._
import zio.http.Middleware._
import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio.http.model._
import zio.test.Assertion._
import zio.test._

object WebSpec extends ZIOSpecDefault with HttpAppTestExtensions { self =>
  private val app  = Route.collectZIO[Request] { case Method.GET -> !! / "health" =>
    ZIO.succeed(Response.ok).delay(1 second)
  }
  private val midA = Middleware.addHeader("X-Custom", "A")
  private val midB = Middleware.addHeader("X-Custom", "B")

  def spec = suite("HttpMiddleware")(
    suite("headers suite")(
      test("addHeaders") {
        val middleware = addHeaders(Headers("KeyA", "ValueA") ++ Headers("KeyB", "ValueB"))
        val headers    = (Handler.ok @@ middleware).toRoute.headerValues
        assertZIO(headers.runZIO(Request.get(URL.empty)))(contains("ValueA") && contains("ValueB"))
      },
      test("addHeader") {
        val middleware = addHeader("KeyA", "ValueA")
        val headers    = (Handler.ok @@ middleware).toRoute.headerValues
        assertZIO(headers.runZIO(Request.get(URL.empty)))(contains("ValueA"))
      },
      test("updateHeaders") {
        val middleware = updateHeaders(_ => Headers("KeyA", "ValueA"))
        val headers    = (Handler.ok @@ middleware).toRoute.headerValues
        assertZIO(headers.runZIO(Request.get(URL.empty)))(contains("ValueA"))
      },
      test("removeHeader") {
        val middleware = removeHeader("KeyA")
        val headers    =
          (Handler.succeed(Response.ok.setHeaders(Headers("KeyA", "ValueA"))) @@ middleware).toRoute header "KeyA"
        assertZIO(headers.runZIO(Request.get(URL.empty)))(isNone)
      },
    ),
    suite("debug")(
      test("log status method url and time") {
        val program = runApp(app @@ debug) *> TestConsole.output
        assertZIO(program)(equalTo(Vector("200 GET /health 1000ms\n")))
      },
      test("log 404 status method url and time") {
        val program = runApp(Route.empty ++ (Handler.notFound @@ debug).toRoute) *> TestConsole.output
        assertZIO(program)(equalTo(Vector("404 GET /health 0ms\n")))
      },
    ),
    suite("when")(
      test("condition is true") {
        val program = runApp(self.app @@ debug.when((_: Any) => true)) *> TestConsole.output
        assertZIO(program)(equalTo(Vector("200 GET /health 1000ms\n")))
      } +
        test("condition is false") {
          val log = runApp(self.app @@ debug.when((_: Any) => false)) *> TestConsole.output
          assertZIO(log)(equalTo(Vector()))
        },
    ),
    suite("whenZIO")(
      test("condition is true") {
        val program =
          runApp(self.app @@ debug.whenZIO((_: Request) => ZIO.succeed(true))) *> TestConsole.output
        assertZIO(program)(equalTo(Vector("200 GET /health 1000ms\n")))
      },
      test("condition is false") {
        val log =
          runApp(self.app @@ debug.whenZIO((_: Request) => ZIO.succeed(false))) *> TestConsole.output
        assertZIO(log)(equalTo(Vector()))
      },
    ),
    suite("race")(
      test("achieved") {
        val program = runApp(self.app @@ timeout(5 seconds)).map(_.status)
        assertZIO(program)(equalTo(Status.Ok))
      },
      test("un-achieved") {
        val program = runApp(self.app @@ timeout(500 millis)).map(_.status)
        assertZIO(program)(equalTo(Status.RequestTimeout))
      },
    ),
    suite("combine")(
      test("before and after") {
        val middleware = runBefore(Console.printLine("A"))
        val program    = runApp(self.app @@ middleware) *> TestConsole.output
        assertZIO(program)(equalTo(Vector("A\n")))
      },
      test("add headers twice") {
        val middleware = addHeader("KeyA", "ValueA") ++ addHeader("KeyB", "ValueB")
        val headers    = (Handler.ok @@ middleware).headerValues
        assertZIO(headers.runZIO(Request.get(URL.empty)))(contains("ValueA") && contains("ValueB"))
      },
      test("add and remove header") {
        val middleware = addHeader("KeyA", "ValueA") ++ removeHeader("KeyA")
        val program    = (Handler.ok @@ middleware) header "KeyA"
        assertZIO(program.runZIO(Request.get(URL.empty)))(isNone)
      },
    ),
    suite("ifRequestThenElseZIO")(
      test("if the condition is true take first") {
        val app = (Handler.ok @@ ifRequestThenElseZIO(condZIO(true))(midA, midB)) header "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("A")))
      },
      test("if the condition is false take 2nd") {
        val app =
          (Handler.ok @@ ifRequestThenElseZIO(condZIO(false))(midA, midB)) header "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("B")))
      },
    ),
    suite("ifRequestThenElse")(
      test("if the condition is true take first") {
        val app = Handler.ok @@ ifRequestThenElse(cond(true))(midA, midB) header "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("A")))
      },
      test("if the condition is false take 2nd") {
        val app = Handler.ok @@ ifRequestThenElse(cond(false))(midA, midB) header "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("B")))
      },
    ),
    suite("whenStatus")(
      test("if the condition is true apply middleware") {
        val app = Handler.ok @@ Middleware.whenStatus(_ == Status.Ok)(midA) header "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply the middleware") {
        val app = Handler.ok @@ Middleware.whenStatus(_ == Status.NoContent)(midA) header "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isNone)
      },
    ),
    suite("whenRequestZIO")(
      test("if the condition is true apply middleware") {
        val app = (Handler.ok @@ Middleware.whenRequestZIO(condZIO(true))(midA)) header "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply any middleware") {
        val app = (Handler.ok @@ Middleware.whenRequestZIO(condZIO(false))(midA)) header "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isNone)
      },
    ),
    suite("whenRequest")(
      test("if the condition is true apply middleware") {
        val app = Handler.ok @@ Middleware.whenRequest(cond(true))(midA) header "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply the middleware") {
        val app = Handler.ok @@ Middleware.whenRequest(cond(false))(midA) header "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isNone)
      },
    ),
    suite("whenResponseZIO")(
      test("if the condition is true apply middleware") {
        val app = (Handler.ok @@ Middleware.whenResponseZIO(condZIO(true))(midA)) header "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply any middleware") {
        val app = (Handler.ok @@ Middleware.whenResponseZIO(condZIO(false))(midA)) header "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isNone)
      },
    ),
    suite("whenResponse")(
      test("if the condition is true apply middleware") {
        val app = Handler.ok @@ Middleware.whenResponse(cond(true))(midA) header "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply the middleware") {
        val app = Handler.ok @@ Middleware.whenResponse(cond(false))(midA) header "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isNone)
      },
    ),
    suite("cookie")(
      test("addCookie") {
        val cookie = Cookie("test", "testValue")
        val app    = (Handler.ok @@ addCookie(cookie)).header("set-cookie")
        assertZIO(app.runZIO(Request.get(URL.empty)))(
          equalTo(cookie.encode.toOption),
        )
      },
      test("addCookieM") {
        val cookie = Cookie("test", "testValue")
        val app    =
          (Handler.ok @@ addCookieZIO(ZIO.succeed(cookie))).header("set-cookie")
        assertZIO(app.runZIO(Request.get(URL.empty)))(
          equalTo(cookie.encode.toOption),
        )
      },
    ),
    suite("signCookies")(
      test("should sign cookies") {
        val cookie = Cookie("key", "value").withHttpOnly(true)
        val app    = Handler.ok.withSetCookie(cookie) @@ signCookies("secret") header "set-cookie"
        assertZIO(app.runZIO(Request.get(URL.empty)))(equalTo(cookie.sign("secret").encode.toOption))
      },
      test("sign cookies no cookie header") {
        val app = (Handler.ok.addHeader("keyA", "ValueA") @@ signCookies("secret")).headerValues
        assertZIO(app.runZIO(Request.get(URL.empty)))(contains("ValueA"))
      },
    ),
    suite("trailingSlashDrop")(
      test("should drop trailing slash") {
        val urls = Gen.fromIterable(
          Seq(
            ""        -> "",
            "/"       -> "",
            "/a"      -> "/a",
            "/a/b"    -> "/a/b",
            "/a/b/"   -> "/a/b",
            "/a/"     -> "/a",
            "/a/?a=1" -> "/a/?a=1",
            "/a?a=1"  -> "/a?a=1",
          ),
        )
        checkAll(urls) { case (url, expected) =>
          val app = Route
            .collect[Request] { case req => Response.text(req.url.encode) } @@ dropTrailingSlash
          for {
            url      <- ZIO.fromEither(URL.fromString(url))
            response <- app.runZIO(Request.get(url = url))
            text     <- response.body.asString
          } yield assertTrue(text == expected)
        }
      },
    ),
    suite("trailingSlashRedirect")(
      test("should send a redirect response") {
        val urls = Gen.fromIterable(
          Seq(
            "/"     -> "",
            "/a/"   -> "/a",
            "/a/b/" -> "/a/b",
          ),
        )

        checkAll(urls zip Gen.fromIterable(Seq(true, false))) { case (url, expected, perm) =>
          val app      = Handler.ok @@ redirectTrailingSlash(perm)
          val location = Some(expected)
          val status   = if (perm) Status.PermanentRedirect else Status.TemporaryRedirect

          for {
            url      <- ZIO.fromEither(URL.fromString(url))
            response <- app.runZIO(Request.get(url = url))
          } yield assertTrue(
            response.status == status,
            response.headers.location == location,
          )
        }
      },
      test("should not send a redirect response") {
        val urls = Gen.fromIterable(
          Seq(
            "",
            "/a",
            "/a/b",
            "/a/b/?a=1",
          ),
        )

        checkAll(urls) { url =>
          val app = Handler.ok @@ redirectTrailingSlash(true)
          for {
            url      <- ZIO.fromEither(URL.fromString(url))
            response <- app.runZIO(Request.get(url = url))
          } yield assertTrue(response.status == Status.Ok)
        }
      },
    ),
    suite("prettify error")(
      test("should not add anything to the body  as request do not have an accept header") {
        val app = (Handler.error("Error !!!") @@ beautifyErrors) header "content-type"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isNone)
      },
      test("should return a html body as the request has accept header set to text/html.") {
        val app = (Handler
          .error("Error !!!") @@ beautifyErrors) header "content-type"
        assertZIO(
          app.runZIO(
            Request.get(URL.empty).copy(headers = Headers.accept(HeaderValues.textHtml)),
          ),
        )(isSome(equalTo("text/html")))
      },
      test("should return a plain body as the request has accept header set to */*.") {
        val app = (Handler
          .error("Error !!!") @@ beautifyErrors) header "content-type"
        assertZIO(
          app.runZIO(
            Request.get(URL.empty).copy(headers = Headers.accept("*/*")),
          ),
        )(isSome(equalTo("text/plain")))
      },
      test("should not add anything to the body as the request has accept header set to application/json.") {
        val app = (Handler
          .error("Error !!!") @@ beautifyErrors) header "content-type"
        assertZIO(
          app.runZIO(
            Request.get(URL.empty).copy(headers = Headers.accept(HeaderValues.applicationJson)),
          ),
        )(isNone)
      },
    ),
  )

  private def cond(flg: Boolean) = (_: Any) => flg

  private def condZIO(flg: Boolean) = (_: Any) => ZIO.succeed(flg)

  private def runApp[R, E](app: HttpRoute[R, E]): ZIO[R, Option[E], Response] = {
    for {
      fib <- app.runZIO { Request.get(url = URL(!! / "health")) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }
}
