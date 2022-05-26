package zhttp.http

import zio._
import zio.duration.durationInt
import zio.test.Assertion._
import zio.test.TestAspect.timeout
import zio.test._
import zio.test.environment.TestClock

object HttpSpec extends DefaultRunnableSpec with HExitAssertion {
  def spec = suite("Http")(
    suite("flatMap")(
      test("should flatten") {
        val app    = Http.succeed(1).flatMap(i => Http.succeed(i + 1))
        val actual = app.execute(0)
        assert(actual)(isSuccess(equalTo(2)))
      },
    ),
    suite("orElse")(
      test("should succeed") {
        val a1     = Http.succeed(1)
        val a2     = Http.succeed(2)
        val a      = a1 <> a2
        val actual = a.execute(())
        assert(actual)(isSuccess(equalTo(1)))
      },
      test("should fail with first") {
        val a1     = Http.fail("A")
        val a2     = Http.succeed("B")
        val a      = a1 <> a2
        val actual = a.execute(())
        assert(actual)(isSuccess(equalTo("B")))
      },
      test("does not recover from defects") {
        val t      = new Throwable("boom")
        val a1     = Http.die(t)
        val a2     = Http.succeed("B")
        val a      = a1 <> a2
        val actual = a.execute(())
        assert(actual)(isDie(equalTo(t)))
      },
    ),
    suite("fail")(
      test("should fail") {
        val a      = Http.fail(100)
        val actual = a.execute(())
        assert(actual)(isFailure(equalTo(100)))
      },
    ),
    suite("die")(
      test("should die") {
        val t      = new Throwable("boom")
        val a      = Http.die(t)
        val actual = a.execute(())
        assert(actual)(isDie(equalTo(t)))
      },
    ),
    suite("foldM")(
      test("should catch") {
        val a      = Http.fail(100).catchAll(e => Http.succeed(e + 1))
        val actual = a.execute(0)
        assert(actual)(isSuccess(equalTo(101)))
      },
    ),
    suite("identity")(
      test("should passthru") {
        val a      = Http.identity[Int]
        val actual = a.execute(0)
        assert(actual)(isSuccess(equalTo(0)))
      },
    ),
    suite("collect")(
      test("should succeed") {
        val a      = Http.collect[Int] { case 1 => "OK" }
        val actual = a.execute(1)
        assert(actual)(isSuccess(equalTo("OK")))
      },
      test("should fail") {
        val a      = Http.collect[Int] { case 1 => "OK" }
        val actual = a.execute(0)
        assert(actual)(isEmpty)
      },
    ),
    suite("codecMiddleware")(
      test("codec success") {
        val a      = Http.collect[Int] { case v => v.toString }
        val b      = Http.collect[String] { case v => v.toInt }
        val app    = Http.identity[String] @@ (a \/ b)
        val actual = app.execute(2)
        assert(actual)(isSuccess(equalTo(2)))
      },
      test("encoder failure") {
        val app    = Http.identity[Int] @@ (Http.succeed(1) \/ Http.fail("fail"))
        val actual = app.execute(())
        assert(actual)(isFailure(equalTo("fail")))
      },
      test("decoder failure") {
        val app    = Http.identity[Int] @@ (Http.fail("fail") \/ Http.succeed(1))
        val actual = app.execute(())
        assert(actual)(isFailure(equalTo("fail")))
      },
    ),
    suite("collectHExit")(
      test("should succeed") {
        val a      = Http.collectHExit[Int] { case 1 => HExit.succeed("OK") }
        val actual = a.execute(1)
        assert(actual)(isSuccess(equalTo("OK")))
      },
      test("should fail") {
        val a      = Http.collectHExit[Int] { case 1 => HExit.fail("OK") }
        val actual = a.execute(1)
        assert(actual)(isFailure(equalTo("OK")))
      },
      test("should die") {
        val t      = new Throwable("boom")
        val a      = Http.collectHExit[Int] { case 1 => HExit.die(t) }
        val actual = a.execute(1)
        assert(actual)(isDie(equalTo(t)))
      },
      test("should give empty if the inout is not defined") {
        val a      = Http.collectHExit[Int] { case 1 => HExit.succeed("OK") }
        val actual = a.execute(0)
        assert(actual)(isEmpty)
      },
    ),
    suite("fromFunctionHExit")(
      test("should succeed if the ") {
        val a      = Http.fromFunctionHExit[Int] { a => HExit.succeed(a + 1) }
        val actual = a.execute(1)
        assert(actual)(isSuccess(equalTo(2)))
      },
      test("should fail if the returned HExit is a failure") {
        val a      = Http.fromFunctionHExit[Int] { a => HExit.fail(a + 1) }
        val actual = a.execute(1)
        assert(actual)(isFailure(equalTo(2)))
      },
      test("should give empty if the returned HExit is empty") {
        val a      = Http.fromFunctionHExit[Int] { _ => HExit.empty }
        val actual = a.execute(0)
        assert(actual)(isEmpty)
      },
      test("should die if the functions throws an exception") {
        val t      = new Throwable("boom")
        val a      = Http.fromFunctionHExit[Int] { _ => throw t }
        val actual = a.execute(0)
        assert(actual)(isDie(equalTo(t)))
      },
    ),
    suite("fromHExit")(
      test("should succeed if the returned HExit succeeds ") {
        val a      = Http.fromHExit(HExit.succeed("a"))
        val actual = a.execute(1)
        assert(actual)(isSuccess(equalTo("a")))
      },
      test("should fail if the returned HExit is a failure") {
        val a      = Http.fromHExit(HExit.fail("fail"))
        val actual = a.execute(1)
        assert(actual)(isFailure(equalTo("fail")))
      },
      test("should give empty if the returned HExit is empty") {
        val a      = Http.fromHExit(HExit.empty)
        val actual = a.execute(1)
        assert(actual)(isEmpty)
      },
    ),
    suite("combine")(
      test("should resolve first") {
        val a      = Http.collect[Int] { case 1 => "A" }
        val b      = Http.collect[Int] { case 2 => "B" }
        val actual = (a ++ b).execute(1)
        assert(actual)(isSuccess(equalTo("A")))
      },
      test("should resolve second") {
        val a      = Http.empty
        val b      = Http.succeed("A")
        val actual = (a ++ b).execute(())
        assert(actual)(isSuccess(equalTo("A")))
      },
      test("should resolve second") {
        val a      = Http.collect[Int] { case 1 => "A" }
        val b      = Http.collect[Int] { case 2 => "B" }
        val actual = (a ++ b).execute(2)
        assert(actual)(isSuccess(equalTo("B")))
      },
      test("should not resolve") {
        val a      = Http.collect[Int] { case 1 => "A" }
        val b      = Http.collect[Int] { case 2 => "B" }
        val actual = (a ++ b).execute(3)
        assert(actual)(isEmpty)
      },
      test("should not resolve") {
        val a      = Http.empty
        val b      = Http.empty
        val c      = Http.empty
        val actual = (a ++ b ++ c).execute(())
        assert(actual)(isEmpty)
      },
      test("should fail with second") {
        val a      = Http.empty
        val b      = Http.fail(100)
        val c      = Http.succeed("A")
        val actual = (a ++ b ++ c).execute(())
        assert(actual)(isFailure(equalTo(100)))
      },
      test("should resolve third") {
        val a      = Http.empty
        val b      = Http.empty
        val c      = Http.succeed("C")
        val actual = (a ++ b ++ c).execute(())
        assert(actual)(isSuccess(equalTo("C")))
      },
      testM("should resolve second") {
        val a      = Http.fromHExit(HExit.Effect(ZIO.fail(None)))
        val b      = Http.succeed(2)
        val actual = (a ++ b).execute(()).toZIO.either
        assertM(actual)(isRight)
      },
    ),
    suite("asEffect")(
      testM("should resolve") {
        val a      = Http.collect[Int] { case 1 => "A" }
        val actual = a.execute(1).toZIO
        assertM(actual)(equalTo("A"))
      },
      testM("should complete") {
        val a      = Http.collect[Int] { case 1 => "A" }
        val actual = a.execute(2).toZIO.either
        assertM(actual)(isLeft(isNone))
      },
    ),
    suite("collectM")(
      test("should be empty") {
        val a      = Http.collectZIO[Int] { case 1 => UIO("A") }
        val actual = a.execute(2)
        assert(actual)(isEmpty)
      },
      test("should resolve") {
        val a      = Http.collectZIO[Int] { case 1 => UIO("A") }
        val actual = a.execute(1)
        assert(actual)(isEffect)
      },
      test("should resolve managed") {
        val a      = Http.collectManaged[Int] { case 1 => ZManaged.succeed("A") }
        val actual = a.execute(1)
        assert(actual)(isEffect)
      },
      test("should resolve second effect") {
        val a      = Http.empty
        val b      = Http.succeed("B")
        val actual = (a ++ b).execute(2)
        assert(actual)(isSuccess(equalTo("B")))
      },
    ),
    suite("collectHttp")(
      test("should delegate to its HTTP apps") {
        val app    = Http.collectHttp[Int] {
          case 1 => Http.succeed(1)
          case 2 => Http.succeed(2)
        }
        val actual = app.execute(2)
        assert(actual)(isSuccess(equalTo(2)))
      },
      test("should be empty if no matches") {
        val app    = Http.collectHttp[Int](Map.empty)
        val actual = app.execute(1)
        assert(actual)(isEmpty)
      },
    ),
    suite("tap")(
      testM("taps the successs") {
        for {
          r <- Ref.make(0)
          app = Http.succeed(1).tap(v => Http.fromZIO(r.set(v)))
          _   <- app.execute(()).toZIO
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("tapM")(
      testM("taps the successs") {
        for {
          r <- Ref.make(0)
          app = Http.succeed(1).tapZIO(r.set)
          _   <- app.execute(()).toZIO
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("tapError")(
      testM("taps the error") {
        for {
          r <- Ref.make(0)
          app = Http.fail(1).tapError(v => Http.fromZIO(r.set(v)))
          _   <- app.execute(()).toZIO.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("tapErrorM")(
      testM("taps the error") {
        for {
          r <- Ref.make(0)
          app = Http.fail(1).tapErrorZIO(r.set)
          _   <- app.execute(()).toZIO.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("tapAll")(
      testM("taps the success") {
        for {
          r <- Ref.make(0)
          app = (Http.succeed(1): Http[Any, Any, Any, Int])
            .tapAll(_ => Http.empty, _ => Http.empty, v => Http.fromZIO(r.set(v)), Http.empty)
          _   <- app.execute(()).toZIO
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
      testM("taps the failure") {
        for {
          r <- Ref.make(0)
          app = (Http.fail(1): Http[Any, Int, Any, Any])
            .tapAll(v => Http.fromZIO(r.set(v)), _ => Http.empty, _ => Http.empty, Http.empty)
          _   <- app.execute(()).toZIO.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
      testM("taps the die") {
        val t = new Throwable("boom")
        for {
          r <- Ref.make(0)
          app = (Http.die(t): Http[Any, Any, Any, Any])
            .tapAll(_ => Http.empty, _ => Http.fromZIO(r.set(1)), _ => Http.empty, Http.empty)
          _   <- app.execute(()).toZIO.run.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
      testM("taps the empty") {
        for {
          r <- Ref.make(0)
          app = (Http.empty: Http[Any, Any, Any, Any])
            .tapAll(_ => Http.empty, _ => Http.empty, _ => Http.empty, Http.fromZIO(r.set(1)))
          _   <- app.execute(()).toZIO.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("tapAllZIO")(
      testM("taps the success") {
        for {
          r <- Ref.make(0)
          app = (Http.succeed(1): Http[Any, Any, Any, Int]).tapAllZIO(_ => ZIO.unit, _ => ZIO.unit, r.set, ZIO.unit)
          _   <- app.execute(()).toZIO
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
      testM("taps the failure") {
        for {
          r <- Ref.make(0)
          app = (Http.fail(1): Http[Any, Int, Any, Any]).tapAllZIO(r.set, _ => ZIO.unit, _ => ZIO.unit, ZIO.unit)
          _   <- app.execute(()).toZIO.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
      testM("taps the die") {
        val t = new Throwable("boom")
        for {
          r <- Ref.make(0)
          app = (Http.die(t): Http[Any, Any, Any, Any])
            .tapAllZIO(_ => ZIO.unit, _ => r.set(1), _ => ZIO.unit, ZIO.unit)
          _   <- app.execute(()).toZIO.run.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
      testM("taps the empty") {
        for {
          r <- Ref.make(0)
          app = (Http.empty: Http[Any, Any, Any, Any])
            .tapAllZIO(_ => ZIO.unit, _ => ZIO.unit, _ => ZIO.unit, r.set(1))
          _   <- app.execute(()).toZIO.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("race")(
      testM("left wins") {
        val http = Http.succeed(1) race Http.succeed(2)
        assertM(http(()))(equalTo(1))
      },
      testM("sync right wins") {
        val http = Http.fromZIO(UIO(1)) race Http.succeed(2)
        assertM(http(()))(equalTo(2))
      },
      testM("sync left wins") {
        val http = Http.succeed(1) race Http.fromZIO(UIO(2))
        assertM(http(()))(equalTo(1))
      },
      testM("async fast wins") {
        val http    = Http.succeed(1).delay(1 second) race Http.succeed(2).delay(2 second)
        val program = http(()) <& TestClock.adjust(5 second)
        assertM(program)(equalTo(1))
      },
    ),
    suite("attempt")(
      suite("failure") {
        test("fails with a throwable") {
          val throwable = new Throwable("boom")
          val actual    = Http.attempt(throw throwable).execute(())
          assert(actual)(isFailure(equalTo(throwable)))
        }
      },
      suite("success") {
        test("succeeds with a value") {
          val actual = Http.attempt("bar").execute(())
          assert(actual)(isSuccess(equalTo("bar")))
        }
      },
    ),
    suite("when")(
      test("should execute http only when condition applies") {
        val app    = Http.succeed(1).when((_: Any) => true)
        val actual = app.execute(0)
        assert(actual)(isSuccess(equalTo(1)))
      },
      test("should not execute http when condition doesn't apply") {
        val app    = Http.succeed(1).when((_: Any) => false)
        val actual = app.execute(0)
        assert(actual)(isEmpty)
      },
      test("should die when condition throws an exception") {
        val t      = new Throwable("boom")
        val app    = Http.succeed(1).when((_: Any) => throw t)
        val actual = app.execute(0)
        assert(actual)(isDie(equalTo(t)))
      },
    ),
    suite("catchSome")(
      test("catches matching exception") {
        val http =
          Http
            .fail(new IllegalArgumentException("boom"))
            .catchSome { case _: IllegalArgumentException =>
              Http.succeed("bar")
            }
        assert(http.execute {})(isSuccess(equalTo("bar")))
      },
      test("keeps an error if doesn't catch anything") {
        val exception = new Throwable("boom")
        val http      =
          Http
            .fail(exception)
            .catchSome { case _: ArithmeticException =>
              Http.succeed("bar")
            }
        assert(http.execute {})(isFailure(equalTo(exception)))
      },
      test("doesn't affect the success") {
        val http =
          (Http.succeed("bar"): Http[Any, Throwable, Any, String]).catchSome { case _: Throwable =>
            Http.succeed("baz")
          }
        assert(http.execute {})(isSuccess(equalTo("bar")))
      },
    ),
    suite("refineOrDie")(
      test("refines matching exception") {
        val http =
          Http.fail(new IllegalArgumentException("boom")).refineOrDie { case _: IllegalArgumentException =>
            "fail"
          }
        assert(http.execute {})(isFailure(equalTo("fail")))
      },
      test("dies if doesn't catch anything") {
        val t    = new Throwable("boom")
        val http =
          Http
            .fail(t)
            .refineOrDie { case _: IllegalArgumentException =>
              "fail"
            }
        assert(http.execute {})(isDie(equalTo(t)))
      },
      test("doesn't affect the success") {
        val http =
          (Http.succeed("bar"): Http[Any, Throwable, Any, String]).refineOrDie { case _: Throwable =>
            Http.succeed("baz")
          }
        assert(http.execute {})(isSuccess(equalTo("bar")))
      },
    ),
    suite("orDie")(
      test("dies on failure") {
        val t    = new Throwable("boom")
        val http =
          Http.fail(t).orDie
        assert(http.execute {})(isDie(equalTo(t)))
      },
      test("doesn't affect the success") {
        val http =
          (Http.succeed("bar"): Http[Any, Throwable, Any, String]).orDie
        assert(http.execute {})(isSuccess(equalTo("bar")))
      },
    ),
    suite("catchSomeDefect")(
      test("catches defect") {
        val t    = new IllegalArgumentException("boom")
        val http = Http.die(t).catchSomeDefect { case _: IllegalArgumentException => Http.succeed("OK") }
        assert(http.execute {})(isSuccess(equalTo("OK")))

      },
      test("catches thrown defects") {
        val http = Http
          .collect[Any] { case _ => throw new IllegalArgumentException("boom") }
          .catchSomeDefect { case _: IllegalArgumentException => Http.succeed("OK") }
        assert(http.execute {})(isSuccess(equalTo("OK")))
      },
      test("propagates non-caught defect") {
        val t    = new IllegalArgumentException("boom")
        val http = Http.die(t).catchSomeDefect { case _: SecurityException => Http.succeed("OK") }
        assert(http.execute {})(isDie(equalTo(t)))
      },
    ),
    suite("catchNonFatalOrDie")(
      test("catches non-fatal exception") {
        val t    = new IllegalArgumentException("boom")
        val http = Http.fail(t).catchNonFatalOrDie { _ => Http.succeed("OK") }
        assert(http.execute {})(isSuccess(equalTo("OK")))
      },
      test("dies with fatal exception") {
        val t    = new OutOfMemoryError()
        val http = Http.fail(t).catchNonFatalOrDie(_ => Http.succeed("OK"))
        assert(http.execute {})(isDie(equalTo(t)))
      },
    ),
    suite("merge")(
      test("merges error into success") {
        val http = Http.fail(1).merge
        assert(http.execute {})(isSuccess(equalTo(1)))
      },
    ),
  ) @@ timeout(10 seconds)
}
