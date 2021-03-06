package zhttp.http

import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assert}

object HttpSpec extends DefaultRunnableSpec {
  implicit val canSupportPartial: CanSupportPartial[Any, String] = _ => "NOT_FOUND"
  implicit val canConcatenate: CanConcatenate[String]            = _ == "NOT_FOUND"

  def spec = suite("Http")(
    suite("flatMap")(
      test("should flatten") {
        val app    = Http.identity[Int].flatMap(i => Http.succeed(i + 1))
        val actual = app.eval(0)
        assert(actual)(equalTo(HttpResult.success(1)))
      },
      test("should be stack-safe") {
        val i      = 100000
        val app    = (0 until i).foldLeft(Http.identity[Int])((i, _) => i.flatMap(c => Http.Succeed(c + 1)))
        val actual = app.eval(0)
        assert(actual)(equalTo(HttpResult.success(i)))
      },
    ),
    suite("orElse")(
      test("should succeed") {
        val a1     = Http.succeed(1)
        val a2     = Http.succeed(2)
        val a      = a1 <> a2
        val actual = a.eval(())
        assert(actual)(equalTo(HttpResult.success(1)))
      },
      test("should fail with first") {
        val a1     = Http.fail("A")
        val a2     = Http.succeed("B")
        val a      = a1 <> a2
        val actual = a.eval(())
        assert(actual)(equalTo(HttpResult.failure("A")))
      },
      test("should succeed with second") {
        val a1     = Http.fail("NOT_FOUND")
        val a2     = Http.succeed("B")
        val a      = a1 <> a2
        val actual = a.eval(())
        assert(actual)(equalTo(HttpResult.success("B")))
      },
    ),
    suite("fail")(
      test("should fail") {
        val a      = Http.fail(100)
        val actual = a.eval(1)
        assert(actual)(equalTo(HttpResult.failure(100)))
      },
    ),
    suite("foldM")(
      test("should catch") {
        val a      = Http.fail(100).foldM(e => Http.succeed(e + 1), (_: Any) => Http.succeed(()))
        val actual = a.eval(0)
        assert(actual)(equalTo(HttpResult.success(101)))
      },
    ),
    suite("identity")(
      test("should passthru") {
        val a      = Http.identity[Int]
        val actual = a.eval(0)
        assert(actual)(equalTo(HttpResult.success(0)))
      },
    ),
    suite("collect")(
      test("should succeed") {
        val a      = Http.collect[Int] { case 1 => "OK" }
        val actual = a.eval(1)
        assert(actual)(equalTo(HttpResult.success("OK")))
      },
      test("should fail") {
        val a      = Http.collect[Int] { case 1 => "OK" }
        val actual = a.eval(0)
        assert(actual)(equalTo(HttpResult.failure("NOT_FOUND")))
      },
    ),
  )
}
