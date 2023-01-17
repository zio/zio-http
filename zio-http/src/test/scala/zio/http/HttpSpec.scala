package zio.http

import zio.test.Assertion.{dies, equalTo, isLeft, isNone}
import zio.test.{Spec, ZIOSpecDefault, assert, assertZIO}
import zio.{Unsafe, ZIO}

object HttpSpec extends ZIOSpecDefault with HExitAssertion {
  implicit val allowUnsafe: Unsafe = Unsafe.unsafe

  def spec: Spec[Any, Nothing] =
    suite("Route")(
      suite("collectHExit")(
        test("should succeed") {
          val a      = Http.collectHExit[Int] { case 1 => HExit.succeed("OK") }
          val actual = a.runHExitOrNull(1)
          assert(actual)(isSuccess(equalTo("OK")))
        },
        test("should fail") {
          val a      = Http.collectHExit[Int] { case 1 => HExit.fail("OK") }
          val actual = a.runHExitOrNull(1)
          assert(actual)(isFailure(equalTo("OK")))
        },
        test("should die") {
          val t      = new Throwable("boom")
          val a      = Http.collectHExit[Int] { case 1 => HExit.die(t) }
          val actual = a.runHExitOrNull(1)
          assert(actual)(isDie(equalTo(t)))
        },
        test("should give empty if the inout is not defined") {
          val a      = Http.collectHExit[Int] { case 1 => HExit.succeed("OK") }
          val actual = a.runHExitOrNull(0)
          assert(actual)(isSuccess(equalTo(null)))
        },
      ),
      suite("combine")(
        test("should resolve first") {
          val a      = Http.collect[Int] { case 1 => "A" }
          val b      = Http.collect[Int] { case 2 => "B" }
          val actual = (a ++ b).runHExitOrNull(1)
          assert(actual)(isSuccess(equalTo("A")))
        },
        test("should resolve second") {
          val a      = Http.empty
          val b      = Handler.succeed("A").toHttp
          val actual = (a ++ b).runHExitOrNull(())
          assert(actual)(isSuccess(equalTo("A")))
        },
        test("should resolve second") {
          val a      = Http.collect[Int] { case 1 => "A" }
          val b      = Http.collect[Int] { case 2 => "B" }
          val actual = (a ++ b).runHExitOrNull(2)
          assert(actual)(isSuccess(equalTo("B")))
        },
        test("should not resolve") {
          val a      = Http.collect[Int] { case 1 => "A" }
          val b      = Http.collect[Int] { case 2 => "B" }
          val actual = (a ++ b).runHExitOrNull(3)
          assert(actual)(isSuccess(equalTo(null)))
        },
        test("should not resolve") {
          val a      = Http.empty
          val b      = Http.empty
          val c      = Http.empty
          val actual = (a ++ b ++ c).runHExitOrNull(())
          assert(actual)(isSuccess(equalTo(null)))
        },
        test("should fail with second") {
          val a      = Http.empty
          val b      = Handler.fail(100).toHttp
          val c      = Handler.succeed("A").toHttp
          val actual = (a ++ b ++ c).runHExitOrNull(())
          assert(actual)(isFailure(equalTo(100)))
        },
        test("should resolve third") {
          val a      = Http.empty
          val b      = Http.empty
          val c      = Handler.succeed("C").toHttp
          val actual = (a ++ b ++ c).runHExitOrNull(())
          assert(actual)(isSuccess(equalTo("C")))
        },
      ),
      suite("asEffect")(
        test("should resolve") {
          val a      = Http.collect[Int] { case 1 => "A" }
          val actual = a.runHExitOrNull(1).toZIO
          assertZIO(actual)(equalTo("A"))
        },
        test("should complete") {
          val a      = Http.collect[Int] { case 1 => "A" }
          val actual = a.runZIO(2).either
          assertZIO(actual)(isLeft(isNone))
        },
      ),
      suite("collect")(
        test("should succeed") {
          val a      = Http.collect[Int] { case 1 => "OK" }
          val actual = a.runHExitOrNull(1)
          assert(actual)(isSuccess(equalTo("OK")))
        },
        test("should fail") {
          val a      = Http.collect[Int] { case 1 => "OK" }
          val actual = a.runHExitOrNull(0)
          assert(actual)(isSuccess(equalTo(null)))
        },
      ),
      suite("collectZIO")(
        test("should be empty") {
          val a      = Http.collectZIO[Int] { case 1 => ZIO.succeed("A") }
          val actual = a.runHExitOrNull(2)
          assert(actual)(isSuccess(equalTo(null)))
        },
        test("should resolve") {
          val a      = Http.collectZIO[Int] { case 1 => ZIO.succeed("A") }
          val actual = a.runHExitOrNull(1)
          assert(actual)(isEffect)
        },
        test("should resolve second effect") {
          val a      = Http.empty
          val b      = Handler.succeed("B").toHttp
          val actual = (a ++ b).runHExitOrNull(2)
          assert(actual)(isSuccess(equalTo("B")))
        },
      ),
      suite("collectHttp")(
        test("should delegate to its HTTP apps") {
          val app    = Http.collectHandler[Int] {
            case 1 => Handler.succeed(1)
            case 2 => Handler.succeed(2)
          }
          val actual = app.runHExitOrNull(2)
          assert(actual)(isSuccess(equalTo(2)))
        },
        test("should be empty if no matches") {
          val app    = Http.collectHandler[Int](Map.empty)
          val actual = app.runHExitOrNull(1)
          assert(actual)(isSuccess(equalTo(null)))
        },
      ),
      suite("when")(
        test("should execute http only when condition applies") {
          val app    = Handler.succeed(1).toHttp.when((_: Any) => true)
          val actual = app.runHExitOrNull(0)
          assert(actual)(isSuccess(equalTo(1)))
        },
        test("should not execute http when condition doesn't apply") {
          val app    = Handler.succeed(1).toHttp.when((_: Any) => false)
          val actual = app.runHExitOrNull(0)
          assert(actual.asInstanceOf[HExit[Any, Any, Any]])(isSuccess(equalTo(null)))
        },
        test("should die when condition throws an exception") {
          val t      = new IllegalArgumentException("boom")
          val app    = Handler.succeed(1).toHttp.when((_: Any) => throw t)
          val actual = app.runZIO(0)
          assertZIO(actual.exit)(dies(equalTo(t)))
        },
      ),
    )
}
