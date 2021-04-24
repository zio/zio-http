package zhttp.http

import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

object HttpResultSpec extends DefaultRunnableSpec {
  def spec: ZSpec[Environment, Failure] =
    suite("HttpResult")(
      suite("catchAll") {
        test("should handle error") {
          val actual =
            HttpResult.failure(1).flatMapError(e => HttpResult.success(e + 1)).asOut
          assert(actual)(equalTo(HttpResult.success(2)))
        }
      },
      suite("catchAll") {
        test("should capture nested error") {
          val a = HttpResult
            .success(0)
            .flatMap(_ => HttpResult.failure("FAIL"))
            .flatMapError(e => HttpResult.failure("FOLD_" + e))

          val actual = a.asOut
          assert(actual)(equalTo(HttpResult.failure("FOLD_FAIL")))
        }
      },
    )
}
