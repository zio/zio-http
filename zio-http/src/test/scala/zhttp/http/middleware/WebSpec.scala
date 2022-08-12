package zhttp.http.middleware

import zhttp.http.Middleware._
import zhttp.http._
import zhttp.internal.HttpAppTestExtensions
import zio._
import zio.test.Assertion._
import zio.test._

object WebSpec extends ZIOSpecDefault with HttpAppTestExtensions { self =>
  private val app  = Http.collectZIO[Request] { case Method.GET -> !! / "health" =>
    ZIO.succeed(Response.ok).delay(1 second)
  }
  private val midA = Middleware.addHeader("X-Custom", "A")
  private val midB = Middleware.addHeader("X-Custom", "B")

  def spec = suite("HttpMiddleware")(
    suite("headers suite")(
      test("addHeaders") {
        val middleware = addHeaders(Headers("KeyA", "ValueA") ++ Headers("KeyB", "ValueB"))
        val headers    = (Http.ok @@ middleware).headerValues
        assertZIO(headers(Request()))(contains("ValueA") && contains("ValueB"))
      },
      test("addHeader") {
        val middleware = addHeader("KeyA", "ValueA")
        val headers    = (Http.ok @@ middleware).headerValues
        assertZIO(headers(Request()))(contains("ValueA"))
      },
      test("updateHeaders") {
        val middleware = updateHeaders(_ => Headers("KeyA", "ValueA"))
        val headers    = (Http.ok @@ middleware).headerValues
        assertZIO(headers(Request()))(contains("ValueA"))
      },
      test("removeHeader") {
        val middleware = removeHeader("KeyA")
        val headers    = (Http.succeed(Response.ok.setHeaders(Headers("KeyA", "ValueA"))) @@ middleware) header "KeyA"
        assertZIO(headers(Request()))(isNone)
      },
    ),
    suite("debug")(
      test("log status method url and time") {
        val program = runApp(app @@ debug) *> TestConsole.output
        assertZIO(program)(equalTo(Vector("200 GET /health 1000ms\n")))
      },
      test("log 404 status method url and time") {
        val program = runApp(Http.empty ++ Http.notFound @@ debug) *> TestConsole.output
        assertZIO(program)(equalTo(Vector("404 GET /health 0ms\n")))
      },
    ),
    suite("when")(
      test("condition is true") {
        val program = runApp(self.app @@ debug.when(_ => true)) *> TestConsole.output
        assertZIO(program)(equalTo(Vector("200 GET /health 1000ms\n")))
      } +
        test("condition is false") {
          val log = runApp(self.app @@ debug.when(_ => false)) *> TestConsole.output
          assertZIO(log)(equalTo(Vector()))
        },
    ),
    suite("whenZIO")(
      test("condition is true") {
        val program = runApp(self.app @@ debug.whenZIO(_ => ZIO.succeed(true))) *> TestConsole.output
        assertZIO(program)(equalTo(Vector("200 GET /health 1000ms\n")))
      },
      test("condition is false") {
        val log = runApp(self.app @@ debug.whenZIO(_ => ZIO.succeed(false))) *> TestConsole.output
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
        val headers    = (Http.ok @@ middleware).headerValues
        assertZIO(headers(Request()))(contains("ValueA") && contains("ValueB"))
      },
      test("add and remove header") {
        val middleware = addHeader("KeyA", "ValueA") ++ removeHeader("KeyA")
        val program    = (Http.ok @@ middleware) header "KeyA"
        assertZIO(program(Request()))(isNone)
      },
    ),
    suite("ifRequestThenElseZIO")(
      test("if the condition is true take first") {
        val app = (Http.ok @@ ifRequestThenElseZIO(condZIO(true))(midA, midB)) header "X-Custom"
        assertZIO(app(Request()))(isSome(equalTo("A")))
      },
      test("if the condition is false take 2nd") {
        val app =
          (Http.ok @@ ifRequestThenElseZIO(condZIO(false))(midA, midB)) header "X-Custom"
        assertZIO(app(Request()))(isSome(equalTo("B")))
      },
    ),
    suite("ifRequestThenElse")(
      test("if the condition is true take first") {
        val app = Http.ok @@ ifRequestThenElse(cond(true))(midA, midB) header "X-Custom"
        assertZIO(app(Request()))(isSome(equalTo("A")))
      },
      test("if the condition is false take 2nd") {
        val app = Http.ok @@ ifRequestThenElse(cond(false))(midA, midB) header "X-Custom"
        assertZIO(app(Request()))(isSome(equalTo("B")))
      },
    ),
    suite("whenStatus")(
      test("if the condition is true apply middleware") {
        val app = Http.ok @@ Middleware.whenStatus(_ == Status.Ok)(midA) header "X-Custom"
        assertZIO(app(Request()))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply the middleware") {
        val app = Http.ok @@ Middleware.whenStatus(_ == Status.NoContent)(midA) header "X-Custom"
        assertZIO(app(Request()))(isNone)
      },
    ),
    suite("whenRequestZIO")(
      test("if the condition is true apply middleware") {
        val app = (Http.ok @@ whenRequestZIO(condZIO(true))(midA)) header "X-Custom"
        assertZIO(app(Request()))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply any middleware") {
        val app = (Http.ok @@ whenRequestZIO(condZIO(false))(midA)) header "X-Custom"
        assertZIO(app(Request()))(isNone)
      },
    ),
    suite("whenRequest")(
      test("if the condition is true apply middleware") {
        val app = Http.ok @@ Middleware.whenRequest(cond(true))(midA) header "X-Custom"
        assertZIO(app(Request()))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply the middleware") {
        val app = Http.ok @@ Middleware.whenRequest(cond(false))(midA) header "X-Custom"
        assertZIO(app(Request()))(isNone)
      },
    ),
    suite("whenResponseZIO")(
      test("if the condition is true apply middleware") {
        val app = (Http.ok @@ whenResponseZIO(condZIO(true))(midA)) header "X-Custom"
        assertZIO(app(Request()))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply any middleware") {
        val app = (Http.ok @@ whenResponseZIO(condZIO(false))(midA)) header "X-Custom"
        assertZIO(app(Request()))(isNone)
      },
    ),
    suite("whenResponse")(
      test("if the condition is true apply middleware") {
        val app = Http.ok @@ Middleware.whenResponse(cond(true))(midA) header "X-Custom"
        assertZIO(app(Request()))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply the middleware") {
        val app = Http.ok @@ Middleware.whenResponse(cond(false))(midA) header "X-Custom"
        assertZIO(app(Request()))(isNone)
      },
    ),
    suite("cookie")(
      test("addCookie") {
        val cookie = Cookie("test", "testValue")
        val app    = (Http.ok @@ addCookie(cookie)).header("set-cookie")
        assertZIO(app(Request()))(
          equalTo(Some(cookie.encode)),
        )
      },
      test("addCookieM") {
        val cookie = Cookie("test", "testValue")
        val app    =
          (Http.ok @@ addCookieZIO(ZIO.succeed(cookie))).header("set-cookie")
        assertZIO(app(Request()))(
          equalTo(Some(cookie.encode)),
        )
      },
    ),
    suite("signCookies")(
      test("should sign cookies") {
        val cookie = Cookie("key", "value").withHttpOnly
        val app    = Http.ok.withSetCookie(cookie) @@ signCookies("secret") header "set-cookie"
        assertZIO(app(Request()))(isSome(equalTo(cookie.sign("secret").encode)))
      } +
        test("sign cookies no cookie header") {
          val app = (Http.ok.addHeader("keyA", "ValueA") @@ signCookies("secret")).headerValues
          assertZIO(app(Request()))(contains("ValueA"))
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
          val app = Http.collect[Request] { case req => Response.text(req.url.encode) } @@ dropTrailingSlash
          for {
            url      <- ZIO.fromEither(URL.fromString(url))
            response <- app(Request(url = url))
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
          val app      = Http.ok @@ redirectTrailingSlash(perm)
          val location = Some(expected)
          val status   = if (perm) Status.PermanentRedirect else Status.TemporaryRedirect

          for {
            url      <- ZIO.fromEither(URL.fromString(url))
            response <- app(Request(url = url))
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
          val app = Http.ok @@ redirectTrailingSlash(true)
          for {
            url      <- ZIO.fromEither(URL.fromString(url))
            response <- app(Request(url = url))
          } yield assertTrue(response.status == Status.Ok)
        }
      },
    ),
  )

  private def cond(flg: Boolean) = (_: Any) => flg

  private def condZIO(flg: Boolean) = (_: Any) => ZIO.succeed(flg)

  private def runApp[R, E](app: HttpApp[R, E]): ZIO[R, Option[E], Response] = {
    for {
      fib <- app { Request(url = URL(!! / "health")) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }
}
