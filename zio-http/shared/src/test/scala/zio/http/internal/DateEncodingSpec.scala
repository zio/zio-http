package zio.http.internal

import zio.Scope
import zio.test._

object DateEncodingSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suiteAll("DateEncodingSpec") {
      test("valid RFC 1123 date format") {
        val dateLeading0        = "Mon, 01 Jan 2024 00:00:00 GMT"
        val dateWithoutLeading0 = "Mon, 1 Jan 2024 00:00:00 GMT"
        assertTrue(
          DateEncoding.decodeDate(dateLeading0).isDefined
            && DateEncoding.decodeDate(dateWithoutLeading0).isDefined,
        )
      }
      test("invalid date format") {
        val invalidDate = "Invalid Date Format"
        assertTrue(DateEncoding.decodeDate(invalidDate).isEmpty)
      }
    }
}
