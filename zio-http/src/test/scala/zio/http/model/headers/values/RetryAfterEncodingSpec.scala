package zio.http.model.headers.values

import zio.Scope
import zio.http.internal.HttpGen
import zio.http.model.headers.values.ExpiresSpec.formatter
import zio.test.{Gen, Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check}

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}

object RetryAfterEncodingSpec extends ZIOSpecDefault {
  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Retry-After header encoder suite")(
    test("parsing invalid retry-after values") {
      assertTrue(RetryAfter.toRetryAfter("") == RetryAfter.InvalidRetryAfter) &&
      assertTrue(RetryAfter.toRetryAfter("-1") == RetryAfter.InvalidRetryAfter) &&
      assertTrue(RetryAfter.toRetryAfter("21 Oct 2015 07:28:00 GMT") == RetryAfter.InvalidRetryAfter)
    },
    test("parsing valid Retry After values") {
      assertTrue(
        RetryAfter.toRetryAfter("Wed, 21 Oct 2015 07:28:00 GMT") == RetryAfter.WebDate(
          ZonedDateTime.parse("Wed, 21 Oct 2015 07:28:00 GMT", formatter),
        ) && RetryAfter.toRetryAfter("20") == RetryAfter.DelaySeconds(20),
      )
    },
    suite("Encoding header value transformation should be symmetrical")(
      test("date format") {
        check(Gen.zonedDateTime(ZonedDateTime.now(), ZonedDateTime.now().plusDays(365))) { date =>
          val zone = ZoneId.of("Australia/Sydney")
          assertTrue(
            RetryAfter.fromRetryAfter(RetryAfter.toRetryAfter(date.withZoneSameLocal(zone).format(formatter))) == date
              .withZoneSameLocal(zone)
              .format(formatter),
          )
        }
      },
      test("seconds format") {
        check(Gen.int(10, 1000)) { seconds =>
          assertTrue(RetryAfter.fromRetryAfter(RetryAfter.toRetryAfter(seconds.toString)) == seconds.toString)
        }
      },
    ),
  )
}
