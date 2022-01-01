package zhttp.middleware

import zhttp.http.{HExitAssertion, Http, Middleware}
import zhttp.internal.UnsafeRef
import zio.UIO
import zio.duration._
import zio.test.Assertion.{equalTo, isLeft, isNone, isSome}
import zio.test.environment.TestClock
import zio.test.{DefaultRunnableSpec, _}

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
        val app = Http.identity[Int] @@@ mid
        assertM(app("1"))(equalTo("1"))
      } +
      testM("constant") {
        val mid = Middleware.fromHttp(Http.succeed("OK"))
        val app = Http.succeed(1) @@@ mid
        assertM(app(()))(equalTo("OK"))
      } +
      testM("interceptZIO") {
        val ref = UnsafeRef(0)
        val mid = Middleware.interceptZIO[Int, Int](i => UIO(i * 10))((i, j) => ref.set(i + j))
        val app = Http.identity[Int] @@@ mid
        assertM(app(1) *> ref.get)(equalTo(11))
      } +
      testM("orElse") {
        val mid = Middleware.fail("left") <> Middleware.fail("right")
        val app = Http.empty @@@ mid
        assertM(app(()).flip)(isSome(equalTo("right")))
      } +
      testM("combine") {
        val mid1 = increment
        val mid2 = increment
        val mid  = mid1 compose mid2
        val app  = Http.identity[Int] @@@ mid
        assertM(app(0))(equalTo(4))
      } +
      testM("flatMap") {
        val mid = increment.flatMap(i => Middleware.succeed(i + 1))
        val app = Http.identity[Int] @@@ mid
        assertM(app(0))(equalTo(3))
      } +
      testM("mapZIO") {
        val mid = increment.mapZIO(i => UIO(i + 1))
        val app = Http.identity[Int] @@@ mid
        assertM(app(0))(equalTo(3))
      } +
      testM("race") {
        val mid = Middleware.succeed('A').delay(2 second) race Middleware.succeed("B").delay(1 second)
        val app = Http.succeed(1) @@@ mid
        assertM(app(()) <& TestClock.adjust(3 second))(equalTo("A"))
      }
  }
}
