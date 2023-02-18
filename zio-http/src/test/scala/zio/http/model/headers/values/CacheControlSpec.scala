package zio.http.model.headers.values

import zio.Scope
import zio.test._

import zio.http.internal.HttpGen

object CacheControlSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("CacheControl suite")(
    suite("CacheControl header value transformation should be symmetrical")(
      test("single value") {
        check(HttpGen.cacheControlSingleValueWithSeconds) { value =>
          assertTrue(CacheControl.toCacheControl(CacheControl.fromCacheControl(value)) == value)
        }
      },
      test("multiple values") {
        check(HttpGen.cacheControl) { value =>
          assertTrue(CacheControl.toCacheControl(CacheControl.fromCacheControl(value)) == value)
        }
      },
    ),
  )
}
