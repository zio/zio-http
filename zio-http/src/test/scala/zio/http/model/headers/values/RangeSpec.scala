package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object RangeSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Range suite")(
    test("parsing of invalid Range values") {
      assertTrue(Range.toRange("") == Range.InvalidRange) &&
      assertTrue(Range.toRange("something") == Range.InvalidRange)
    },
    test("parsing and encoding is symmetrical") {
      val value = Range.SingleRange("bytes", 0L, Some(100L))
      assertTrue(Range.toRange(Range.fromRange(value)) == value)
    },
    test("parsing of valid Range values") {
      assertTrue(Range.toRange("bytes=0-100") == Range.SingleRange("bytes", 0L, Some(100L))) &&
      assertTrue(Range.toRange("bytes=0-") == Range.SingleRange("bytes", 0L, None)) &&
      assertTrue(
        Range.toRange("bytes=0-100,200-300") == Range.MultipleRange("bytes", List((0L, Some(100L)), (200L, Some(300L)))),
      ) &&
      assertTrue(
        Range.toRange("bytes=0-100,200-") == Range.MultipleRange("bytes", List((0L, Some(100L)), (200L, None))),
      ) &&
      assertTrue(Range.toRange("bytes=-100") == Range.SuffixRange("bytes", 100L))
    },
    test("render Range values") {
      assertTrue(Range.fromRange(Range.SingleRange("bytes", 0L, Some(100L))) == "bytes=0-100") &&
      assertTrue(Range.fromRange(Range.SingleRange("bytes", 0L, None)) == "bytes=0-") &&
      assertTrue(
        Range.fromRange(
          Range.MultipleRange("bytes", List((0L, Some(100L)), (200L, Some(300L)))),
        ) == "bytes=0-100,200-300",
      ) &&
      assertTrue(
        Range.fromRange(Range.MultipleRange("bytes", List((0L, Some(100L)), (200L, None)))) == "bytes=0-100,200-",
      ) &&
      assertTrue(Range.fromRange(Range.SuffixRange("bytes", 100L)) == "bytes=-100")
    },
  )
}
