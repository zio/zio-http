package zhttp.logging

import zio.test._

object LogLevelSpec extends DefaultRunnableSpec {
  val LogLevelList =
    List(LogLevel.Info, LogLevel.Error, LogLevel.Warn, LogLevel.Debug, LogLevel.Trace, LogLevel.Disable)

  def spec = suite("LogLevelSpec")(
    test("log level order") {
      val act    = LogLevelList
      val exp    = List(LogLevel.Trace, LogLevel.Debug, LogLevel.Info, LogLevel.Warn, LogLevel.Error, LogLevel.Disable)
      val sorted = act.sortBy(_.id)
      assertTrue(sorted == exp)
    },
    testM("encode decode") {
      checkAll(Gen.fromIterable(LogLevelList)) { level =>
        assertTrue(LogLevel.fromString(level.toString) == level)
      }
    },
  )
}
