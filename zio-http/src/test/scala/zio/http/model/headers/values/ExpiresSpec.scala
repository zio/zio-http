package zio.http.model.headers.values

import zio.Scope
import zio.http.model.headers.values.Expires.ValidExpires
import zio.test._

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

object ExpiresSpec extends ZIOSpecDefault {

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Expires header suite")(
    test("parsing of invalid expires values") {
      assertTrue(Expires.toExpires("") == Expires.InvalidExpires) &&
      assertTrue(Expires.toExpires("any string") == Expires.InvalidExpires) &&
      assertTrue(Expires.toExpires("Wed 21 Oct 2015 07:28:00") == Expires.InvalidExpires) &&
      assertTrue(Expires.toExpires("21 Oct 2015 07:28:00 GMT") == Expires.InvalidExpires)
      assertTrue(Expires.toExpires("Wed 21 Oct 2015 07:28:00 GMT") == Expires.InvalidExpires)
    },
    test("parsing of valid Expires values") {
      assertTrue(
        Expires.toExpires("Wed, 21 Oct 2015 07:28:00 GMT") == Expires.ValidExpires(
          ZonedDateTime.parse("Wed, 21 Oct 2015 07:28:00 GMT", formatter),
        ),
      )
    },
    test("parsing and encoding is symmetrical") {
      check(Gen.zonedDateTime(ZonedDateTime.now(), ZonedDateTime.now().plusDays(365))) { date =>
        val zone = ZoneId.of("Australia/Sydney")
        assertTrue(
          Expires
            .toExpires(Expires.fromExpires(ValidExpires(date.withZoneSameLocal(zone))))
            .value == date.withZoneSameLocal(zone).format(formatter),
        )
      }
    },
  )
}
