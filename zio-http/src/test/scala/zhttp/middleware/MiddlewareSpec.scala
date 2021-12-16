package zhttp.middleware

import zhttp.http.Middleware.cors
import zhttp.http._
import zhttp.internal.HttpAppTestExtensions
import zio._
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, TestClock, TestConsole, assert, assertM}

object MiddlewareSpec extends DefaultRunnableSpec with HttpAppTestExtensions {
  def cond(flg: Boolean) = (_: Any, _: Any, _: Any) => flg

  def condM(flg: Boolean) = (_: Any, _: Any, _: Any) => UIO(flg)

  def corsSpec = suite("cors") {
    test("options request") {
      val app     = Http.collect[Request] { case Method.GET -> !! / "success" => Response.ok } @@ cors()
      val headers = List(Header.accessControlRequestMethod(Method.GET), Header.origin("test-env"))
      val res     = app(Request(headers = headers)).map(_.headers)
      assertM(res)(
        hasSubset(
          List(
            Header.accessControlAllowCredentials(true),
            Header.accessControlAllowMethods(Method.GET),
            Header.accessControlAllowOrigin("test-env"),
            Header.accessControlAllowHeaders(CORS.DefaultCORSConfig.allowedHeaders.getOrElse(Set.empty).mkString(", ")),
          ),
        ),
      )
    }
  }

  def run[R, E](app: HttpApp[R, E]): ZIO[TestClock with R, Option[E], Response[R, E]] = {
    for {
      fib <- app { Request(url = URL(!! / "health")) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }

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
          val app = (Http.ok @@ ifThenElseM(condM(true))(midA, midB)) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          test("if the condition is false take 2nd") {
            val app =
              (Http.ok @@ ifThenElseM(condM(false))(midA, midB)) getHeader "X-Custom"
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
          val app = (Http.ok @@ whenM(condM(true))(midA)) getHeader "X-Custom"
          assertM(app(Request()))(isSome(equalTo("A")))
        } +
          test("if the condition is false don't apply any middleware") {
            val app = (Http.ok @@ whenM(condM(false))(midA)) getHeader "X-Custom"
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
            assertM(app(Request().addHeaders(List(basicHS))))(equalTo(Status.OK))
          } +
            test("Uses forbidden app if the basic authentication fails") {
              val app = (Http.ok @@ basicAuthM).getStatus
              assertM(app(Request().addHeaders(List(basicHF))))(equalTo(Status.FORBIDDEN))
            } +
            test("Responses sould have WWW-Authentication header if Basic Auth failed") {
              val app = Http.ok @@ basicAuthM getHeader "WWW-AUTHENTICATE"
              assertM(app(Request().addHeaders(List(basicHF))))(isSome)
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
            headers = List(Header.accessControlRequestMethod(Method.GET), Header.origin("test-env")),
          )

          val expected = List(
            Header.accessControlAllowCredentials(true),
            Header.accessControlAllowMethods(Method.GET),
            Header.accessControlAllowOrigin("test-env"),
            Header.accessControlAllowHeaders(CORS.DefaultCORSConfig.allowedHeaders.getOrElse(Set.empty).mkString(",")),
          )

          for {
            res <- app(request)
          } yield assert(res.headers.map(_.toTuple))(hasSubset(expected.map(_.toTuple))) &&
            assert(res.status)(equalTo(Status.NO_CONTENT))
        } +
          test("GET request") {
            val request =
              Request(
                method = Method.GET,
                url = URL(!! / "success"),
                headers = List(Header.accessControlRequestMethod(Method.GET), Header.origin("test-env")),
              )

            val expected = List(
              Header.accessControlExposeHeaders("*"),
              Header.accessControlAllowOrigin("test-env"),
              Header.accessControlAllowMethods(Method.GET),
              Header.accessControlAllowCredentials(true),
            )

            for {
              res <- app(request)
            } yield assert(res.headers.map(_.toTuple))(hasSubset(expected.map(_.toTuple)))
          }
      }
  }

  private val app: HttpApp[Any with Clock, Nothing] = Http.collectM[Request] { case Method.GET -> !! / "health" =>
    UIO(Response.ok).delay(1 second)
  }

  private val midA       = Middleware.addHeader("X-Custom", "A")
  private val midB       = Middleware.addHeader("X-Custom", "B")
  private val basicHS    = Header.basicHttpAuthorization("user", "resu")
  private val basicHF    = Header.basicHttpAuthorization("user", "user")
  private val basicAuthM = Middleware.basicAuth((u, p) => p.reverse == u)
}
