package zhttp.http

import zhttp.internal.HttpGen
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test._

object CookieSpec extends DefaultRunnableSpec {
  def spec = suite("Cookies") {
    suite("response cookies") {
      testM("encode/decode signed/unsigned nonEmptyCookies") {
        checkAll(HttpGen.nonEmptyCookies) { cookie =>
          val cookieString = cookie.encode
          assert(Cookie.decodeResponseCookie(cookieString, cookie.secret))(isSome(equalTo(cookie))) &&
          assert(Cookie.decodeResponseCookie(cookieString, cookie.secret).map(_.encode))(isSome(equalTo(cookieString)))
        }
      } + testM("encode/decode signed/unsigned emptyCookies") {
        checkAll(HttpGen.emptyCookies) { cookie =>
          val cookieString = cookie.encode
          assert(Cookie.decodeResponseCookie(cookieString, cookie.secret))(isNone) &&
          assert(Cookie.decodeResponseCookie(cookieString, cookie.secret).map(_.encode))(isNone)
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
