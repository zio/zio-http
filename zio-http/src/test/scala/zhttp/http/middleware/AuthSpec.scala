package zhttp.http.middleware

import zhttp.http._
import zhttp.internal.HttpAppTestExtensions
import zio.test.Assertion._
import zio.test._

object AuthSpec extends DefaultRunnableSpec with HttpAppTestExtensions {
  private val basicHS    = Headers.basicAuthorizationHeader("user", "resu")
  private val basicHF    = Headers.basicAuthorizationHeader("user", "user")
  private val basicAuthM = Middleware.basicAuth { case (u, p) => p.toString.reverse == u }

  def spec = suite("AuthSpec") {
    suite("basicAuth") {
      testM("HttpApp is accepted if the basic authentication succeeds") {
        val app = (Http.ok @@ basicAuthM).status
        assertM(app(Request().addHeaders(basicHS)))(equalTo(Status.OK))
      } +
        testM("Uses forbidden app if the basic authentication fails") {
          val app = (Http.ok @@ basicAuthM).status
          assertM(app(Request().addHeaders(basicHF)))(equalTo(Status.FORBIDDEN))
        } +
        testM("Responses should have WWW-Authentication header if Basic Auth failed") {
          val app = Http.ok @@ basicAuthM header "WWW-AUTHENTICATE"
          assertM(app(Request().addHeaders(basicHF)))(isSome)
        }
    }
  }
}
