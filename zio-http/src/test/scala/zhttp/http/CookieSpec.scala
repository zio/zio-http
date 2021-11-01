package zhttp.http

import zhttp.experiment.internal.HttpGen
import zio.test.Assertion.{equalTo, isRight}
import zio.test._

object CookieSpec extends DefaultRunnableSpec {
  def spec = suite("Cookies")(
    suite("encode/decode cookies")(
      testM("encode/decode cookies with ZIO Test Gen") {
        checkAll(HttpGen.cookies) { cookie =>
          val cookieString = cookie.encode
          assert(Cookie.decode(cookieString))(isRight(equalTo(cookie))) &&
          assert(Cookie.decode(cookieString).map(_.encode))(isRight(equalTo(cookieString)))
        }
      },
    ),
    suite("encode/decode multiple cookies")(
      testM("encode/decode multiple cookies with ZIO Test Gen") {
        checkAll(HttpGen.reqCookies) { cookie =>
          assert(Cookie.decodeMultiple(cookie._2))(isRight(equalTo(cookie._1))) &&
          assert(Cookie.decodeMultiple(cookie._2).map(x => x.map(_.encode).mkString(";")))(isRight(equalTo(cookie._2)))
        }
      },
    ),
  )

}
