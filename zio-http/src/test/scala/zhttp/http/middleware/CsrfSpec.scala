package zhttp.http.middleware

import zhttp.http.Middleware.csrfValidate
import zhttp.http._
import zhttp.internal.HttpAppTestExtensions
import zio.Ref
import zio.test.Assertion.equalTo
import zio.test._

object CsrfSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  override def spec = suite("CSRF Middlewares") {
    val app           = (Http.ok @@ csrfValidate("x-token")).status
    val setCookie     = Headers.cookie(Cookie("x-token", "secret"))
    val invalidXToken = Headers("x-token", "secret1")
    val validXToken   = Headers("x-token", "secret")
    test("x-token not present") {
      assertZIO(app(Request(headers = setCookie)))(equalTo(Status.Forbidden))
    } +
      test("x-token mismatch") {
        assertZIO(app(Request(headers = setCookie ++ invalidXToken)))(
          equalTo(Status.Forbidden),
        )
      } +
      test("x-token match") {
        assertZIO(app(Request(headers = setCookie ++ validXToken)))(
          equalTo(Status.Ok),
        )
      } +
      test("app execution skipped") {
        for {
          r <- Ref.make(false)
          app = Http.ok.tapZIO(_ => r.set(true)) @@ csrfValidate("x-token")
          _   <- app(Request(headers = setCookie ++ invalidXToken))
          res <- r.get
        } yield assert(res)(equalTo(false))
      }
  }

}
