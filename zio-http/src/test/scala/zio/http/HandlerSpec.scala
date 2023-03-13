/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import zio._
import zio.test.Assertion._
import zio.test.TestAspect.timeout
import zio.test._

object HandlerSpec extends ZIOSpecDefault with ExitAssertion {

  def spec = suite("Handler")(
    suite("flatMap")(
      test("should flatten") {
        val app    = Handler.succeed(1).flatMap(i => Handler.succeed(i + 1))
        val actual = app.apply(0)
        assert(actual)(isSuccess(equalTo(2)))
      },
    ),
    suite("orElse")(
      test("should succeed") {
        val a1     = Handler.succeed(1)
        val a2     = Handler.succeed(2)
        val a      = a1 <> a2
        val actual = a.apply(())
        assert(actual)(isSuccess(equalTo(1)))
      },
      test("should fail with first") {
        val a1     = Handler.fail("A")
        val a2     = Handler.succeed("B")
        val a      = a1 <> a2
        val actual = a.apply(())
        assert(actual)(isSuccess(equalTo("B")))
      },
      test("does not recover from defects") {
        val t      = new Throwable("boom")
        val a1     = Handler.die(t)
        val a2     = Handler.succeed("B")
        val a      = a1 <> a2
        val actual = a.apply(())
        assert(actual)(isDie(equalTo(t)))
      },
    ),
    suite("fail")(
      test("should fail") {
        val a      = Handler.fail(100)
        val actual = a.apply(())
        assert(actual)(isFailure(equalTo(100)))
      },
    ),
    suite("die")(
      test("should die") {
        val t      = new Throwable("boom")
        val a      = Handler.die(t)
        val actual = a.apply(())
        assert(actual)(isDie(equalTo(t)))
      },
    ),
    suite("foldM")(
      test("should catch") {
        val a      = Handler.fail(100).catchAll(e => Handler.succeed(e + 1))
        val actual = a.apply(0)
        assert(actual)(isSuccess(equalTo(101)))
      },
    ),
    suite("identity")(
      test("should passthru") {
        val a      = Handler.identity[Int]
        val actual = a.apply(0)
        assert(actual)(isSuccess(equalTo(0)))
      },
    ),
    suite("fromFunctionExit")(
      test("should succeed if the ") {
        val a      = Handler.fromFunctionExit[Int] { a => Exit.succeed(a + 1) }
        val actual = a.apply(1)
        assert(actual)(isSuccess(equalTo(2)))
      },
      test("should fail if the returned Exit is a failure") {
        val a      = Handler.fromFunctionExit[Int] { a => Exit.fail(a + 1) }
        val actual = a.apply(1)
        assert(actual)(isFailure(equalTo(2)))
      },
      test("should die if the functions throws an exception") {
        val t      = new Throwable("boom")
        val a      = Handler.fromFunctionExit[Int] { _ => throw t }
        val actual = a.runZIO(0)
        assertZIO(actual.exit)(dies(equalTo(t)))
      },
    ),
    suite("fromExit")(
      test("should succeed if the returned Exit succeeds ") {
        val a      = Handler.fromExit(Exit.succeed("a"))
        val actual = a.apply(1)
        assert(actual)(isSuccess(equalTo("a")))
      },
      test("should fail if the returned Exit is a failure") {
        val a      = Handler.fromExit(Exit.fail("fail"))
        val actual = a.apply(1)
        assert(actual)(isFailure(equalTo("fail")))
      },
    ),
    suite("tapZIO")(
      test("taps the success") {
        for {
          r <- Ref.make(0)
          app = Handler.succeed(1).tapZIO(r.set)
          _   <- app.apply(())
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("tapErrorZIO")(
      test("taps the error") {
        for {
          r <- Ref.make(0)
          app = Handler.fail(1).tapErrorZIO(r.set)
          _   <- app.apply(()).ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("tapAllZIO")(
      test("taps the success") {
        for {
          r <- Ref.make(0)
          app = (Handler.succeed(1): Handler[Any, Any, Any, Int]).tapAllZIO(_ => ZIO.unit, r.set)
          _   <- app.apply(())
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
      test("taps the failure") {
        for {
          r <- Ref.make(0)
          app = (Handler.fail(1): Handler[Any, Int, Any, Any])
            .tapAllZIO(cause => cause.failureOption.fold(ZIO.unit)(r.set), _ => ZIO.unit)
          _   <- app.apply(()).ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
      test("taps the die") {
        val t = new Throwable("boom")
        for {
          r <- Ref.make(0)
          app = (Handler.die(t): Handler[Any, Any, Any, Any])
            .tapAllZIO(cause => cause.dieOption.fold(ZIO.unit)(_ => r.set(1)), _ => ZIO.unit)
          _   <- app.apply(()).exit.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("race")(
      test("left wins") {
        val http = Handler.succeed(1) race Handler.succeed(2)
        assertZIO(http.runZIO(()))(equalTo(1))
      },
      test("sync right wins") {
        val http = Handler.fromZIO(ZIO.succeed(1)) race Handler.succeed(2)
        assertZIO(http.runZIO(()))(equalTo(2))
      },
      test("sync left wins") {
        val http = Handler.succeed(1) race Handler.fromZIO(ZIO.succeed(2))
        assertZIO(http.runZIO(()))(equalTo(1))
      },
      test("async fast wins") {
        val http    = Handler.succeed(1).delay(1 second) race Handler.succeed(2).delay(2 second)
        val program = http.runZIO(()) <& TestClock.adjust(5 second)
        assertZIO(program)(equalTo(1))
      },
    ),
    suite("attempt")(
      suite("failure") {
        test("fails with a throwable") {
          val throwable = new Throwable("boom")
          val actual    = Handler.attempt(throw throwable).apply(())
          assert(actual)(isFailure(equalTo(throwable)))
        }
      },
      suite("success") {
        test("succeeds with a value") {
          val actual = Handler.attempt("bar").apply(())
          assert(actual)(isSuccess(equalTo("bar")))
        }
      },
    ),
    suite("catchSome")(
      test("catches matching exception") {
        val http =
          Handler
            .fail(new IllegalArgumentException("boom"))
            .catchSome { case _: IllegalArgumentException =>
              Handler.succeed("bar")
            }
        assert(http.apply {})(isSuccess(equalTo("bar")))
      },
      test("keeps an error if doesn't catch anything") {
        val exception = new Throwable("boom")
        val http      =
          Handler
            .fail(exception)
            .catchSome { case _: ArithmeticException =>
              Handler.succeed("bar")
            }
        assert(http.apply {})(isFailure(equalTo(exception)))
      },
      test("doesn't affect the success") {
        val http =
          (Handler.succeed("bar"): Handler[Any, Throwable, Any, String]).catchSome { case _: Throwable =>
            Handler.succeed("baz")
          }
        assert(http.apply {})(isSuccess(equalTo("bar")))
      },
    ),
    suite("refineOrDie")(
      test("refines matching exception") {
        val http =
          Handler.fail(new IllegalArgumentException("boom")).refineOrDie { case _: IllegalArgumentException =>
            "fail"
          }
        assert(http.apply {})(isFailure(equalTo("fail")))
      },
      test("dies if doesn't catch anything") {
        val t    = new Throwable("boom")
        val http =
          Handler
            .fail(t)
            .refineOrDie { case _: IllegalArgumentException =>
              "fail"
            }
        assert(http.apply {})(isDie(equalTo(t)))
      },
      test("doesn't affect the success") {
        val http =
          (Handler.succeed("bar"): Handler[Any, Throwable, Any, String]).refineOrDie { case _: Throwable =>
            Handler.succeed("baz")
          }
        assert(http.apply {})(isSuccess(equalTo("bar")))
      },
    ),
    suite("orDie")(
      test("dies on failure") {
        val t    = new Throwable("boom")
        val http =
          Handler.fail(t).orDie
        assert(http.apply {})(isDie(equalTo(t)))
      },
      test("doesn't affect the success") {
        val http =
          (Handler.succeed("bar"): Handler[Any, Throwable, Any, String]).orDie
        assert(http.apply {})(isSuccess(equalTo("bar")))
      },
    ),
    suite("catchSomeDefect")(
      test("catches defect") {
        val t    = new IllegalArgumentException("boom")
        val http = Handler.die(t).catchSomeDefect { case _: IllegalArgumentException => Handler.succeed("OK") }
        assert(http.apply {})(isSuccess(equalTo("OK")))

      },
      test("catches thrown defects") {
        val http = Handler
          .fromFunction[Any] { _ => throw new IllegalArgumentException("boom") }
          .catchSomeDefect { case _: IllegalArgumentException => Handler.succeed("OK") }
        assert(http.apply {})(isSuccess(equalTo("OK")))
      },
      test("propagates non-caught defect") {
        val t    = new IllegalArgumentException("boom")
        val http = Handler.die(t).catchSomeDefect { case _: SecurityException => Handler.succeed("OK") }
        assert(http.apply {})(isDie(equalTo(t)))
      },
    ),
    suite("merge")(
      test("merges error into success") {
        val http = Handler.fail(1).merge
        assert(http.apply {})(isSuccess(equalTo(1)))
      },
    ),
  ) @@ timeout(10 seconds)
}
