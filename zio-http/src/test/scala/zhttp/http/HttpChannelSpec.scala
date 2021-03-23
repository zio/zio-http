package zhttp.http

import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assert}

object HttpChannelSpec extends DefaultRunnableSpec {
  implicit val canSupportPartial: CanSupportPartial[Any, String] = _ => "NOT_FOUND"
  implicit val canConcatenate: CanConcatenate[String]            = _ == "NOT_FOUND"

  def spec = suite("HttpChannel")(
    suite("flatMap")(
      test("should flatten") {
        val app    = HttpChannel.identity[Int].flatMap(i => HttpChannel.succeed(i + 1))
        val actual = app.eval(0)
        assert(actual)(equalTo(HttpResult.success(1)))
      },
      test("should be stack-safe") {
        val i      = 100000
        val app    = (0 until i).foldLeft(HttpChannel.identity[Int])((i, _) => i.flatMap(c => HttpChannel.Succeed(c + 1)))
        val actual = app.eval(0)
        assert(actual)(equalTo(HttpResult.success(i)))
      },
    ),
    suite("orElse")(
      test("should succeed") {
        val a1     = HttpChannel.succeed(1)
        val a2     = HttpChannel.succeed(2)
        val a      = a1 <> a2
        val actual = a.eval(())
        assert(actual)(equalTo(HttpResult.success(1)))
      },
      test("should fail with first") {
        val a1     = HttpChannel.fail("A")
        val a2     = HttpChannel.succeed("B")
        val a      = a1 <> a2
        val actual = a.eval(())
        assert(actual)(equalTo(HttpResult.failure("A")))
      },
      test("should succeed with second") {
        val a1     = HttpChannel.fail("NOT_FOUND")
        val a2     = HttpChannel.succeed("B")
        val a      = a1 <> a2
        val actual = a.eval(())
        assert(actual)(equalTo(HttpResult.success("B")))
      },
    ),
    suite("fail")(
      test("should fail") {
        val a      = HttpChannel.fail(100)
        val actual = a.eval(1)
        assert(actual)(equalTo(HttpResult.failure(100)))
      },
    ),
    suite("foldM")(
      test("should catch") {
        val a      = HttpChannel.fail(100).foldM(e => HttpChannel.succeed(e + 1), (_: Any) => HttpChannel.succeed(()))
        val actual = a.eval(0)
        assert(actual)(equalTo(HttpResult.success(101)))
      },
    ),
    suite("identity")(
      test("should passthru") {
        val a      = HttpChannel.identity[Int]
        val actual = a.eval(0)
        assert(actual)(equalTo(HttpResult.success(0)))
      },
    ),
    suite("collect")(
      test("should succeed") {
        val a      = HttpChannel.collect[Int] { case 1 => "OK" }
        val actual = a.eval(1)
        assert(actual)(equalTo(HttpResult.success("OK")))
      },
      test("should fail") {
        val a      = HttpChannel.collect[Int] { case 1 => "OK" }
        val actual = a.eval(0)
        assert(actual)(equalTo(HttpResult.failure("NOT_FOUND")))
      },
    ),
  )
}
