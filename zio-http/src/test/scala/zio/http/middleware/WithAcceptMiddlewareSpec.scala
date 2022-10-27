package zio.http.middleware

import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio.test.Assertion.hasSubset
import zio.test._

object WithAcceptMiddlewareSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  private val request = Request.get(URL.empty)
  override def spec   = suite("Header Middlewares")(
    test("withAccept") {
      val app      = Http.ok.withMiddleware(api.Middleware.withAccept("accept"))
      val expected = request.withAccept("accept").headersAsList

      for {
        res <- app(request)
      } yield assert(res.headersAsList)(hasSubset(expected))
    },
    test("withAcceptEncoding") {
      val app      = Http.ok.withMiddleware(api.Middleware.withAcceptEncoding("acceptEncoding"))
      val expected = request.withAcceptEncoding("acceptEncoding").headersAsList

      for {
        res <- app(request)
      } yield assert(res.headers)(hasSubset(expected))
    },
    test("withAcceptLanguage") {
      val app      = Http.ok.withMiddleware(api.Middleware.withAcceptLanguage("acceptLanguage"))
      val expected = request.withAcceptLanguage("acceptLanguage").headersAsList

      for {
        res <- app(request)
      } yield assert(res.headersAsList)(hasSubset(expected))
    },
    test("withAcceptPatch") {
      val app      = Http.ok.withMiddleware(api.Middleware.withAcceptPatch("acceptPatch"))
      val expected = request.withAcceptPatch("acceptPatch").headersAsList

      for {
        res <- app(request)
      } yield assert(res.headersAsList)(hasSubset(expected))
    },
    test("withAcceptRanges") {
      val app      = Http.ok.withMiddleware(api.Middleware.withAcceptRanges("acceptRanges"))
      val expected = request.withAcceptRanges("acceptRanges").headersAsList

      for {
        res <- app(request)
      } yield assert(res.headersAsList)(hasSubset(expected))
    },
  )

}
