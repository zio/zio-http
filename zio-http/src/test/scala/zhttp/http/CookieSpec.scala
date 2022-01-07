package zhttp.http

import zhttp.internal.HttpGen
import zio.test._

object CookieSpec extends DefaultRunnableSpec {
  def spec = suite("Cookies") {
    suite("response cookies") {
      testM("encode/decode signed/unsigned cookies with secret") {
        checkAll(HttpGen.cookies) { cookie =>
          val cookieString = cookie.encode
          assertTrue(Cookie.decodeResponseCookie(cookieString, cookie.secret).contains(cookie)) &&
          assertTrue(Cookie.decodeResponseCookie(cookieString, cookie.secret).map(_.encode).contains(cookieString))
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
            assertTrue(Cookie.decodeRequestCookie(message).contains(cookies))
          }
        }
      }
  }
}
