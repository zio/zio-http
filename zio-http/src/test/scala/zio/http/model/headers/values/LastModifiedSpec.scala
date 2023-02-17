package zio.http.model.headers.values

import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

import zio.http.model.headers.values.LastModified.LastModifiedDateTime

object LastModifiedSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("LastModified spec")(
    test("LastModifiedDateTime") {
      val dateTime     = ZonedDateTime.parse("Sun, 06 Nov 1994 08:49:37 GMT", DateTimeFormatter.RFC_1123_DATE_TIME)
      val lastModified = LastModified.LastModifiedDateTime(dateTime)
      assertTrue(LastModified.fromLastModified(lastModified) == "Sun, 6 Nov 1994 08:49:37 GMT")
    },
    test("LastModified  should be parsed correctly with invalid value") {
      val lastModified = LastModified.toLastModified("Mon, 07 Nov 1994 08:49:37")
      assertTrue(lastModified == LastModified.InvalidLastModified)
    },
    test("LastModified should render correctly a valid date") {
      assertTrue(
        LastModified.fromLastModified(
          LastModifiedDateTime(ZonedDateTime.of(1994, 11, 7, 8, 49, 37, 0, ZoneOffset.UTC)),
        ) == "Mon, 7 Nov 1994 08:49:37 GMT",
      )
    },
  )
}
