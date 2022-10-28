package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object IfRangeSpec extends ZIOSpecDefault {

  private val webDateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("If-Range header encoder suite")(
      test("parsing valid eTag value") {
        assertTrue(
          IfRange.toIfRange(""""675af34563dc-tr34"""") ==
            IfRange.ETagValue("675af34563dc-tr34"),
        )
      },
      test("parsing valid date time value") {
        assertTrue(
          IfRange.toIfRange("Wed, 21 Oct 2015 07:28:00 GMT") ==
            IfRange.DateTimeValue(ZonedDateTime.parse("Wed, 21 Oct 2015 07:28:00 GMT", webDateTimeFormatter)),
        )
      },
      test("parsing invalid eTag value") {
        assertTrue(
          IfRange.toIfRange("675af34563dc-tr34") ==
            IfRange.InvalidIfRangeValue,
        )
      },
      test("parsing invalid date time value") {
        assertTrue(
          IfRange.toIfRange("21 Oct 2015 07:28:00") ==
            IfRange.InvalidIfRangeValue,
        )
      },
      test("parsing empty value") {
        assertTrue(
          IfRange.toIfRange("") ==
            IfRange.InvalidIfRangeValue,
        )
      },
      test("transformations are symmetrical") {
        assertTrue(IfRange.fromIfRange(IfRange.toIfRange(""""975af34563dc-tr34"""")) == """"975af34563dc-tr34"""") &&
        assertTrue(
          IfRange.fromIfRange(IfRange.toIfRange("Fri, 28 Oct 2022 01:01:01 GMT")) == "Fri, 28 Oct 2022 01:01:01 GMT",
        )
      },
    )
}
