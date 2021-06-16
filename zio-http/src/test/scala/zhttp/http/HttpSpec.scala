package zhttp.http

import zio.duration.durationInt
import zio.test.Assertion._
import zio.test.TestAspect.timeout
import zio.test._
import zio.{Has, Ref, UIO, ZIO, ZManaged}

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
    suite("wrap")(
      testM("allows to effectfully wrap Http execution") {
        for {
          ref <- Ref.make(0)
          app     = Http.fromFunction[Int](i => i)
          wrapped = app.wrap((a: Int, eff) =>
            ZManaged.make(ref.set(a) *> ZIO.succeed(ref))(_ => ref.update(_ - 1)).use { r =>
              eff *> r.get
            },
          )
          refBefore <- ref.get
          res       <- wrapped.execute(10).evaluate.asEffect
          refAfter  <- ref.get
        } yield assert(refBefore)(equalTo(0)) &&
          assert(res)(equalTo(10)) &&
          assert(refAfter)(equalTo(9))
      },
      testM("infers the environment properly") {
        val app     = Http.fromEffect(ZIO.service[Int])
        val wrapped = app.wrap((_: Any, eff) => ZIO.service[String].flatMap(s => eff.map(i => s"$s: $i")))
        val res     = wrapped.execute(0).evaluate.asEffect.provide(Has("string") ++ Has(1))
        assertM(res)(equalTo("string: 1"))
      },
    ),
  ) @@ timeout(10 seconds)
}
