package zio.http.model.headers

import zio.http.internal.HttpGen
import zio.http.model.headers.HeaderValue.MaxForwards
import zio.http.model.headers.HeaderValue.MaxForwards.{InvalidMaxForwardsValue, MaxForwardsValue}
import zio.http.model.headers.values.Origin.{InvalidOriginValue, OriginNull, OriginValue}
import zio.http.{Path, QueryParams}
import zio.test._

object MaxForwardsSpec extends ZIOSpecDefault {
  override def spec = suite("Max Forwards header suite")(
    test("Correct from Max-Forwards headers") {
      assertTrue(MaxForwards.toMaxForwards("10") == MaxForwardsValue(10))
      assertTrue(MaxForwards.toMaxForwards("0") == MaxForwardsValue(0))
      assertTrue(MaxForwards.toMaxForwards("99") == MaxForwardsValue(99))
    },
    test("Incorrect from Max-Forwards headers") {
      assertTrue(MaxForwards.toMaxForwards("fail") == InvalidMaxForwardsValue)
      assertTrue(MaxForwards.toMaxForwards("-10") == InvalidMaxForwardsValue)
      assertTrue(MaxForwards.toMaxForwards("") == InvalidMaxForwardsValue)
    },
    test("From Max-Forwards") {
      assertTrue(MaxForwards.fromMaxForwards(MaxForwardsValue(200)) == "200")
      assertTrue(MaxForwards.fromMaxForwards(MaxForwardsValue(1)) == "1")
      assertTrue(MaxForwards.fromMaxForwards(InvalidMaxForwardsValue) == "")
    },
    test("parsing and encoding is symmetrical") {
      check(Gen.int(0, 9000)) { int =>
        assertTrue(MaxForwards.fromMaxForwards(MaxForwards.toMaxForwards(int.toString)) == int.toString)
      }
    },
  )
}
