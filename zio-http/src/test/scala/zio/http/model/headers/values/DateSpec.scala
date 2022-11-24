package zio.http.model.headers.values

import zio.Scope
import zio.http.model.headers.values.Date.InvalidDate
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object DateSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Date suite")(
    suite("Date header value transformation should be symmetrical")(
      test("Date rendering should be reversible") {
        val value = "Wed, 21 Oct 2015 07:28:00 GMT"
        assertTrue(Date.fromDate(Date.toDate(value)) == value)
      },
      test("Date parsing should fail for invalid date") {
        val value = "Wed, 21 Oct 20 07:28:00"
        assertTrue(Date.toDate(value) == InvalidDate)
      },
      test("Date parsing should fail for empty date") {
        val value = ""
        assertTrue(Date.toDate(value) == InvalidDate)
      },
    ),
  )
}
