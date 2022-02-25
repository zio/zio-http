package zhttp.http.middleware

import zhttp.http._
import zhttp.internal.HttpAppTestExtensions
import zio.UIO
import zio.test.Assertion._
import zio.test._

object AuthSpec extends DefaultRunnableSpec with HttpAppTestExtensions {
  private val basicHS: Headers = Headers.basicAuthorizationHeader("user", "resu")
  private val basicHF: Headers = Headers.basicAuthorizationHeader("user", "user")
  private val jwtToken: String = "dummyJwtTocken"
  private val barerHS: Headers = Headers.bearerAuthorizationHeader(jwtToken)
  private val barerHF: Headers = Headers.bearerAuthorizationHeader(jwtToken + "SomethingElse")

  private val basicAuthM: HttpMiddleware[Any, Nothing]    = Middleware.basicAuth { c =>
    c.uname.reverse == c.upassword
  }
  private val basicAuthZIOM: HttpMiddleware[Any, Nothing] = Middleware.basicAuthZIO { c =>
    UIO(c.uname.reverse == c.upassword)
  }
  private val jwtAuthM: HttpMiddleware[Any, Nothing]      = Middleware.jwtAuth { c =>
    c == jwtToken
  }
  private val jwtAuthZIOM: HttpMiddleware[Any, Nothing]   = Middleware.jwtAuthZIO { c =>
    UIO(c == jwtToken)
  }

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
    } +
      suite("basicAuthZIO") {
        testM("HttpApp is accepted if the basic authentication succeeds") {
          val app = (Http.ok @@ basicAuthZIOM).status
          assertM(app(Request().addHeaders(basicHS)))(equalTo(Status.OK))
        } +
          testM("Uses forbidden app if the basic authentication fails") {
            val app = (Http.ok @@ basicAuthZIOM).status
            assertM(app(Request().addHeaders(basicHF)))(equalTo(Status.FORBIDDEN))
          } +
          testM("Responses should have WWW-Authentication header if Basic Auth failed") {
            val app = Http.ok @@ basicAuthZIOM header "WWW-AUTHENTICATE"
            assertM(app(Request().addHeaders(basicHF)))(isSome)
          }
      } +
      suite("jwtAuth") {
        testM("HttpApp is accepted if the jwt authentication succeeds") {
          val app = (Http.ok @@ jwtAuthM).status
          assertM(app(Request().addHeaders(barerHS)))(equalTo(Status.OK))
        } +
          testM("Uses forbidden app if the jwt authentication fails") {
            val app = (Http.ok @@ jwtAuthM).status
            assertM(app(Request().addHeaders(barerHF)))(equalTo(Status.FORBIDDEN))
          } +
          testM("Responses should have WWW-Authentication header if jwt Auth failed") {
            val app = Http.ok @@ jwtAuthM header "WWW-AUTHENTICATE"
            assertM(app(Request().addHeaders(barerHF)))(isSome)
          }
      } +
      suite("jwtAuthZIO") {
        testM("HttpApp is accepted if the jwt authentication succeeds") {
          val app = (Http.ok @@ jwtAuthZIOM).status
          assertM(app(Request().addHeaders(barerHS)))(equalTo(Status.OK))
        } +
          testM("Uses forbidden app if the jwt authentication fails") {
            val app = (Http.ok @@ jwtAuthZIOM).status
            assertM(app(Request().addHeaders(barerHF)))(equalTo(Status.FORBIDDEN))
          } +
          testM("Responses should have WWW-Authentication header if jwt Auth failed") {
            val app = Http.ok @@ jwtAuthZIOM header "WWW-AUTHENTICATE"
            assertM(app(Request().addHeaders(barerHF)))(isSome)
          }
      }
  }
}
