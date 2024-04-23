package zio.http.netty

import java.time.ZonedDateTime

import zio._
import zio.test.{Spec, TestAspect, TestEnvironment, assertCompletes}

import zio.http.ZIOHttpSpec
import zio.http.internal.DateEncoding

object CachedDateHeaderSpec extends ZIOHttpSpec {
  private val dateHeaderCache = CachedDateHeader.default

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("CachedDateHeader")(
      test("yields the same date header value as DateEncoding") {
        val f = ZIO.suspendSucceed {
          val uncached = DateEncoding.default.encodeDate(ZonedDateTime.now())
          val cached   = dateHeaderCache.get()

          ZIO
            .fail(
              new Exception(
                s"Mismatch in cached and uncached date header value:\n\tuncached: $uncached\n\tcached: $cached",
              ),
            )
            .unless(uncached == cached)
        }
          .delay(5.milli)
          .retryN(1)

        ZIO.foreachDiscard((1 to 300).toList)(_ => f) *> assertCompletes
      }
        @@ TestAspect.withLiveClock,
    )
}
