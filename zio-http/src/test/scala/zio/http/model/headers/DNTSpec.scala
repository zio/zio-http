package zio.http.model.headers

import zio.Scope
import zio.http.model.headers.values.DNT
import zio.http.model.headers.values.DNT.{
  InvalidDNTValue,
  NotSpecifiedDNTValue,
  TrackingAllowedDNTValue,
  TrackingNotAllowedDNTValue,
}
import zio.test._

object DNTSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("DNT header suite")(
    test("parse DNT headers") {
      assertTrue(DNT.toDNT("1") == TrackingAllowedDNTValue)
      assertTrue(DNT.toDNT("0") == TrackingNotAllowedDNTValue)
      assertTrue(DNT.toDNT("null") == NotSpecifiedDNTValue)
      assertTrue(DNT.toDNT("test") == InvalidDNTValue)
    },
    test("encode DNT to String") {
      assertTrue(DNT.fromDNT(TrackingAllowedDNTValue) == "1")
      assertTrue(DNT.fromDNT(TrackingNotAllowedDNTValue) == "0")
      assertTrue(DNT.fromDNT(NotSpecifiedDNTValue) == "null")
      assertTrue(DNT.fromDNT(InvalidDNTValue) == "")
    },
    test("parsing and encoding is symmetrical") {
      assertTrue(DNT.fromDNT(DNT.toDNT("1")) == "1")
      assertTrue(DNT.fromDNT(DNT.toDNT("0")) == "0")
      assertTrue(DNT.fromDNT(DNT.toDNT("null")) == "null")
      assertTrue(DNT.fromDNT(DNT.toDNT("")) == "")
    },
  )
}
