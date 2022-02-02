package zhttp.http

import zio._
import zio.duration.durationInt
import zio.test.Assertion._
import zio.test.TestAspect.timeout
import zio.test._
import zio.test.environment.TestClock

object HttpSpec extends DefaultRunnableSpec with HExitAssertion {
  private val convert: Int => Int      = a => a
  private val convertEmpty: Any => Any = _ => ()

  def spec = suite("Http")(
    suite("flatMap")(
      test("should flatten") {
        val app    = Http.succeed(1).flatMap(i => Http.succeed(i + 1))
        val actual = app.execute(0, convert)
        assert(actual)(isSuccess(equalTo(2)))
      },
    ) +
      suite("orElse")(
        test("should succeed") {
          val a1     = Http.succeed(1)
          val a2     = Http.succeed(2)
          val a      = a1 <> a2
          val actual = a.execute((), convertEmpty)
          assert(actual)(isSuccess(equalTo(1)))
        } +
          test("should fail with first") {
            val a1     = Http.fail("A")
            val a2     = Http.succeed("B")
            val a      = a1 <> a2
            val actual = a.execute((), convertEmpty)
            assert(actual)(isSuccess(equalTo("B")))
          },
      ) +
      suite("fail")(
        test("should fail") {
          val a      = Http.fail(100)
          val actual = a.execute((), convertEmpty)
          assert(actual)(isFailure(equalTo(100)))
        },
      ) +
      suite("foldM")(
        test("should catch") {
          val a      = Http.fail(100).catchAll(e => Http.succeed(e + 1))
          val actual = a.execute(0, convert)
          assert(actual)(isSuccess(equalTo(101)))
        },
      ) +
      suite("identity")(
        test("should passthru") {
          val a      = Http.identity[Int]
          val actual = a.execute(0, convert)
          assert(actual)(isSuccess(equalTo(0)))
        },
      ) +
      suite("collect")(
        test("should succeed") {
          val a      = Http.collect[Int] { case 1 => "OK" }
          val actual = a.execute(1, convert)
          assert(actual)(isSuccess(equalTo("OK")))
        } +
          test("should fail") {
            val a      = Http.collect[Int] { case 1 => "OK" }
            val actual = a.execute(0, convert)
            assert(actual)(isEmpty)
          },
      ) +
      suite("combine")(
        test("should resolve first") {
          val a      = Http.collect[Int] { case 1 => "A" }
          val b      = Http.collect[Int] { case 2 => "B" }
          val actual = (a ++ b).execute(1, convert)
          assert(actual)(isSuccess(equalTo("A")))
        } +
          test("should resolve second") {
            val a      = Http.empty
            val b      = Http.succeed("A")
            val actual = (a ++ b).execute((), convertEmpty)
            assert(actual)(isSuccess(equalTo("A")))
          } +
          test("should resolve second") {
            val a      = Http.collect[Int] { case 1 => "A" }
            val b      = Http.collect[Int] { case 2 => "B" }
            val actual = (a ++ b).execute(2, convert)
            assert(actual)(isSuccess(equalTo("B")))
          } +
          test("should not resolve") {
            val a      = Http.collect[Int] { case 1 => "A" }
            val b      = Http.collect[Int] { case 2 => "B" }
            val actual = (a ++ b).execute(3, convert)
            assert(actual)(isEmpty)
          },
      ) +
      suite("asEffect")(
        testM("should resolve") {
          val a      = Http.collect[Int] { case 1 => "A" }
          val actual = a.execute(1, convert).toZIO
          assertM(actual)(equalTo("A"))
        } +
          testM("should complete") {
            val a      = Http.collect[Int] { case 1 => "A" }
            val actual = a.execute(2, convert).toZIO.either
            assertM(actual)(isLeft(isNone))
          },
      ) +
      suite("collectM")(
        test("should be empty") {
          val a      = Http.collectZIO[Int] { case 1 => UIO("A") }
          val actual = a.execute(2, convert)
          assert(actual)(isEmpty)
        } +
          test("should resolve") {
            val a      = Http.collectZIO[Int] { case 1 => UIO("A") }
            val actual = a.execute(1, convert)
            assert(actual)(isEffect)
          } +
          test("should resolve managed") {
            val a      = Http.collectManaged[Int] { case 1 => ZManaged.succeed("A") }
            val actual = a.execute(1, convert)
            assert(actual)(isEffect)
          } +
          test("should resolve second effect") {
            val a      = Http.empty.flatten
            val b      = Http.succeed("B")
            val actual = (a ++ b).execute(2, convert)
            assert(actual)(isSuccess(equalTo("B")))
          },
      ) +
      suite("route")(
        test("should delegate to its HTTP apps") {
          val app    = Http.route[Int] {
            case 1 => Http.succeed(1)
            case 2 => Http.succeed(2)
          }
          val actual = app.execute(2, convert)
          assert(actual)(isSuccess(equalTo(2)))
        } +
          test("should be empty if no matches") {
            val app    = Http.route[Int](Map.empty)
            val actual = app.execute(1, convert)
            assert(actual)(isEmpty)
          },
      ) +
      suite("tap")(
        testM("taps the successs") {
          for {
            r <- Ref.make(0)
            app = Http.succeed(1).tap(v => Http.fromZIO(r.set(v)))
            _   <- app.execute((), convertEmpty).toZIO
            res <- r.get
          } yield assert(res)(equalTo(1))
        },
      ) +
      suite("tapM")(
        testM("taps the successs") {
          for {
            r <- Ref.make(0)
            app = Http.succeed(1).tapZIO(r.set)
            _   <- app.execute((), convertEmpty).toZIO
            res <- r.get
          } yield assert(res)(equalTo(1))
        },
      ) +
      suite("tapError")(
        testM("taps the error") {
          for {
            r <- Ref.make(0)
            app = Http.fail(1).tapError(v => Http.fromZIO(r.set(v)))
            _   <- app.execute((), convertEmpty).toZIO.ignore
            res <- r.get
          } yield assert(res)(equalTo(1))
        },
      ) +
      suite("tapErrorM")(
        testM("taps the error") {
          for {
            r <- Ref.make(0)
            app = Http.fail(1).tapErrorZIO(r.set)
            _   <- app.execute((), convertEmpty).toZIO.ignore
            res <- r.get
          } yield assert(res)(equalTo(1))
        },
      ) +
      suite("tapAll")(
        testM("taps the success") {
          for {
            r <- Ref.make(0)
            app = (Http.succeed(1): Http[Any, Any, Any, Int])
              .tapAll(_ => Http.empty, v => Http.fromZIO(r.set(v)), Http.empty)
            _   <- app.execute((), convertEmpty).toZIO
            res <- r.get
          } yield assert(res)(equalTo(1))
        } +
          testM("taps the failure") {
            for {
              r <- Ref.make(0)
              app = (Http.fail(1): Http[Any, Int, Any, Any])
                .tapAll(v => Http.fromZIO(r.set(v)), _ => Http.empty, Http.empty)
              _   <- app.execute((), convertEmpty).toZIO.ignore
              res <- r.get
            } yield assert(res)(equalTo(1))
          } +
          testM("taps the empty") {
            for {
              r <- Ref.make(0)
              app = (Http.empty: Http[Any, Any, Any, Any])
                .tapAll(_ => Http.empty, _ => Http.empty, Http.fromZIO(r.set(1)))
              _   <- app.execute((), convertEmpty).toZIO.ignore
              res <- r.get
            } yield assert(res)(equalTo(1))
          },
      ) +
      suite("tapAllM")(
        testM("taps the success") {
          for {
            r <- Ref.make(0)
            app = (Http.succeed(1): Http[Any, Any, Any, Int]).tapAllZIO(_ => ZIO.unit, r.set, ZIO.unit)
            _   <- app.execute((), convertEmpty).toZIO
            res <- r.get
          } yield assert(res)(equalTo(1))
        } +
          testM("taps the failure") {
            for {
              r <- Ref.make(0)
              app = (Http.fail(1): Http[Any, Int, Any, Any]).tapAllZIO(r.set, _ => ZIO.unit, ZIO.unit)
              _   <- app.execute((), convertEmpty).toZIO.ignore
              res <- r.get
            } yield assert(res)(equalTo(1))
          } +
          testM("taps the empty") {
            for {
              r <- Ref.make(0)
              app = (Http.empty: Http[Any, Any, Any, Any])
                .tapAllZIO(_ => ZIO.unit, _ => ZIO.unit, r.set(1))
              _   <- app.execute((), convertEmpty).toZIO.ignore
              res <- r.get
            } yield assert(res)(equalTo(1))
          },
      ) +
      suite("race") {
        testM("left wins") {
          val http = Http.succeed(1) race Http.succeed(2)
          assertM(http(()))(equalTo(1))
        } +
          testM("sync right wins") {
            val http = Http.fromZIO(UIO(1)) race Http.succeed(2)
            assertM(http(()))(equalTo(2))
          } +
          testM("sync left wins") {
            val http = Http.succeed(1) race Http.fromZIO(UIO(2))
            assertM(http(()))(equalTo(1))
          } +
          testM("async fast wins") {
            val http    = Http.succeed(1).delay(1 second) race Http.succeed(2).delay(2 second)
            val program = http(()) <& TestClock.adjust(5 second)
            assertM(program)(equalTo(1))
          }
      },
  ) @@ timeout(10 seconds)
}
