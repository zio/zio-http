package zhttp.http

import zhttp.experiment.internal.HttpGen
import zio.test.Assertion.{equalTo, isRight}
import zio.test._

object CookieSpec extends DefaultRunnableSpec {
  def spec = suite("Cookies")(
    suite("encode/decode cookies")(
      testM("encode/decode cookies with ZIO Test Gen") {
        checkAll(HttpGen.cookies) { cookie =>
          val cookieString = cookie.asString
          assert(Cookie.parse(cookieString))(isRight(equalTo(cookie))) &&
          assert(Cookie.parse(cookieString).map(_.asString))(isRight(equalTo(cookieString)))
        }
      },
    ),
  )
}
