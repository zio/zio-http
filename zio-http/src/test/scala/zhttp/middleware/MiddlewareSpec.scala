package zhttp.middleware

import zhttp.http._
import zhttp.internal.HttpAppTestExtensions
import zio._
import zio.test.Assertion._
import zio.test.{TestClock, TestConsole, _}

object MiddlewareSpec extends DefaultRunnableSpec with HttpAppTestExtensions {
  def spec = suite("HttpMiddleware") {
    import Middleware._

    suite("debug") {
      test("log status method url and time") {
        val program = run(app @@ debug) *> TestConsole.output
        assertM(program)(equalTo(Vector("200 GET /health 1000ms\n")))
      } +
        test("log 404 status method url and time") {
          val program = run(Http.empty @@ debug.withEmpty) *> TestConsole.output
          assertM(program)(equalTo(Vector("404 GET /health 0ms\n")))
        }
    } +
      suite("withEmpty") {
        test("log 404 status method url and time") {
          val program = run(Http.empty @@ debug.withEmpty) *> TestConsole.output
          assertM(program)(equalTo(Vector("404 GET /health 0ms\n")))
        }
      } +
      suite("when") {
        test("condition is true") {
          val program = run(app @@ debug.when((_, _, _) => true)) *> TestConsole.output
          assertM(program)(equalTo(Vector("200 GET /health 1000ms\n")))
        } +
          test("condition is false") {
            val log = run(app @@ debug.when((_, _, _) => false)) *> TestConsole.output
            assertM(log)(equalTo(Vector()))
          }
      } +
      suite("race") {
        test("achieved") {
          val program = run(app @@ timeout(5 seconds)).map(_.status)
          assertM(program)(equalTo(Status.OK))
        } +
          test("un-achieved") {
            val program = run(app @@ timeout(500 millis)).map(_.status)
            assertM(program)(equalTo(Status.REQUEST_TIMEOUT))
          }
      } +
      suite("combine") {
        test("before and after") {
          val middleware = runBefore(Console.printLine("A")) ++ runAfter(Console.printLine("B"))
          val program    = run(app @@ middleware) *> TestConsole.output
          assertM(program)(equalTo(Vector("A\n", "B\n")))
        } +
          test("add headers twice") {
            val middleware = addHeader("KeyA", "ValueA") ++ addHeader("KeyB", "ValueB")
            val headers    = (Http.ok @@ middleware).getHeaderValues
            assertM(headers(Request()))(contains("ValueA") && contains("ValueB"))
          } +
          test("add and remove header") {
            val middleware = addHeader("KeyA", "ValueA") ++ removeHeader("KeyA")
            val program    = (Http.ok @@ middleware) getHeader "KeyA"
            assertM(program(Request()))(isNone)
          }
      } +
      suite("ifThenElseM") {
        test("if the condition is true take first") {
          val app = (Http.ok @@ ifThenElseZIO(condM(true))(midA, midB)) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          test("if the condition is false take 2nd") {
            val app =
              (Http.ok @@ ifThenElseZIO(condM(false))(midA, midB)) getHeader "X-Custom"
            assertM(app(Request()))(isSome(equalTo("B")))
          }
      } +
      suite("ifThenElse") {
        test("if the condition is true take first") {
          val app = Http.ok @@ ifThenElse(cond(true))(midA, midB) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          test("if the condition is false take 2nd") {
            val app = Http.ok @@ ifThenElse(cond(false))(midA, midB) getHeader "X-Custom"
            assertM(app(Request()))(isSome(equalTo("B")))
          }
      } +
      suite("whenM") {
        test("if the condition is true apply middleware") {
          val app = (Http.ok @@ whenZIO(condM(true))(midA)) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          test("if the condition is false don't apply any middleware") {
            val app = (Http.ok @@ whenZIO(condM(false))(midA)) getHeader "X-Custom"
            assertM(app(Request()))(isNone)
          }
      } +
      suite("when") {
        test("if the condition is true apple middleware") {
          val app = Http.ok @@ when(cond(true))(midA) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          test("if the condition is false don't apply the middleware") {
            val app = Http.ok @@ when(cond(false))(midA) getHeader "X-Custom"
            assertM(app(Request()))(isNone)
          }
      } +
      suite("Authentication middleware") {
        suite("basicAuth") {
          test("HttpApp is accepted if the basic authentication succeeds") {
            val app = (Http.ok @@ basicAuthM).getStatus
            assertM(app(Request().addHeaders(basicHS)))(equalTo(Status.OK))
          } +
            test("Uses forbidden app if the basic authentication fails") {
              val app = (Http.ok @@ basicAuthM).getStatus
              assertM(app(Request().addHeaders(basicHF)))(equalTo(Status.FORBIDDEN))
            } +
            test("Responses should have WWW-Authentication header if Basic Auth failed") {
              val app = Http.ok @@ basicAuthM getHeader "WWW-AUTHENTICATE"
              assertM(app(Request().addHeaders(basicHF)))(isSome)
            }
        }
      } +
      suite("cors") {
        // FIXME:The test should ideally pass with `Http.ok` also
        val app = Http.collect[Request] { case Method.GET -> !! / "success" => Response.ok } @@ cors()
        test("OPTIONS request") {
          val request = Request(
            method = Method.OPTIONS,
            url = URL(!! / "success"),
            headers = Headers.accessControlRequestMethod(Method.GET) ++ Headers.origin("test-env"),
          )

          val expected = Headers
            .accessControlAllowCredentials(true)
            .withAccessControlAllowMethods(Method.GET)
            .withAccessControlAllowOrigin("test-env")
            .withAccessControlAllowHeaders(
              CORS.DefaultCORSConfig.allowedHeaders.getOrElse(Set.empty).mkString(","),
            )
            .toList

          for {
            res <- app(request)
          } yield {
            val set = res.getHeadersAsList.toSet

            assertTrue(expected.forall(h => set.contains(h))) && assertTrue(res.status == Status.NO_CONTENT)
          }
        } +
          test("GET request") {
            val request =
              Request(
                method = Method.GET,
                url = URL(!! / "success"),
                headers = Headers.accessControlRequestMethod(Method.GET) ++ Headers.origin("test-env"),
              )

            val expected = Headers
              .accessControlExposeHeaders("*")
              .withAccessControlAllowOrigin("test-env")
              .withAccessControlAllowMethods(Method.GET)
              .withAccessControlAllowCredentials(true)
              .toList

            for {
              res <- app(request).map(_.getHeadersAsList.toSet)
            } yield assertTrue(expected.forall(h => res.contains(h)))
          }
      } +
      suite("cookie") {
        test("addCookie") {
          val cookie = Cookie("test", "testValue")
          val app    = (Http.ok @@ addCookie(cookie)).getHeader("set-cookie")
          assertM(app(Request()))(
            equalTo(Some(cookie.encode)),
          )
        } +
          test("addCookieM") {
            val cookie = Cookie("test", "testValue")
            val app    =
              (Http.ok @@ addCookieM(UIO(cookie))).getHeader("set-cookie")
            assertM(app(Request()))(
              equalTo(Some(cookie.encode)),
            )
          }
      } +
      suite("csrf") {
        val app           = (Http.ok @@ csrfValidate("x-token")).getStatus
        val setCookie     = Headers.cookie(Cookie("x-token", "secret"))
        val invalidXToken = Headers("x-token", "secret1")
        val validXToken   = Headers("x-token", "secret")
        test("x-token not present") {
          assertM(app(Request(headers = setCookie)))(equalTo(Status.FORBIDDEN))
        } +
          test("x-token mismatch") {
            assertM(app(Request(headers = setCookie ++ invalidXToken)))(
              equalTo(Status.FORBIDDEN),
            )
          } +
          test("x-token match") {
            assertM(app(Request(headers = setCookie ++ validXToken)))(
              equalTo(Status.OK),
            )
          } +
          test("app execution skipped") {
            for {
              r <- Ref.make(false)
              app = Http.ok.tapZIO(_ => r.set(true)) @@ csrfValidate("x-token")
              _   <- app(Request(headers = setCookie ++ invalidXToken))
              res <- r.get
            } yield assertTrue(res == false)
          }
      } +
      suite("signCookies") {
        test("should sign cookies") {
          val cookie = Cookie("key", "value").withHttpOnly
          val app    = Http.ok.withSetCookie(cookie) @@ signCookies("secret") getHeader "set-cookie"
          assertM(app(Request()))(isSome(equalTo(cookie.sign("secret").encode)))
        }
      }
  }

  private def app: HttpApp[Any with Clock, Nothing] = Http.collectZIO[Request] { case Method.GET -> !! / "health" =>
    UIO(Response.ok).delay(1 second)
  }
  private val midA                                  = Middleware.addHeader("X-Custom", "A")
  private val midB                                  = Middleware.addHeader("X-Custom", "B")
  private val basicHS                               = Headers.basicAuthorizationHeader("user", "resu")
  private val basicHF                               = Headers.basicAuthorizationHeader("user", "user")
  private val basicAuthM                            = Middleware.basicAuth { case (u, p) => p.toString.reverse == u }

  private def cond(flg: Boolean) = (_: Any, _: Any, _: Any) => flg

  private def condM(flg: Boolean) = (_: Any, _: Any, _: Any) => UIO(flg)

  private def run[R, E](app: HttpApp[R, E]): ZIO[TestClock with R, Option[E], Response] = {
    for {
      fib <- app { Request(url = URL(!! / "health")) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }
}
