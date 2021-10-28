package zhttp.http

import zhttp.http.middleware.HttpMiddleware
import zio.test.Assertion.equalTo
import zio.test.environment.TestConsole
import zio.test.{DefaultRunnableSpec, assertM}

object HttpMiddlewareSpec extends DefaultRunnableSpec {
  val app = HttpApp.collect { case Method.GET -> !! / "health" =>
    Response.ok
  }

  def updateApp[R, E](app: HttpApp[R, E]) = for {
    _   <- app(req = Request(url = URL(!! / "health")))
    out <- TestConsole.output
  } yield out

  def spec = suite("Debug")(
    testM("log status method url and time") {
      val debugApp = app @@ HttpMiddleware.debug
      val a        = updateApp(debugApp)
      assertM(a)(equalTo(Vector("200 GET /health 0ms\n")))
    } +
      testM("log when method is GET") {
        val debugApp = app @@ HttpMiddleware.debug.when((method, _, _) => method == Method.GET)
        val a        = updateApp(debugApp)
        assertM(a)(equalTo(Vector("200 GET /health 0ms\n")))
      } +
      testM("fail log when method is POST") {
        val debugApp = app @@ HttpMiddleware.debug.when((method, _, _) => method == Method.POST)
        val a        = updateApp(debugApp)
        assertM(a)(equalTo(Vector()))
      },
  )
}
