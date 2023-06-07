package zio.http.netty.client

import zio._
import zio.test.Assertion.equalTo
import zio.test.TestAspect.withLiveClock
import zio.test.{ZIOSpecDefault, assertZIO}

object HappyEyeballsSpec extends ZIOSpecDefault {

  def spec = suite("Happy eyeballs spec") {
    test("happy eyeballs execution") {
      val start = java.lang.System.currentTimeMillis()

      def log(msg: String): UIO[Unit] = ZIO.succeed {
        val now    = java.lang.System.currentTimeMillis()
        val second = (now - start) / 1000L
        println(s"after ${second}s: $msg")
      }

      def printSleepPrint(sleep: Duration, name: String) =
        log(s"START: $name") *> ZIO.sleep(sleep) *> log(s"DONE: $name") *> ZIO.succeed(name)

      def printSleepFail(sleep: Duration, name: String) =
        log(s"START: $name") *> ZIO.sleep(sleep) *> log(s"FAIL: $name") *> ZIO.fail(
          new RuntimeException(s"FAIL: $name"),
        )

      // 0s-6s: 1st should be interrupted
      // 2s-3s: 2nd should fail
      // 3s-6s: 3rd succeeds
      // 5s-6s: 4th should be interrupted
      // -----:  5th shouldn't start
      val result = NettyConnectionPool
//        .executeHappyEyeballs(
//          Chunk(
//            printSleepPrint(10.seconds, "task1"),
//            printSleepFail(500.millis, "task2"),
//            printSleepPrint(1500.millis, "task3"),
//            printSleepPrint(1.seconds, "task4"),
//            printSleepPrint(1.seconds, "task5"),
//          ),
//          1.seconds,
//        )
        .executeHappyEyeballs(
          NonEmptyChunk(
            printSleepPrint(10.seconds, "task1"),
            printSleepFail(1.second, "task2"),
            printSleepPrint(3.seconds, "task3"),
            printSleepPrint(2.seconds, "task4"),
            printSleepPrint(2.seconds, "task5"),
          ),
          2.seconds,
        )
        .debug("HAPPY EYE BALLS")

      assertZIO(result)(equalTo("task3"))
    }
  } @@ withLiveClock

}
