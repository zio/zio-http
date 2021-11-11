package zhttp.http

import zhttp.experiment.internal.HttpGen
import zio.test.Assertion.{equalTo, isRight}
import zio.test._

object CookieSpec extends DefaultRunnableSpec {
  def spec = suite("Cookies") {
    suite("response cookies") {
      testM("encode/decode cookies with ZIO Test Gen") {
        checkAll(HttpGen.cookies) { cookie =>
          val cookieString = cookie.encode
          assert(Cookie.decodeResponseCookie(cookieString))(isRight(equalTo(cookie))) &&
          assert(Cookie.decodeResponseCookie(cookieString).map(_.encode))(isRight(equalTo(cookieString)))
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
            assert(Cookie.decodeRequestCookie(message))(isRight(equalTo(cookies)))
          }
        }
      }
  }
}
