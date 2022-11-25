package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

import java.time.{ZoneOffset, ZonedDateTime}

object IfUnmodifiedSinceSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("IfUnmodifiedSince suite")(
    test("IfUnmodifiedSince should be parsed correctly") {
      val ifModifiedSince = IfUnmodifiedSince.toIfUnmodifiedSince("Mon, 07 Nov 1994 08:49:37 GMT")
      assertTrue(
        ifModifiedSince == IfUnmodifiedSince.UnmodifiedSince(
          ZonedDateTime.of(1994, 11, 7, 8, 49, 37, 0, ZoneOffset.UTC),
        ),
      )
    },
    test("IfUnmodifiedSince should be parsed correctly with invalid value") {
      val ifModifiedSince = IfUnmodifiedSince.toIfUnmodifiedSince("Mon, 07 Nov 1994 08:49:37")
      assertTrue(ifModifiedSince == IfUnmodifiedSince.InvalidUnmodifiedSince)
    },
    test("IfUnmodifiedSince should render correctly a valid date") {
      assertTrue(
        IfUnmodifiedSince.fromIfUnmodifiedSince(
          IfUnmodifiedSince.UnmodifiedSince(ZonedDateTime.of(1994, 11, 7, 8, 49, 37, 0, ZoneOffset.UTC)),
        ) == "Mon, 7 Nov 1994 08:49:37 GMT",
      )
    },
  )
}
