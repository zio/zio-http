package zhttp.http

import zio.duration._
import zio.test.Assertion.{equalTo, isLeft, isNone, isSome}
import zio.test.environment.TestClock
import zio.test.{DefaultRunnableSpec, assert, assertM}
import zio.{Ref, UIO}

object MiddlewareSpec extends DefaultRunnableSpec with HExitAssertion {
  def spec = suite("Middleware") {
    val increment = Middleware.codec[Int, Int](decoder = _ + 1, encoder = _ + 1)

    testM("empty") {
      val http = Http.empty
      val app  = Middleware.identity(http)
      assertM(app(()).either)(isLeft(isNone))
    } +
      testM("codec") {
        val mid = Middleware.codec[String, Int](decoder = _.toInt, encoder = _.toString)
        val app = Http.identity[Int] @@ mid
        assertM(app("1"))(equalTo("1"))
      } +
      testM("constant") {
        val mid = Middleware.fromHttp(Http.succeed("OK"))
        val app = Http.succeed(1) @@ mid
        assertM(app(()))(equalTo("OK"))
      } +
      testM("interceptZIO") {
        for {
          ref <- Ref.make(0)
          mid = Middleware.interceptZIO[Int, Int](i => UIO(i * 10))((i, j) => ref.set(i + j))
          app = Http.identity[Int] @@ mid
          _ <- app(1)
          i <- ref.get
        } yield assert(i)(equalTo(11))
      } +
      testM("orElse") {
        val mid = Middleware.fail("left") <> Middleware.fail("right")
        val app = Http.empty @@ mid
        assertM(app(()).flip)(isSome(equalTo("right")))
      } +
      testM("combine") {
        val mid1 = increment
        val mid2 = increment
        val mid  = mid1 compose mid2
        val app  = Http.identity[Int] @@ mid
        assertM(app(0))(equalTo(4))
      } +
      testM("flatMap") {
        val mid = increment.flatMap(i => Middleware.succeed(i + 1))
        val app = Http.identity[Int] @@ mid
        assertM(app(0))(equalTo(3))
      } +
      testM("mapZIO") {
        val mid = increment.mapZIO(i => UIO(i + 1))
        val app = Http.identity[Int] @@ mid
        assertM(app(0))(equalTo(3))
      } +
      testM("race") {
        val mid = Middleware.succeed('A').delay(2 second) race Middleware.succeed("B").delay(1 second)
        val app = Http.succeed(1) @@ mid
        assertM(app(()) <& TestClock.adjust(3 second))(equalTo("B"))
      } +
      suite("timeOut") {
        testM("application completes") {
          val mid = Middleware.succeed('A').delay(2 second).timeout(1 second)
          val app = Http.succeed(1) @@ mid
          assertM(app(()) <& TestClock.adjust(3 second))(isNone)
        } +
          testM("timeout operator timed out") {
            val mid = Middleware.succeed('A').delay(1 second).timeout(2 second)
            val app = Http.succeed(1) @@ mid
            assertM(app(()) <& TestClock.adjust(3 second))(equalTo(Some('A')))
          } +
          testM("other middleware completes") {
            val mid = Middleware.succeed('A').delay(1 second)
            val app = Http.succeed(1) @@ mid @@ Middleware.timeout[Char](2 second)
            assertM(app(()) <& TestClock.adjust(3 second))(equalTo(Some('A')))
          } +
          testM("timeout middleware timed out") {
            val mid = Middleware.succeed('A').delay(2 second)
            val app = Http.succeed(1) @@ mid @@ Middleware.timeout[Char](1 second)
            assertM(app(()) <& TestClock.adjust(3 second))(isNone)
          }
      } +
      suite("ifThenElse") {
        val mid = Middleware.ifThenElse[Int](_ > 5)(
          isTrue = i => Middleware.succeed(i + 1),
          isFalse = i => Middleware.succeed(i - 1),
        )
        testM("isTrue") {
          val app = Http.identity[Int] @@ mid
          assertM(app(10))(equalTo(11))
        } +
          testM("isFalse") {
            val app = Http.identity[Int] @@ mid
            assertM(app(1))(equalTo(0))
          }
      } +
      suite("ifThenElseZIO") {
        val mid = Middleware.ifThenElseZIO[Int](i => UIO(i > 5))(
          isTrue = i => Middleware.succeed(i + 1),
          isFalse = i => Middleware.succeed(i - 1),
        )
        testM("isTrue") {
          val app = Http.identity[Int] @@ mid
          assertM(app(10))(equalTo(11))
        } +
          testM("isFalse") {
            val app = Http.identity[Int] @@ mid
            assertM(app(1))(equalTo(0))
          }
      }
  }
}
