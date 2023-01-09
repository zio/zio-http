package zio.http

import zio.ZIO
import zio.http.HandlerSpec.{isFailure, isSuccess, suite, test}
import zio.test.Assertion.{dies, equalTo}
import zio.test.{ZIOSpecDefault, assert, assertTrue, assertZIO}

object RouteSpec extends ZIOSpecDefault with HExitAssertion {

  def spec = suite("Route")(
    suite("collectHExit")(
      test("should succeed") {
        val a = Route.collectHExit[Int] { case 1 => HExit.succeed("OK") }
        val actual = a.apply(1)
        assert(actual)(isSuccess(equalTo("OK")))
      },
      test("should fail") {
        val a = Route.collectHExit[Int] { case 1 => HExit.fail("OK") }
        val actual = a.apply(1)
        assert(actual)(isFailure(equalTo("OK")))
      },
      test("should die") {
        val t = new Throwable("boom")
        val a = Route.collectHExit[Int] { case 1 => HExit.die(t) }
        val actual = a.apply(1)
        assert(actual)(isDie(equalTo(t)))
      },
      test("should give empty if the inout is not defined") {
        val a = Route.collectHExit[Int] { case 1 => HExit.succeed("OK") }
        assertTrue(!a.isDefinedAt(0))
      },
    ),
    suite("combine")(
      test("should resolve first") {
        val a = Route.collect[Int] { case 1 => "A" }
        val b = Route.collect[Int] { case 2 => "B" }
        val actual = (a ++ b).apply(1)
        assert(actual)(isSuccess(equalTo("A")))
      },
      test("should resolve second") {
        val a = Route.empty
        val b = Handler.succeed("A").toRoute
        val actual = (a ++ b).apply(())
        assert(actual)(isSuccess(equalTo("A")))
      },
      test("should resolve second") {
        val a = Route.collect[Int] { case 1 => "A" }
        val b = Route.collect[Int] { case 2 => "B" }
        val actual = (a ++ b).apply(2)
        assert(actual)(isSuccess(equalTo("B")))
      },
      test("should not resolve") {
        val a = Route.collect[Int] { case 1 => "A" }
        val b = Route.collect[Int] { case 2 => "B" }
        assertTrue(!(a ++ b).isDefinedAt(3))
      },
      test("should not resolve") {
        val a = Route.empty
        val b = Route.empty
        val c = Route.empty
        assertTrue(!(a ++ b ++ c).isDefinedAt(()))
      },
      test("should fail with second") {
        val a = Route.empty
        val b = Handler.fail(100).toRoute
        val c = Handler.succeed("A").toRoute
        val actual = (a ++ b ++ c).apply(())
        assert(actual)(isFailure(equalTo(100)))
      },
      test("should resolve third") {
        val a = Route.empty
        val b = Route.empty
        val c = Handler.succeed("C").toRoute
        val actual = (a ++ b ++ c).apply(())
        assert(actual)(isSuccess(equalTo("C")))
      },
    ),
    suite("asEffect")(
      test("should resolve") {
        val a = Route.collect[Int] { case 1 => "A" }
        val actual = a.apply(1).toZIO
        assertZIO(actual)(equalTo("A"))
      },
      test("should complete") {
        val a = Route.collect[Int] { case 1 => "A" }
        val actual = a.toZIO(2).either
        assertZIO(actual)(isLeft(isNone))
      },
    ),
    suite("collect")(
      test("should succeed") {
        val a = Route.collect[Int] { case 1 => "OK" }
        val actual = a.apply(1)
        assert(actual)(isSuccess(equalTo("OK")))
      },
      test("should fail") {
        val a = Route.collect[Int] { case 1 => "OK" }
        assertTrue(!a.isDefinedAt(0))
      },
    ),
    suite("collectZIO")(
      test("should be empty") {
        val a = Route.collectZIO[Int] { case 1 => ZIO.succeed("A") }
        assertTrue(!a.isDefinedAt(2))
      },
      test("should resolve") {
        val a = Route.collectZIO[Int] { case 1 => ZIO.succeed("A") }
        val actual = a.apply(1)
        assert(actual)(isEffect)
      },
      test("should resolve second effect") {
        val a = Route.empty
        val b = Handler.succeed("B").toRoute
        val actual = (a ++ b).apply(2)
        assert(actual)(isSuccess(equalTo("B")))
      },
    ),
    suite("collectHttp")(
      test("should delegate to its HTTP apps") {
        val app = Route.collectHandler[Int] {
          case 1 => Handler.succeed(1)
          case 2 => Handler.succeed(2)
        }
        val actual = app.apply(2)
        assert(actual)(isSuccess(equalTo(2)))
      },
      test("should be empty if no matches") {
        val app = Route.collectHandler[Int](Map.empty)
        assertTrue(!app.isDefinedAt(1))
      },
    ),
    suite("when")(
      test("should execute http only when condition applies") {
        val app = Handler.succeed(1).toRoute.when((_: Any) => true)
        val actual = app.apply(0)
        assert(actual)(isSuccess(equalTo(1)))
      },
      test("should not execute http when condition doesn't apply") {
        val app = Handler.succeed(1).toRoute.when((_: Any) => false)
        assertTrue(!app.isDefinedAt(0))
      },
      test("should die when condition throws an exception") {
        val t = new IllegalArgumentException("boom")
        val app = Handler.succeed(1).toRoute.when((_: Any) => throw t)
        val actual = app.toZIO(0)
        assertZIO(actual.exit)(dies(equalTo(t)))
      },
    ),
  )
}
