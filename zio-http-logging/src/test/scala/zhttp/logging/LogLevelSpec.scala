package zhttp.logging

import zio.test._

object LogLevelSpec extends DefaultRunnableSpec {
  def spec = suite("LogLevelSpec")(
    test("log level order") {
      val act    = LogLevel.all
      val exp    = List(LogLevel.Trace, LogLevel.Debug, LogLevel.Info, LogLevel.Warn, LogLevel.Error)
      val sorted = act.sortBy(_.id)
      assertTrue(sorted == exp)
    },
    testM("encode decode") {
      checkAll(Gen.fromIterable(LogLevel.all)) { level =>
        assertTrue(LogLevel.fromString(level.toString) == level)
      }
    },
  )
}
