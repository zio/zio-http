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
    ) +
      suite("orElse")(
        test("should succeed") {
          val a1     = Http.succeed(1)
          val a2     = Http.succeed(2)
          val a      = a1 <> a2
          val actual = a.execute(())
          assert(actual)(isSuccess(equalTo(1)))
        } +
          test("should fail with first") {
            val a1     = Http.fail("A")
            val a2     = Http.succeed("B")
            val a      = a1 <> a2
            val actual = a.execute(())
            assert(actual)(isSuccess(equalTo("B")))
          },
      ) +
      suite("fail")(
        test("should fail") {
          val a      = Http.fail(100)
          val actual = a.execute(())
          assert(actual)(isFailure(equalTo(100)))
        },
      ) +
      suite("foldM")(
        test("should catch") {
          val a      = Http.fail(100).catchAll(e => Http.succeed(e + 1))
          val actual = a.execute(0)
          assert(actual)(isSuccess(equalTo(101)))
        },
      ) +
      suite("identity")(
        test("should passthru") {
          val a      = Http.identity[Int]
          val actual = a.execute(0)
          assert(actual)(isSuccess(equalTo(0)))
        },
      ) +
      suite("collect")(
        test("should succeed") {
          val a      = Http.collect[Int] { case 1 => "OK" }
          val actual = a.execute(1)
          assert(actual)(isSuccess(equalTo("OK")))
        } +
          test("should fail") {
            val a      = Http.collect[Int] { case 1 => "OK" }
            val actual = a.execute(0)
            assert(actual)(isEmpty)
          },
      ) +
      suite("collectHExit")(
        test("should succeed") {
          val a      = Http.collectHExit[Int] { case 1 => HExit.succeed("OK") }
          val actual = a.execute(1)
          assert(actual)(isSuccess(equalTo("OK")))
        } +
          test("should fail") {
            val a      = Http.collectHExit[Int] { case 1 => HExit.fail("OK") }
            val actual = a.execute(1)
            assert(actual)(isFailure(equalTo("OK")))
          } +
          test("should give empty if the inout is not defined") {
            val a      = Http.collectHExit[Int] { case 1 => HExit.succeed("OK") }
            val actual = a.execute(0)
            assert(actual)(isEmpty)
          },
      ) +
      suite("fromFunctionHExit")(
        test("should succeed if the ") {
          val a      = Http.fromFunctionHExit[Int] { a => HExit.succeed(a + 1) }
          val actual = a.execute(1)
          assert(actual)(isSuccess(equalTo(2)))
        } +
          test("should fail if the returned HExit is a failure") {
            val a      = Http.fromFunctionHExit[Int] { a => HExit.fail(a + 1) }
            val actual = a.execute(1)
            assert(actual)(isFailure(equalTo(2)))
          } +
          test("should give empty if the returned HExit is empty") {
            val a      = Http.fromFunctionHExit[Int] { _ => HExit.empty }
            val actual = a.execute(0)
            assert(actual)(isEmpty)
          },
      ) +
      suite("fromHExit")(
        test("should succeed if the returned HExit succeeds ") {
          val a      = Http.fromHExit(HExit.succeed("a"))
          val actual = a.execute(1)
          assert(actual)(isSuccess(equalTo("a")))
        } +
          test("should fail if the returned HExit is a failure") {
            val a      = Http.fromHExit(HExit.fail("fail"))
            val actual = a.execute(1)
            assert(actual)(isFailure(equalTo("fail")))
          } +
          test("should give empty if the returned HExit is empty") {
            val a      = Http.fromHExit(HExit.empty)
            val actual = a.execute(1)
            assert(actual)(isEmpty)
          },
      ) +

      suite("combine")(
        test("should resolve first") {
          val a      = Http.collect[Int] { case 1 => "A" }
          val b      = Http.collect[Int] { case 2 => "B" }
          val actual = (a ++ b).execute(1)
          assert(actual)(isSuccess(equalTo("A")))
        } +
          test("should resolve second") {
            val a      = Http.empty
            val b      = Http.succeed("A")
            val actual = (a ++ b).execute(())
            assert(actual)(isSuccess(equalTo("A")))
          } +
          test("should resolve second") {
            val a      = Http.collect[Int] { case 1 => "A" }
            val b      = Http.collect[Int] { case 2 => "B" }
            val actual = (a ++ b).execute(2)
            assert(actual)(isSuccess(equalTo("B")))
          } +
          test("should not resolve") {
            val a      = Http.collect[Int] { case 1 => "A" }
            val b      = Http.collect[Int] { case 2 => "B" }
            val actual = (a ++ b).execute(3)
            assert(actual)(isEmpty)
          },
      ) +
      suite("asEffect")(
        testM("should resolve") {
          val a      = Http.collect[Int] { case 1 => "A" }
          val actual = a.execute(1).toZIO
          assertM(actual)(equalTo("A"))
        } +
          testM("should complete") {
            val a      = Http.collect[Int] { case 1 => "A" }
            val actual = a.execute(2).toZIO.either
            assertM(actual)(isLeft(isNone))
          },
      ) +
      suite("collectM")(
        test("should be empty") {
          val a      = Http.collectZIO[Int] { case 1 => UIO("A") }
          val actual = a.execute(2)
          assert(actual)(isEmpty)
        } +
          test("should resolve") {
            val a      = Http.collectZIO[Int] { case 1 => UIO("A") }
            val actual = a.execute(1)
            assert(actual)(isEffect)
          } +
          test("should resolve managed") {
            val a      = Http.collectManaged[Int] { case 1 => ZManaged.succeed("A") }
            val actual = a.execute(1)
            assert(actual)(isEffect)
          } +
          test("should resolve second effect") {
            val a      = Http.empty.flatten
            val b      = Http.succeed("B")
            val actual = (a ++ b).execute(2)
            assert(actual)(isSuccess(equalTo("B")))
          },
      ) +
      suite("route")(
        test("should delegate to its HTTP apps") {
          val app    = Http.route[Int] {
            case 1 => Http.succeed(1)
            case 2 => Http.succeed(2)
          }
          val actual = app.execute(2)
          assert(actual)(isSuccess(equalTo(2)))
        } +
          test("should be empty if no matches") {
            val app    = Http.route[Int](Map.empty)
            val actual = app.execute(1)
            assert(actual)(isEmpty)
          },
      ) +
      suite("tap")(
        testM("taps the successs") {
          for {
            r <- Ref.make(0)
            app = Http.succeed(1).tap(v => Http.fromZIO(r.set(v)))
            _   <- app.execute(()).toZIO
            res <- r.get
          } yield assert(res)(equalTo(1))
        },
      ) +
      suite("tapM")(
        testM("taps the successs") {
          for {
            r <- Ref.make(0)
            app = Http.succeed(1).tapZIO(r.set)
            _   <- app.execute(()).toZIO
            res <- r.get
          } yield assert(res)(equalTo(1))
        },
      ) +
      suite("tapError")(
        testM("taps the error") {
          for {
            r <- Ref.make(0)
            app = Http.fail(1).tapError(v => Http.fromZIO(r.set(v)))
            _   <- app.execute(()).toZIO.ignore
            res <- r.get
          } yield assert(res)(equalTo(1))
        },
      ) +
      suite("tapErrorM")(
        testM("taps the error") {
          for {
            r <- Ref.make(0)
            app = Http.fail(1).tapErrorZIO(r.set)
            _   <- app.execute(()).toZIO.ignore
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
            _   <- app.execute(()).toZIO
            res <- r.get
          } yield assert(res)(equalTo(1))
        } +
          testM("taps the failure") {
            for {
              r <- Ref.make(0)
              app = (Http.fail(1): Http[Any, Int, Any, Any])
                .tapAll(v => Http.fromZIO(r.set(v)), _ => Http.empty, Http.empty)
              _   <- app.execute(()).toZIO.ignore
              res <- r.get
            } yield assert(res)(equalTo(1))
          } +
          testM("taps the empty") {
            for {
              r <- Ref.make(0)
              app = (Http.empty: Http[Any, Any, Any, Any])
                .tapAll(_ => Http.empty, _ => Http.empty, Http.fromZIO(r.set(1)))
              _   <- app.execute(()).toZIO.ignore
              res <- r.get
            } yield assert(res)(equalTo(1))
          },
      ) +
      suite("tapAllM")(
        testM("taps the success") {
          for {
            r <- Ref.make(0)
            app = (Http.succeed(1): Http[Any, Any, Any, Int]).tapAllZIO(_ => ZIO.unit, r.set, ZIO.unit)
            _   <- app.execute(()).toZIO
            res <- r.get
          } yield assert(res)(equalTo(1))
        } +
          testM("taps the failure") {
            for {
              r <- Ref.make(0)
              app = (Http.fail(1): Http[Any, Int, Any, Any]).tapAllZIO(r.set, _ => ZIO.unit, ZIO.unit)
              _   <- app.execute(()).toZIO.ignore
              res <- r.get
            } yield assert(res)(equalTo(1))
          } +
          testM("taps the empty") {
            for {
              r <- Ref.make(0)
              app = (Http.empty: Http[Any, Any, Any, Any])
                .tapAllZIO(_ => ZIO.unit, _ => ZIO.unit, r.set(1))
              _   <- app.execute(()).toZIO.ignore
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
      } +
      suite("attempt") {
        suite("failure") {
          test("fails with a throwable") {
            val throwable = new Throwable("boom")
            val actual    = Http.attempt(throw throwable).execute(())
            assert(actual)(isFailure(equalTo(throwable)))
          }
        } +
          suite("success") {
            test("succeeds with a value") {
              val actual = Http.attempt("bar").execute(())
              assert(actual)(isSuccess(equalTo("bar")))
            }
          }
      } +
      suite("when")(
        test("should execute http only when condition applies") {
          val app    = Http.succeed(1).when((_: Any) => true)
          val actual = app.execute(0)
          assert(actual)(isSuccess(equalTo(1)))
        } +
          test("should not execute http when condition doesn't apply") {
            val app    = Http.succeed(1).when((_: Any) => false)
            val actual = app.execute(0)
            assert(actual)(isEmpty)
          },
      ),
  ) @@ timeout(10 seconds)
}
