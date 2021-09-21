package zhttp.http

import zio.test.Assertion._
import zio.test._
import zio.test.DefaultRunnableSpec

object SchemeSpec extends DefaultRunnableSpec{
def spec = suite("Scheme")(
  suite("asString")(
    test("HTTP")(assert(Scheme.asString(Scheme.HTTP))(equalTo("http"))),
    test("HTTPS")(assert(Scheme.asString(Scheme.HTTPS))(equalTo("https")))
  )
)
}
