package zhttp.http

import zhttp.internal.HttpGen
import zio.test.Assertion.{equalTo, isSome}
import zio.test._

object CookieSpec extends DefaultRunnableSpec {
  def spec = suite("Cookies") {
    suite("response cookies") {
      test("encode/decode cookies with ZIO Test Gen") {
        checkAll(HttpGen.cookies) { cookie =>
          val cookieString = cookie.encode
          assert(Cookie.decodeResponseCookie(cookieString))(isSome(equalTo(cookie))) &&
          assert(Cookie.decodeResponseCookie(cookieString).map(_.encode))(isSome(equalTo(cookieString)))
        }
      }
    } +
      suite("request cookies") {
        test("encode/decode multiple cookies with ZIO Test Gen") {
          checkAll(for {
            name         <- Gen.string
            content      <- Gen.string
            cookieList   <- Gen.listOf(Gen.const(Cookie(name, content)))
            cookieString <- Gen.const(cookieList.map(x => s"${x.name}=${x.content}").mkString(";"))
          } yield (cookieList, cookieString)) { case (cookies, message) =>
            assert(Cookie.decodeRequestCookie(message))(isSome(equalTo(cookies)))
          }
        }
      }
  }
}
