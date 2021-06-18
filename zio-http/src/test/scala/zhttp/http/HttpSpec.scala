package zhttp.http

import zio._
import zio.duration.durationInt
import zio.test.Assertion._
import zio.test.TestAspect.timeout
import zio.test._

object HttpSpec extends DefaultRunnableSpec with HttpResultAssertion {
  def spec = suite("Http")(
    suite("flatMap")(
      test("should flatten") {
        val app    = Http.identity[Int].flatMap(i => Http.succeed(i + 1))
        val actual = app.execute(0).evaluate
        assert(actual)(isSuccess(equalTo(1)))
      },
      test("should be stack-safe") {
        val i      = 100000
        val app    = (0 until i).foldLeft(Http.identity[Int])((i, _) => i.flatMap(c => Http.succeed(c + 1)))
        val actual = app.execute(0).evaluate
        assert(actual)(isSuccess(equalTo(i)))
      },
    ),
    suite("orElse")(
      test("should succeed") {
        val a1     = Http.succeed(1)
        val a2     = Http.succeed(2)
        val a      = a1 <> a2
        val actual = a.execute(()).evaluate
        assert(actual)(isSuccess(equalTo(1)))
      },
      test("should fail with first") {
        val a1     = Http.fail("A")
        val a2     = Http.succeed("B")
        val a      = a1 <> a2
        val actual = a.execute(()).evaluate
        assert(actual)(isSuccess(equalTo("B")))
      },
    ),
    suite("fail")(
      test("should fail") {
        val a      = Http.fail(100)
        val actual = a.execute(()).evaluate
        assert(actual)(isFailure(equalTo(100)))
      },
    ),
    suite("foldM")(
      test("should catch") {
        val a      = Http.fail(100).catchAll(e => Http.succeed(e + 1))
        val actual = a.execute(0).evaluate
        assert(actual)(isSuccess(equalTo(101)))
      },
    ),
    suite("identity")(
      test("should passthru") {
        val a      = Http.identity[Int]
        val actual = a.execute(0).evaluate
        assert(actual)(isSuccess(equalTo(0)))
      },
    ),
    suite("collect")(
      test("should succeed") {
        val a      = Http.collect[Int] { case 1 => "OK" }
        val actual = a.execute(1).evaluate
        assert(actual)(isSuccess(equalTo("OK")))
      },
      test("should fail") {
        val a      = Http.collect[Int] { case 1 => "OK" }
        val actual = a.execute(0).evaluate
        assert(actual)(isEmpty)
      },
    ),
    suite("combine")(
      test("should resolve first") {
        val a      = Http.collect[Int] { case 1 => "A" }
        val b      = Http.collect[Int] { case 2 => "B" }
        val actual = (a +++ b).execute(1).evaluate
        assert(actual)(isSuccess(equalTo("A")))
      },
      test("should resolve second") {
        val a      = Http.empty
        val b      = Http.succeed("A")
        val actual = (a +++ b).execute(()).evaluate
        assert(actual)(isSuccess(equalTo("A")))
      },
      test("should resolve second") {
        val a      = Http.collect[Int] { case 1 => "A" }
        val b      = Http.collect[Int] { case 2 => "B" }
        val actual = (a +++ b).execute(2).evaluate
        assert(actual)(isSuccess(equalTo("B")))
      },
      test("should not resolve") {
        val a      = Http.collect[Int] { case 1 => "A" }
        val b      = Http.collect[Int] { case 2 => "B" }
        val actual = (a +++ b).execute(3).evaluate
        assert(actual)(isEmpty)
      },
      test("should be stack-safe") {
        val i      = 100000
        val a      = Http.collect[Int]({ case i => i + 1 })
        val app    = (0 until i).foldLeft(a)((i, _) => i +++ a)
        val actual = app.execute(0).evaluate
        assert(actual)(isSuccess(equalTo(1)))
      },
    ),
    suite("asEffect")(
      testM("should resolve") {
        val a      = Http.collect[Int] { case 1 => "A" }
        val actual = a.execute(1).evaluate.asEffect
        assertM(actual)(equalTo("A"))
      },
      testM("should complete") {
        val a      = Http.collect[Int] { case 1 => "A" }
        val actual = a.execute(2).evaluate.asEffect.either
        assertM(actual)(isLeft(isNone))
      },
    ),
    suite("collectM")(
      test("should be empty") {
        val a      = Http.collectM[Int] { case 1 => UIO("A") }
        val actual = a.execute(2).evaluate
        assert(actual)(isEmpty)
      },
      test("should resolve") {
        val a      = Http.collectM[Int] { case 1 => UIO("A") }
        val actual = a.execute(1).evaluate
        assert(actual)(isEffect)
      },
      test("should resolve second effect") {
        val a      = Http.empty.flatten
        val b      = Http.succeed("B")
        val actual = (a +++ b).execute(2).evaluate
        assert(actual)(isSuccess(equalTo("B")))
      },
    ),
    suite("route")(
      test("should delegate to its HTTP apps") {
        val app    = Http.route[Int]({
          case 1 => Http.succeed(1)
          case 2 => Http.succeed(2)
        })
        val actual = app.execute(2).evaluate
        assert(actual)(isSuccess(equalTo(2)))
      },
      test("should be empty if no matches") {
        val app    = Http.route[Int](Map.empty)
        val actual = app.execute(1).evaluate
        assert(actual)(isEmpty)
      },
    ),
    suite("tap")(
      testM("taps the successs") {
        for {
          r <- Ref.make(0)
          app = Http.succeed(1).tap(v => Http.fromEffect(r.set(v)))
          _   <- app.execute(()).evaluate.asEffect
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("tapM")(
      testM("taps the successs") {
        for {
          r <- Ref.make(0)
          app = Http.succeed(1).tapM(r.set)
          _   <- app.execute(()).evaluate.asEffect
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("tapError")(
      testM("taps the error") {
        for {
          r <- Ref.make(0)
          app = Http.fail(1).tapError(v => Http.fromEffect(r.set(v)))
          _   <- app.execute(()).evaluate.asEffect.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("tapErrorM")(
      testM("taps the error") {
        for {
          r <- Ref.make(0)
          app = Http.fail(1).tapErrorM(r.set)
          _   <- app.execute(()).evaluate.asEffect.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("tapAll")(
      testM("taps the success") {
        for {
          r <- Ref.make(0)
          app = (Http.succeed(1): Http[Any, Any, Any, Int])
            .tapAll(_ => Http.empty, v => Http.fromEffect(r.set(v)), Http.empty)
          _   <- app.execute(()).evaluate.asEffect
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
      testM("taps the failure") {
        for {
          r <- Ref.make(0)
          app = (Http.fail(1): Http[Any, Int, Any, Any])
            .tapAll(v => Http.fromEffect(r.set(v)), _ => Http.empty, Http.empty)
          _   <- app.execute(()).evaluate.asEffect.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
      testM("taps the empty") {
        for {
          r <- Ref.make(0)
          app = (Http.empty: Http[Any, Any, Any, Any])
            .tapAll(_ => Http.empty, _ => Http.empty, Http.fromEffect(r.set(1)))
          _   <- app.execute(()).evaluate.asEffect.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("tapAllM")(
      testM("taps the success") {
        for {
          r <- Ref.make(0)
          app = (Http.succeed(1): Http[Any, Any, Any, Int]).tapAllM(_ => ZIO.unit, r.set, ZIO.unit)
          _   <- app.execute(()).evaluate.asEffect
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
      testM("taps the failure") {
        for {
          r <- Ref.make(0)
          app = (Http.fail(1): Http[Any, Int, Any, Any]).tapAllM(r.set, _ => ZIO.unit, ZIO.unit)
          _   <- app.execute(()).evaluate.asEffect.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
      testM("taps the empty") {
        for {
          r <- Ref.make(0)
          app = (Http.empty: Http[Any, Any, Any, Any])
            .tapAllM(_ => ZIO.unit, _ => ZIO.unit, r.set(1))
          _   <- app.execute(()).evaluate.asEffect.ignore
          res <- r.get
        } yield assert(res)(equalTo(1))
      },
    ),
    suite("provide")(
      testM("provide") {
        val app = Http.fromEffect(ZIO.environment[Int]).provide(1).execute(1).evaluate.asEffect
        assertM(app)(equalTo(1))
      },
      testM("foldM") {
        val app = (Http.fromEffect(ZIO.environment[Int]) *> Http.succeed(1)).provide(1).execute(1).evaluate.asEffect
        assertM(app)(equalTo(1))
      },
    ),
    suite("provideSome")(
      testM("provideSome") {
        trait HasInt {
          val int: Int
        }

        val needsEnv = Http.fromEffect(for {
          int <- ZIO.environment[HasInt]
        } yield int.int)

        val app = needsEnv.provideSome[Any](_ => {
          new HasInt {
            val int = 2
          }
        })
        val res = app.execute(1).evaluate.asEffect
        assertM(res)(equalTo(2))
      },
      testM("foldM 1") {
        trait HasInt {
          val int: Int
        }

        val needsEnv = Http.fromEffect(for {
          int <- ZIO.environment[HasInt]
        } yield int.int)

        val app = (needsEnv *> Http.succeed(1)).provideSome[Any](_ => {
          new HasInt { val int = 1 }
        })
        val res = app.execute(1).evaluate.asEffect

        assertM(res)(equalTo(1))
      },
      testM("it provides parts of the environment") {
        trait HasInt    {
          val int: Int
        }
        trait HasString {
          val string: String
        }

        val needsEnv = Http.fromEffect(for {
          _   <- ZIO.environment[HasString]
          int <- ZIO.environment[HasInt]
        } yield int.int)

        val app = (needsEnv *> needsEnv).provideSome[HasInt](env =>
          new HasInt with HasString {
            val int    = env.int
            val string = "String"
          },
        )

        val res = app.provide(new HasInt { val int = 2 }).execute(1).evaluate.asEffect
        assertM(res)(equalTo(2))
      },
    ),
    suite("provideLayer")(
      testM("provideLayer") {
        val app = Http.fromEffect(for {
          _   <- ZIO.service[String]
          int <- ZIO.service[Int]
        } yield int)

        val res =
          app.provideLayer(ZLayer.succeed("String") ++ ZLayer.succeed(2)).execute(1).evaluate.asEffect
        assertM(res)(equalTo(2))
      },
      testM("foldM") {
        val app = Http.fromEffect(for {
          _   <- ZIO.service[String]
          int <- ZIO.service[Int]
        } yield int) *> Http.succeed(2)

        val res =
          app.provideLayer(ZLayer.succeed("String") ++ ZLayer.succeed(2)).execute(1).evaluate.asEffect
        assertM(res)(equalTo(2))
      },
    ),
  ) @@ timeout(10 seconds)
}
