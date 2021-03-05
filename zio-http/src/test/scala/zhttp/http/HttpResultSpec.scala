package zhttp.http

import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

object HttpResultSpec extends DefaultRunnableSpec {
  def spec: ZSpec[Environment, Failure] =
    suite("HttpResult")(
      suite("catchAll") {
        test("should handle error") {
          val actual =
            HttpResult.failure(1).catchAll(e => HttpResult.success(e + 1)).evaluate
          assert(actual)(equalTo(HttpResult.success(2)))
        }
      },
      suite("catchAll") {
        test("should capture nested error") {
          val a = HttpResult
            .success(0)
            .flatMap(_ => HttpResult.failure("FAIL"))
            .catchAll(e => HttpResult.failure("FOLD_" + e))

          val actual = a.evaluate
          assert(actual)(equalTo(HttpResult.failure("FOLD_FAIL")))
        }
      },
    )
}
