package zio.http.model.headers.values

import zio.http.internal.HttpGen
import zio.test._

object ProxyAuthenticateSpec extends ZIOSpecDefault {
  override def spec =
    suite("ProxyAuthenticateSpec")(
      test("parsing of invalid inputs") {
        assertTrue(ProxyAuthenticate.toProxyAuthenticate("invalid") == ProxyAuthenticate.InvalidProxyAuthenticate) &&
        assertTrue(
          ProxyAuthenticate.toProxyAuthenticate("!123456 realm=somerealm") == ProxyAuthenticate.InvalidProxyAuthenticate,
        )
      },
      test("parsing of valid inputs") {
        check(HttpGen.authSchemes) { scheme =>
          assertTrue(
            ProxyAuthenticate.toProxyAuthenticate(scheme.name) == ProxyAuthenticate.ValidProxyAuthenticate(scheme, None),
          )
        } &&
        check(HttpGen.authSchemes, Gen.alphaNumericStringBounded(4, 6)) { (scheme, realm) =>
          assertTrue(
            ProxyAuthenticate.toProxyAuthenticate(s"${scheme.name} realm=$realm") == ProxyAuthenticate
              .ValidProxyAuthenticate(scheme, Some(realm)),
          )
        }
      },
      test("parsing and encoding is symmetrical") {
        check(HttpGen.authSchemes, Gen.alphaNumericStringBounded(4, 6)) { (scheme, realm) =>
          val header = s"${scheme.name} realm=$realm"
          assertTrue(
            ProxyAuthenticate.fromProxyAuthenticate(ProxyAuthenticate.toProxyAuthenticate(header)) == header,
          )
        }
      },
    )
}
