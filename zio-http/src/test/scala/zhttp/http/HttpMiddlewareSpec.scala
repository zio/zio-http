package zhttp.http

import zhttp.http.middleware.HttpMiddleware
import zio.test.Assertion.equalTo
import zio.test.environment.TestConsole
import zio.test.{DefaultRunnableSpec, assertM}

object HttpMiddlewareSpec extends DefaultRunnableSpec {
  val app = HttpApp.collect { case Method.GET -> !! / "health" =>
    Response.ok
  }

  def spec = suite("Debug")(testM("Log status, method") {
    val debugApp = app @@ HttpMiddleware.debug

    val a = for {
      _   <- debugApp(req = Request(url = URL(!! / "health")))
      out <- TestConsole.output
    } yield out

    assertM(a)(equalTo(Vector("200 GET /health 0ms\n")))

  })
}
