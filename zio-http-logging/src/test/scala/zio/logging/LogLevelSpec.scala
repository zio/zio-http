package zio.logging

import zio.test._

object LogLevelSpec extends ZIOSpecDefault {
  def spec = suite("LogLevelSpec")(
    test("log level order") {
      val act    = LogLevel.all
      val exp    = List(LogLevel.Trace, LogLevel.Debug, LogLevel.Info, LogLevel.Warn, LogLevel.Error)
      val sorted = act.sortBy(_.id)
      assertTrue(sorted == exp)
    },
    test("encode decode") {
      checkAll(Gen.fromIterable(LogLevel.all)) { level =>
        assertTrue(LogLevel.fromString(level.toString).contains(level))
      }
    },
    test("any invalid value should not decode") {
      checkAll(Gen.fromIterable(List("not defined", "unknown", "disable"))) { level =>
        assertTrue(LogLevel.fromString(level).isEmpty)
      }
    },
  )
}
