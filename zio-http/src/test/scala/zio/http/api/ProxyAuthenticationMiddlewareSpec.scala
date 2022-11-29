package zio.http.api

import zio.http._
import zio.http.internal.HttpGen
import zio.test._

object ProxyAuthenticationMiddlewareSpec extends ZIOSpecDefault {
  override def spec =
    suite("ProxyAuthenticationMiddlewareSpec")(
      suite("valid values")(
        test("Proxy-Authenticate with realm") {
          check(HttpGen.authSchemes, Gen.alphaNumericStringBounded(4, 6)) { (scheme, realm) =>
            val header = s"${scheme.name} realm=$realm"
            for {
              response <- api.Middleware
                .withProxyAuthenticate(header)
                .apply(Http.succeed(Response.ok))
                .apply(Request.get(URL.empty))
            } yield assertTrue(
              response.headers.proxyAuthenticate.contains(header),
            )
          }
        },
        test("Proxy-Authenticate without realm") {
          check(HttpGen.authSchemes) { scheme =>
            for {
              response <- api.Middleware
                .withProxyAuthenticate(scheme.name)
                .apply(Http.succeed(Response.ok))
                .apply(Request.get(URL.empty))
            } yield assertTrue(
              response.headers.proxyAuthenticate.contains(scheme.name),
            )
          }
        },
      ),
      test("invalid value") {
        for {
          response <- api.Middleware
            .withProxyAuthenticate("bad input")
            .apply(Http.succeed(Response.ok))
            .apply(Request.get(URL.empty))
        } yield assertTrue(
          response.headers.proxyAuthenticate.contains(""),
        )
      },
    )
}
