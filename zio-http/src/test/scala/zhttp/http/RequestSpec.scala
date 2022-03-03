package zhttp.http

import zio.test.Assertion._
import zio.test._

object RequestSpec extends DefaultRunnableSpec {
  def spec = suite("Request")(
    suite("toString") {
      test("should produce string representation of a request") {
        val r = Request()
        assert(r.toString)(matchesRegex("""Request\(.*\)"""))
      } +
        test("should produce string representation of a parameterized request") {
          val r = Request.ParameterizedRequest(Request(), "TestParam")
          assert(r.toString)(matchesRegex("""ParameterizedRequest\(.*\)"""))
        }
    }
  )
}
