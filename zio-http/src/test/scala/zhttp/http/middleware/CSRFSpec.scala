package zhttp.http.middleware

import zhttp.http.Middleware.csrfValidate
import zhttp.http._
import zhttp.internal.HttpAppTestExtensions
import zio.Ref
import zio.test.Assertion.equalTo
import zio.test._

object CSRFSpec extends DefaultRunnableSpec with HttpAppTestExtensions {
  override def spec = suite("csrf") {
    val app           = (Http.ok @@ csrfValidate("x-token")).getStatus
    val setCookie     = Headers.cookie(Cookie("x-token", "secret"))
    val invalidXToken = Headers("x-token", "secret1")
    val validXToken   = Headers("x-token", "secret")
    testM("x-token not present") {
      assertM(app(Request(headers = setCookie)))(equalTo(Status.FORBIDDEN))
    } +
      testM("x-token mismatch") {
        assertM(app(Request(headers = setCookie ++ invalidXToken)))(
          equalTo(Status.FORBIDDEN),
        )
      } +
      testM("x-token match") {
        assertM(app(Request(headers = setCookie ++ validXToken)))(
          equalTo(Status.OK),
        )
      } +
      testM("app execution skipped") {
        for {
          r <- Ref.make(false)
          app = Http.ok.tapZIO(_ => r.set(true)) @@ csrfValidate("x-token")
          _   <- app(Request(headers = setCookie ++ invalidXToken))
          res <- r.get
        } yield assert(res)(equalTo(false))
      }
  }

}
