package zhttp.http.cookie

import zio.test._

object CookieSpec extends ZIOSpecDefault {
  override def spec =
    suite("CookieSpec")(
      suite("getter")(
        test("source is Unit") {
          val cookieGen = for {
            name    <- Gen.alphaNumericString
            content <- Gen.alphaNumericString
          } yield (name, content) -> Cookie(name, content)
          check(cookieGen) { case (name, content) -> cookie =>
            assertTrue(cookie.content == content) && assertTrue(cookie.name == name)
          }
        },
        test("Server") {
          val cookieGen = for {
            name    <- Gen.alphaNumericString
            content <- Gen.alphaNumericString
          } yield (name, content) -> Cookie(name, content, Cookie.Server)
          check(cookieGen) { case (name, content) -> cookie =>
            assertTrue(cookie.content == content) && assertTrue(cookie.name == name)
          }
        },
        test("Client") {
          val cookieGen = for {
            name     <- Gen.alphaNumericString
            content  <- Gen.alphaNumericString
            response <- for {
              expires    <- Gen.option(Gen.instant)
              domain     <- Gen.option(Gen.alphaNumericString)
              path       <- Gen.option(Gen.alphaNumericString)
              maxAge     <- Gen.option(Gen.finiteDuration)
              sameSite   <- Gen.option(Gen.fromIterable(Cookie.SameSite.values))
              isSecure   <- Gen.boolean
              isHttpOnly <- Gen.boolean
            } yield Cookie.Client(expires, domain, path, isSecure, isHttpOnly, maxAge, sameSite)
          } yield (name, content) -> Cookie(name, content, response)

          check(cookieGen) { case (name, content) -> cookie =>
            assertTrue(cookie.content == content) &&
            assertTrue(cookie.name == name) &&
            assertTrue(cookie.expires == cookie.target.expires) &&
            assertTrue(cookie.domain == cookie.target.domain) &&
            assertTrue(cookie.path == cookie.target.path) &&
            assertTrue(cookie.maxAge == cookie.target.maxAge) &&
            assertTrue(cookie.sameSite == cookie.target.sameSite) &&
            assertTrue(cookie.isSecure == cookie.target.isSecure) &&
            assertTrue(cookie.isHttpOnly == cookie.target.isHttpOnly)
          }
        },
      ),
    )
}
