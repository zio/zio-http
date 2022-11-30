package zio.http.model.headers.values

import zio.Scope
import zio.http.api.HttpCodec.dntCodec
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
      assertTrue(dntCodec.decode("1").map(DNT.toDNT) == Right(TrackingNotAllowedDNTValue))
      assertTrue(dntCodec.decode("0").map(DNT.toDNT) == Right(TrackingAllowedDNTValue))
      assertTrue(dntCodec.decode("null").map(DNT.toDNT) == Right(NotSpecifiedDNTValue))
      assertTrue(dntCodec.decode("test").map(DNT.toDNT).isLeft)
    },
    test("encode DNT to String") {
      assertTrue(DNT.fromDNT(TrackingAllowedDNTValue) == Right("1"))
      assertTrue(DNT.fromDNT(TrackingNotAllowedDNTValue) == Right("0"))
      assertTrue(DNT.fromDNT(NotSpecifiedDNTValue) == Right("null"))
      assertTrue(DNT.fromDNT(InvalidDNTValue) == Right("invalid"))
    },
    test("parsing and encoding is symmetrical") {

      assertTrue(dntCodec.decode("1").map(DNT.toDNT).flatMap(DNT.fromDNT) == Right("1"))
      assertTrue(dntCodec.decode("0").map(DNT.toDNT).flatMap(DNT.fromDNT) == Right("0"))
      assertTrue(dntCodec.decode("null").map(DNT.toDNT).flatMap(DNT.fromDNT) == Right(null))
      assertTrue(dntCodec.decode("").map(DNT.toDNT).flatMap(DNT.fromDNT).isLeft)
    },
  )
}
