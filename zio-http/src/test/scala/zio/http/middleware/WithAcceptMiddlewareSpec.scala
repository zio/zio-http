package zio.http.middleware

import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio.http.model.Headers
import zio.test.Assertion.hasSubset
import zio.test._

object WithAcceptMiddlewareSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  override def spec = suite("WithAccept* Middleware")(
    test("withAccept") {
      val app      = Http.ok.withMiddleware(api.Middleware.withAccept("application/json"))
      val expected = Headers.accept("application/json").headersAsList

      for {
        res <- app.toZIO(Request.get(URL.empty))
      } yield assert(res.headersAsList)(hasSubset(expected))
    },
    test("withAcceptEncoding") {
      val app      = Http.ok.withMiddleware(api.Middleware.withAcceptEncoding("compress"))
      val expected = Headers.acceptEncoding("compress").headersAsList

      for {
        res <- app.toZIO(Request.get(URL.empty))
      } yield assert(res.headersAsList)(hasSubset(expected))
    },
    test("withAcceptLanguage") {
      val app      = Http.ok.withMiddleware(api.Middleware.withAcceptLanguage("en"))
      val expected = Headers.acceptLanguage("en").headersAsList

      for {
        res <- app.toZIO(Request.get(URL.empty))
      } yield assert(res.headersAsList)(hasSubset(expected))
    },
    test("withAcceptPatch") {
      val app      = Http.ok.withMiddleware(api.Middleware.withAcceptPatch("application/example"))
      val expected = Headers.acceptPatch("application/example").headersAsList

      for {
        res <- app.toZIO(Request.get(URL.empty))
      } yield assert(res.headersAsList)(hasSubset(expected))
    },
    test("withAcceptRanges") {
      val app      = Http.ok.withMiddleware(api.Middleware.withAcceptRanges("bytes"))
      val expected = Headers.acceptRanges("bytes").headersAsList

      for {
        res <- app.toZIO(Request.get(URL.empty))
      } yield assert(res.headersAsList)(hasSubset(expected))
    },
  )

}
