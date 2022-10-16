package zio.http.model.headers.values

import zio.Scope
import zio.http.model.headers.values.AccessControlMaxAge._
import zio.test._

import scala.concurrent.duration.{Duration, SECONDS}

object AccessControlMaxAgeSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Acc header suite")(
    test("parsing of invalid AccessControlMaxAge values returns default") {
      assertTrue(toAccessControlMaxAge("") == AccessControlMaxAge.ValidAccessControlMaxAge()) &&
      assertTrue(toAccessControlMaxAge("any string") == AccessControlMaxAge.ValidAccessControlMaxAge()) &&
      assertTrue(toAccessControlMaxAge("-1") == AccessControlMaxAge.ValidAccessControlMaxAge())
    },
    test("parsing of valid AccessControlMaxAge values") {
      check(Gen.long(0, 1000000)) { long =>
        assertTrue(
          toAccessControlMaxAge(long.toString).seconds == fromAccessControlMaxAge(
            ValidAccessControlMaxAge(Duration(long, SECONDS)),
          ),
        )
      }
    },
    test("parsing of negative seconds AccessControlMaxAge values returns default") {
      check(Gen.long(-1000000, -1)) { long =>
        assertTrue(
          toAccessControlMaxAge(long.toString).seconds == fromAccessControlMaxAge(ValidAccessControlMaxAge()),
        )
      }
    },
  )
}
