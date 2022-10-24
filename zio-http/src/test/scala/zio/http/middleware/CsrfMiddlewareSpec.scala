package zio.http.middleware

import zio.Ref
import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio.http.model.{Cookie, Headers, Status}
import zio.test.Assertion.equalTo
import zio.test._

object CsrfMiddlewareSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  private val app           = Http.ok.withMiddleware(api.Middleware.csrfValidate("x-token")).status
  private val setCookie     = Headers.cookie(Cookie("x-token", "secret").toRequest)
  private val invalidXToken = Headers("x-token", "secret1")
  private val validXToken   = Headers("x-token", "secret")
  override def spec         = suite("CSRF Middlewares")(
    test("x-token not present") {
      assertZIO(app(Request.get(URL.empty).copy(headers = setCookie)))(equalTo(Status.Forbidden))
    },
    test("x-token mismatch") {
      assertZIO(app(Request.get(URL.empty).copy(headers = setCookie ++ invalidXToken)))(
        equalTo(Status.Forbidden),
      )
    },
    test("x-token match") {
      assertZIO(app(Request.get(URL.empty).copy(headers = setCookie ++ validXToken)))(
        equalTo(Status.Ok),
      )
    },
    test("app execution skipped") {
      for {
        r <- Ref.make(false)
        app = Http.ok.tapZIO(_ => r.set(true)).withMiddleware(api.Middleware.csrfValidate("x-token"))
        _   <- app(Request.get(URL.empty).copy(headers = setCookie ++ invalidXToken))
        res <- r.get
      } yield assert(res)(equalTo(false))
    },
  )

}
