package zhttp.http

import zio.test.Assertion.{equalTo, isLeft, isRight, isSome}
import zio.test.{assert, assertM, DefaultRunnableSpec, ZSpec}

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
      suite("asEffect")(
        testM("should succeed") {
          val actual = HttpResult.success(1).asOut.asEffect.either
          assertM(actual)(isRight(equalTo(1)))
        },
        testM("should fail") {
          val actual = HttpResult.failure(1).asOut.asEffect.either
          assertM(actual)(isLeft(isSome(equalTo(1))))
        },
      ),
      suite("flatMap")(
        testM("should succeed") {
          val res    = HttpResult.success(1) *> HttpResult.success(2)
          val actual = res.asOut.asEffect.either
          assertM(actual)(isRight(equalTo(2)))
        },
        testM("should fail") {
          val res    = HttpResult.failure(1) *> HttpResult.success(2)
          val actual = res.asOut.asEffect.either
          assertM(actual)(isLeft(isSome(equalTo(1))))
        },
      ),
      suite("defaultWith")(
        test("should succeed") {
          val res    = HttpResult.success(1).defaultWith(HttpResult.success(2))
          val actual = res.asOut
          assert(actual)(equalTo(HttpResult.success(1)))
        },
        test("should fail") {
          val res    = HttpResult.failure(1).defaultWith(HttpResult.success(2))
          val actual = res.asOut
          assert(actual)(equalTo(HttpResult.failure(1)))
        },
        test("should succeed with default") {
          val res    = HttpResult.empty.defaultWith(HttpResult.success(2))
          val actual = res.asOut
          assert(actual)(equalTo(HttpResult.success(2)))
        },
        test("should succeed with default") {
          val res    = (HttpResult.success(1) *> HttpResult.empty).defaultWith(HttpResult.success(2))
          val actual = res.asOut
          assert(actual)(equalTo(HttpResult.success(2)))
        },
        test("should fail with default") {
          val res    = (HttpResult.success(1) *> HttpResult.empty).defaultWith(HttpResult.failure(2))
          val actual = res.asOut
          assert(actual)(equalTo(HttpResult.failure(2)))
        },
      ),
    )
}
