package zhttp.http

import zio.test.Assertion._
import zio.test._

object URLSpec extends DefaultRunnableSpec {
  def spec = suite("URL")(suite("fromString")(test("Should Handle invalid url String with restricted chars") {
    assert(URL.fromString("http://mw1.google.com/$[level]/r$[y]_c$[x].jpg"))(isLeft)
  }))
}
