package zio.http.model.headers.values

import java.time.Duration

import zio.Scope
import zio.test._

import zio.http.model.headers.values.AccessControlMaxAge._

object AccessControlMaxAgeSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Acc header suite")(
    test("parsing of invalid AccessControlMaxAge values returns default") {
      assertTrue(toAccessControlMaxAge("") == AccessControlMaxAge.InvalidAccessControlMaxAge) &&
      assertTrue(toAccessControlMaxAge("any string") == AccessControlMaxAge.InvalidAccessControlMaxAge) &&
      assertTrue(toAccessControlMaxAge("-1") == AccessControlMaxAge.InvalidAccessControlMaxAge)
    },
    test("parsing of valid AccessControlMaxAge values") {
      check(Gen.long(0, 1000000)) { long =>
        assertTrue(
          toAccessControlMaxAge(long.toString).seconds.getSeconds().toString == fromAccessControlMaxAge(
            ValidAccessControlMaxAge(Duration.ofSeconds(long)),
          ),
        )
      }
    },
    test("parsing of negative seconds AccessControlMaxAge values returns default") {
      check(Gen.long(-1000000, -1)) { long =>
        assertTrue(
          toAccessControlMaxAge(long.toString).seconds.getSeconds().toString == fromAccessControlMaxAge(
            ValidAccessControlMaxAge(),
          ),
        )
      }
    },
  )
}
