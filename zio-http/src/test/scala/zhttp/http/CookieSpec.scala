package zhttp.http

import zhttp.experiment.internal.HttpGen
import zio.random.Random
import zio.test.Assertion.{equalTo, isRight}
import zio.test._

import scala.concurrent.duration.{Duration, FiniteDuration, SECONDS}

object CookieSpec extends DefaultRunnableSpec {
  def spec = suite("Cookies")(
    suite("encode/decode cookies")(
      testM("encode/decode cookies with ZIO Test Gen") {
        val genPath                                    = Gen.option(Gen.fromIterable(List(!!, Path(""), !! / "Path")))
        val gMaxAge: Gen[Random, Some[FiniteDuration]] =
          for {
            maxAge <- Gen.anyFiniteDuration
          } yield Some(Duration(maxAge.getSeconds, SECONDS))

        val genCookies = HttpGen.cookies(genPath, gMaxAge)

        checkAll(genCookies) { cookie =>
          val cookieString = cookie.asString

          assert(Cookie.parse(cookieString))(isRight(equalTo(cookie))) &&
          assert(Cookie.parse(cookieString).map(_.asString))(isRight(equalTo(cookieString)))
        }
      },
    ),
  )
}
