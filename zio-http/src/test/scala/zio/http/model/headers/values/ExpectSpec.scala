package zio.http.model.headers.values

import zio.Scope
import zio.test._

object ExpectSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("Expect header suite")(
      test("parse valid value") {
        assertTrue(Expect.toExpect("100-continue") == Expect.ExpectValue)
      },
      test("parse invalid value") {
        assertTrue(Expect.toExpect("") == Expect.InvalidExpectValue) &&
        assertTrue(Expect.toExpect("200-ok") == Expect.InvalidExpectValue)
      },
    )
}
