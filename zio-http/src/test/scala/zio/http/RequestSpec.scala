package zio.http

import zio.http.internal.HttpGen
import zio.test.Assertion._
import zio.test._

object RequestSpec extends ZIOSpecDefault {
  def spec = suite("Request")(
    suite("toString") {
      test("should produce string representation of a request") {
        check(HttpGen.request) { req =>
          assert(req.toString)(
            equalTo(s"Request(${req.version}, ${req.method}, ${req.url}, ${req.headers})"),
          )
        }
      }
    },
  )
}
