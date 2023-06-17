/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.internal.middlewares

import zio._
import zio.test.Assertion._
import zio.test._

import zio.http.HttpAppMiddleware._
import zio.http._
import zio.http.internal.HttpAppTestExtensions

object WebSpec extends ZIOSpecDefault with HttpAppTestExtensions { self =>
  private val app  = Http.collectZIO[Request] { case Method.GET -> Root / "health" =>
    ZIO.succeed(Response.ok).delay(1 second)
  }
  private val midA = HttpAppMiddleware.addHeader("X-Custom", "A")
  private val midB = HttpAppMiddleware.addHeader("X-Custom", "B")

  def spec = suite("WebSpec")(
    suite("headers suite")(
      test("addHeaders") {
        val middleware = addHeaders(Headers("KeyA", "ValueA") ++ Headers("KeyB", "ValueB"))
        val headers    = (Handler.ok @@ middleware).toHttp.headerValues
        assertZIO(headers.runZIO(Request.get(URL.empty)))(contains("ValueA") && contains("ValueB"))
      },
      test("addHeader") {
        val middleware = addHeader("KeyA", "ValueA")
        val headers    = (Handler.ok @@ middleware).toHttp.headerValues
        assertZIO(headers.runZIO(Request.get(URL.empty)))(contains("ValueA"))
      },
      test("updateHeaders") {
        val middleware = updateHeaders(_ => Headers("KeyA", "ValueA"))
        val headers    = (Handler.ok @@ middleware).toHttp.headerValues
        assertZIO(headers.runZIO(Request.get(URL.empty)))(contains("ValueA"))
      },
      test("removeHeader") {
        val middleware = removeHeader("KeyA")
        val headers    =
          (Handler.succeed(Response.ok.setHeaders(Headers("KeyA", "ValueA"))) @@ middleware).toHttp rawHeader "KeyA"
        assertZIO(headers.runZIO(Request.get(URL.empty)))(isNone)
      },
    ),
    suite("debug")(
      test("log status method url and time") {
        for {
          _   <- runApp(app @@ debug)
          log <- TestConsole.output
        } yield assertTrue(
          log.size == 1,
          log.head.startsWith("200 GET /health"),
          log.head.endsWith("ms\n"),
        )
      },
      test("log 404 status method url and time") {
        for {
          _   <- runApp(Http.empty ++ (Handler.notFound @@ debug).toHttp)
          log <- TestConsole.output
        } yield assertTrue(
          log.size == 1,
          log.head.startsWith("404 GET /health"),
          log.head.endsWith("ms\n"),
        )
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
        val program    = (Handler.ok @@ middleware) rawHeader "KeyA"
        assertZIO(program.runZIO(Request.get(URL.empty)))(isNone)
      },
    ),
    suite("ifRequestThenElseZIO")(
      test("if the condition is true take first") {
        val app = (Handler.ok @@ ifRequestThenElseZIO(condZIO(true))(midA, midB)) rawHeader "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("A")))
      },
      test("if the condition is false take 2nd") {
        val app =
          (Handler.ok @@ ifRequestThenElseZIO(condZIO(false))(midA, midB)) rawHeader "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("B")))
      },
    ),
    suite("ifRequestThenElse")(
      test("if the condition is true take first") {
        val app = Handler.ok @@ ifRequestThenElse(cond(true))(midA, midB) rawHeader "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("A")))
      },
      test("if the condition is false take 2nd") {
        val app = Handler.ok @@ ifRequestThenElse(cond(false))(midA, midB) rawHeader "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("B")))
      },
    ),
    suite("whenStatus")(
      test("if the condition is true apply middleware") {
        val app = Handler.ok @@ HttpAppMiddleware.whenStatus(_ == Status.Ok)(midA) rawHeader "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply the middleware") {
        val app = Handler.ok @@ HttpAppMiddleware.whenStatus(_ == Status.NoContent)(midA) rawHeader "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isNone)
      },
    ),
    suite("whenRequestZIO")(
      test("if the condition is true apply middleware") {
        val app = (Handler.ok @@ HttpAppMiddleware.whenRequestZIO(condZIO(true))(midA)) rawHeader "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply any middleware") {
        val app = (Handler.ok @@ HttpAppMiddleware.whenRequestZIO(condZIO(false))(midA)) rawHeader "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isNone)
      },
    ),
    suite("whenRequest")(
      test("if the condition is true apply middleware") {
        val app = Handler.ok @@ HttpAppMiddleware.whenRequest(cond(true))(midA) rawHeader "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply the middleware") {
        val app = Handler.ok @@ HttpAppMiddleware.whenRequest(cond(false))(midA) rawHeader "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isNone)
      },
    ),
    suite("whenResponseZIO")(
      test("if the condition is true apply middleware") {
        val app = (Handler.ok @@ HttpAppMiddleware.whenResponseZIO(condZIO(true))(midA)) rawHeader "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply any middleware") {
        val app = (Handler.ok @@ HttpAppMiddleware.whenResponseZIO(condZIO(false))(midA)) rawHeader "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isNone)
      },
    ),
    suite("whenResponse")(
      test("if the condition is true apply middleware") {
        val app = Handler.ok @@ HttpAppMiddleware.whenResponse(cond(true))(midA) rawHeader "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo("A")))
      },
      test("if the condition is false don't apply the middleware") {
        val app = Handler.ok @@ HttpAppMiddleware.whenResponse(cond(false))(midA) rawHeader "X-Custom"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isNone)
      },
    ),
    suite("cookie")(
      test("addCookie") {
        val cookie = Cookie.Response("test", "testValue")
        val app    = (Handler.ok @@ addCookie(cookie)).rawHeader("set-cookie")
        assertZIO(app.runZIO(Request.get(URL.empty)))(
          equalTo(cookie.encode.toOption),
        )
      },
      test("addCookieM") {
        val cookie = Cookie.Response("test", "testValue")
        val app    =
          (Handler.ok @@ addCookieZIO(ZIO.succeed(cookie))).rawHeader("set-cookie")
        assertZIO(app.runZIO(Request.get(URL.empty)))(
          equalTo(cookie.encode.toOption),
        )
      },
    ),
    suite("signCookies")(
      test("should sign cookies") {
        val cookie = Cookie.Response("key", "value").copy(isHttpOnly = true)
        val app    =
          (Handler.ok.addHeader(Header.SetCookie(cookie)) @@ signCookies("secret")).header(Header.SetCookie)
        assertZIO(app.runZIO(Request.get(URL.empty)))(isSome(equalTo(Header.SetCookie(cookie.sign("secret")))))
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
          val app = Http
            .collect[Request] { case req =>
              Response.text(req.url.encode)
            } @@ dropTrailingSlash(onlyIfNoQueryParams = true)
          for {
            url      <- ZIO.fromEither(URL.decode(url))
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
            "/"     -> "/",
            "/a/"   -> "/a",
            "/a/b/" -> "/a/b",
          ),
        )

        checkAll(urls zip Gen.fromIterable(Seq(true, false))) { case (url, expected, perm) =>
          val app      = Handler.ok @@ redirectTrailingSlash(perm)
          val location = if (url != expected) Some(expected) else None
          val status   =
            if (url == expected) Status.Ok
            else if (perm) Status.PermanentRedirect
            else Status.TemporaryRedirect

          for {
            url      <- ZIO.fromEither(URL.decode(url))
            response <- app.runZIO(Request.get(url = url))
            _        <- ZIO.debug(response.headerOrFail(Header.Location))
          } yield assertTrue(
            response.status == status,
            response.header(Header.Location) == location.map(l => Header.Location(URL.decode(l).toOption.get)),
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
            url      <- ZIO.fromEither(URL.decode(url))
            response <- app.runZIO(Request.get(url = url))
          } yield assertTrue(response.status == Status.Ok)
        }
      },
    ),
    suite("prettify error")(
      test("should not add anything to the body  as request do not have an accept header") {
        val app = (Handler.errorMessage("Error !!!") @@ beautifyErrors) rawHeader "content-type"
        assertZIO(app.runZIO(Request.get(URL.empty)))(isNone)
      },
      test("should return a html body as the request has accept header set to text/html.") {
        val app = (Handler
          .errorMessage("Error !!!") @@ beautifyErrors) rawHeader "content-type"
        assertZIO(
          app.runZIO(
            Request.get(URL.empty).copy(headers = Headers(Header.Accept(MediaType.text.`html`))),
          ),
        )(isSome(equalTo("text/html")))
      },
      test("should return a plain body as the request has accept header set to */*.") {
        val app = (Handler
          .errorMessage("Error !!!") @@ beautifyErrors) rawHeader "content-type"
        assertZIO(
          app.runZIO(
            Request
              .get(URL.empty)
              .copy(headers = Headers(Header.Accept(MediaType.any))),
          ),
        )(isSome(equalTo("text/plain")))
      },
      test("should not add anything to the body as the request has accept header set to application/json.") {
        val app = (Handler
          .errorMessage("Error !!!") @@ beautifyErrors) rawHeader "content-type"
        assertZIO(
          app.runZIO(
            Request.get(URL.empty).copy(headers = Headers(Header.Accept(MediaType.application.json))),
          ),
        )(isNone)
      },
    ),
  )

  private def cond(flg: Boolean) = (_: Any) => flg

  private def condZIO(flg: Boolean) = (_: Any) => ZIO.succeed(flg)

  private def runApp[R, E](app: HttpApp[R, E]): ZIO[R, Option[E], Response] = {
    for {
      fib <- app.runZIO { Request.get(url = URL(Root / "health")) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }
}
