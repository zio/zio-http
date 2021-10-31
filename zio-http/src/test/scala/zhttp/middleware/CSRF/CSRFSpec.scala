package zhttp.middleware.CSRF

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http._
import zhttp.http.middleware.CSRF.CSRF
import zhttp.http.middleware.CSRF.CSRF.CookieSetting
import zio.UIO
import zio.test.Assertion.equalTo
import zio.test._

object CSRFSpec extends DefaultRunnableSpec {

  def spec = suite("CSRF Builder") {
    val csrf = CSRF("x-csrf", CookieSetting("csrf-token"), () => UIO("token"))
    testM("Should set set-cookie header") {
      val app: HttpApp[Any, Nothing]      = HttpApp.collect { case Method.GET -> !! / "health" =>
        Response.ok
      }
      def run(app: HttpApp[Any, Nothing]) = {
        app { Request(url = URL(!! / "health")) }
      }
      val middleware                      = csrf.generateToken
      val program                         = run(app @@ middleware).map(_.headers)
      assertM(program)(equalTo(List(Header("Set-Cookie", "csrf-token=token; SameSite=Lax"))))
    } +
      testM("Should set response status to UNAUTHORIZED, if CSRF header is not set") {
        val app: HttpApp[Any, Nothing]      = HttpApp.collect { case Method.GET -> !! / "health" =>
          Response.ok
        }
        def run(app: HttpApp[Any, Nothing]) = {
          app {
            Request(
              url = URL(!! / "health"),
              headers = List(Header(HttpHeaderNames.COOKIE, Cookie(name = "csrf-token", content = "token").encode)),
            )
          }
        }
        val middleware                      = csrf.checkToken
        val program                         = run(app @@ middleware).map(_.status)
        assertM(program)(equalTo(Status.UNAUTHORIZED))
      } +
      testM("Should set response status to OK, if CSRF header is set correctly") {
        val app: HttpApp[Any, Nothing]      = HttpApp.collect { case Method.GET -> !! / "health" =>
          Response.ok
        }
        def run(app: HttpApp[Any, Nothing]) = {
          app {
            Request(
              url = URL(!! / "health"),
              headers = List(
                Header(HttpHeaderNames.COOKIE, Cookie(name = "csrf-token", content = "token").encode),
                Header("x-csrf", "token"),
              ),
            )
          }
        }
        val middleware                      = csrf.checkToken
        val program                         = run(app @@ middleware).map(_.status)
        assertM(program)(equalTo(Status.OK))
      }
  }
}
