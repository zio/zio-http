package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

import java.time.{ZoneOffset, ZonedDateTime}

object IfModifiedSinceSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("IfModifiedSinceSpec")(
    test("IfModifiedSince should be parsed correctly") {
      val ifModifiedSince = IfModifiedSince.toIfModifiedSince("Sun, 06 Nov 1994 08:49:37 GMT")
      assertTrue(
        ifModifiedSince == IfModifiedSince.ModifiedSince(ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, ZoneOffset.UTC)),
      )
    },
    test("IfModifiedSince should be parsed correctly with invalid value") {
      val ifModifiedSince = IfModifiedSince.toIfModifiedSince("Sun, 06 Nov 1994 08:49:37")
      assertTrue(ifModifiedSince == IfModifiedSince.InvalidModifiedSince)
    },
    test("IfModifiedSince should render correctly a valid date") {
      assertTrue(
        IfModifiedSince.fromIfModifiedSince(
          IfModifiedSince.ModifiedSince(ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, ZoneOffset.UTC)),
        ) == "Sun, 6 Nov 1994 08:49:37 GMT",
      )
    },
  )

}
