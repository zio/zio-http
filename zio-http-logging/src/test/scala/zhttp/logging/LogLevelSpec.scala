package zhttp.logging

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
    test("any value with the exception of defined values for LogLevel should be set to Error log level.") {
      checkAll(Gen.fromIterable(List("not defined", "unknown", "disable"))) { level =>
        assertTrue(LogLevel.fromString(level).isEmpty)
      }
    },
  )
}
