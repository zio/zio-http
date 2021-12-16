package zhttp.http

import zhttp.internal.HttpGen
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test._

object CookieSpec extends DefaultRunnableSpec {
  def spec = suite("Cookies") {
    suite("response cookies") {
      testM("encode/decode cookies with ZIO Test Gen") {
        checkAll(HttpGen.cookies) { case (cookie, _) =>
          val cookieString = cookie.encode
          assert(Cookie.decodeResponseCookie(cookieString))(isSome(equalTo(cookie))) &&
          assert(Cookie.decodeResponseCookie(cookieString).map(_.encode))(isSome(equalTo(cookieString)))
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
      } +
      suite("sign/unsign cookies") {
        testM("should sign/unsign cookies with same secret") {
          checkAll(HttpGen.cookies) { case (cookie, secret) =>
            val cookieSigned = cookie.withSign(secret).unSign(secret)
            assert(cookieSigned)(isSome(equalTo(cookie)))
          }
        } +
          testM("should not unsign cookies with different secret") {
            checkAll(HttpGen.cookies) { case (cookie, secret) =>
              val cookieSigned = cookie.withSign(secret).unSign("sec")
              assert(cookieSigned)(isNone)
            }
          }
      }
  }
}
