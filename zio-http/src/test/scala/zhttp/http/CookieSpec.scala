package zhttp.http

import zhttp.internal.HttpGen
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test._

object CookieSpec extends DefaultRunnableSpec {
  def spec = suite("Cookies") {
    suite("response cookies") {
      testM("should encode/decode signed/unsigned cookies with same secret") {
        checkAll(HttpGen.cookies) { case (cookie, s) =>
          s match {
            case Some(secret) => {
              val cookieString = cookie.sign(secret).encode
              assert(Cookie.decodeResponseSignedCookie(cookieString, secret))(isSome(equalTo(cookie))) &&
              assert(
                Cookie.decodeResponseSignedCookie(cookieString, secret).map(_.sign(secret).encode),
              )(
                isSome(equalTo(cookieString)),
              )
            }
            case None         => {
              val cookieString = cookie.encode
              assert(Cookie.decodeResponseCookie(cookieString))(isSome(equalTo(cookie))) &&
              assert(Cookie.decodeResponseCookie(cookieString).map(_.encode))(isSome(equalTo(cookieString)))
            }
          }
        }
      } +
        testM("should not unsign cookies with different secret") {
          checkAll(HttpGen.cookies) { case (cookie, _) =>
            val cookieSigned = cookie.encode
            assert(Cookie.decodeResponseSignedCookie(cookieSigned, "a"))(isNone)
          }
        }
    } +
      suite("request cookies") {
        testM("encode/decode multiple cookies with ZIO Test Gen") {
          checkAll(for {
            name         <- Gen.anyString
            content      <- Gen.anyString
            cookieList   <- Gen.listOf(Gen.const(Cookie(name, content)))
            cookieString <- Gen.const(cookieList.map(x => s"${x.name}=${x.content}").mkString(";"))
          } yield (cookieList, cookieString)) { case (cookies, message) =>
            assert(Cookie.decodeRequestCookie(message))(isSome(equalTo(cookies)))
          }
        }
      }
  }
}
