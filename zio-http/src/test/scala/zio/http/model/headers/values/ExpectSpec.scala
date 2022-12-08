package zio.http.model.headers.values

import zio.Scope
import zio.http.api.HttpCodec.expectCodec
import zio.test._

object ExpectSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("Expect header suite")(
      test("parse valid value") {
        assertTrue(expectCodec.decode("100-continue").map(Expect.toExpect) == Right(Expect.ExpectValue)) &&
        assertTrue(Expect.fromExpect(Expect.ExpectValue) == "100-continue")
      },
      test("parse invalid value") {
        assertTrue(expectCodec.decode("").map(Expect.toExpect).isLeft) &&
        assertTrue(expectCodec.decode("200-ok").map(Expect.toExpect).isLeft) &&
        assertTrue(Expect.fromExpect(Expect.InvalidExpectValue).isEmpty)
      },
    )
}
