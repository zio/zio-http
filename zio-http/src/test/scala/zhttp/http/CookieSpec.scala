package zhttp.http

import zhttp.internal.HttpGen
import zio.test.Assertion.{contains, equalTo, isSome}
import zio.test._

object CookieSpec extends ZIOSpecDefault {
  def spec = suite("Cookies")(
    suite("response cookies")(
      test("encode/decode signed/unsigned cookies with secret") {
        check(HttpGen.cookies) { cookie =>
          val expected = cookie.encode
          val actual   = Cookie.decodeResponseCookie(expected, cookie.secret).map(_.encode)
          assert(actual)(isSome(equalTo(expected)))
        }
      },
    ),
    suite("request cookies")(
      test("encode/decode multiple cookies") {
        check(for {
          name         <- Gen.string
          content      <- Gen.string
          cookieList   <- Gen.listOf(Gen.const(Cookie(name, content)))
          cookieString <- Gen.const(cookieList.map(x => s"${x.name}=${x.content}").mkString(";"))
        } yield (cookieList, cookieString)) { case (cookies, message) =>
          assert(Cookie.decodeRequestCookie(message))(equalTo(cookies))
        }
      },
      test("encode/decode multiple cookies with secret") {
        check(HttpGen.requestCookies) { cookie =>
          val expected = cookie.encode
          val actual   = Cookie.decodeRequestCookie(expected, cookie.secret).map(_.encode)
          assert(actual)(contains(expected))
        }
      },
    ),
  )
}
