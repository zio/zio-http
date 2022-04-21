package zhttp.logging
import zhttp.logging.frontend.LogFrontend
import zhttp.logging.macros.LoggerMacro._

final class InternalLogger(val frontend: LogFrontend) {
  import scala.language.experimental.macros

  def trace(msg: String, tags: List[String]): Unit = macro logTraceImpl
  def info(msg: String, tags: List[String]): Unit = macro logInfoImpl
  def debug(msg: String, tags: List[String]): Unit = macro logDebugImpl
  def warn(msg: String, tags: List[String]): Unit = macro logWarnImpl
  def error(msg: String, tags: List[String]): Unit = macro logErrorImpl
  def error(msg: String, throwable: Throwable, tags: List[String]): Unit = macro logErrorWithCauseImpl
}

object InternalLogger {
  def make(loggerTransport: LoggerTransport): InternalLogger =
    new InternalLogger(LogFrontend.default(loggerTransport))
}
