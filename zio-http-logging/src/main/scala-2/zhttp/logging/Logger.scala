package zhttp.logging

import zhttp.logging.frontend.ConsoleLogger
import zhttp.logging.macros.LoggerMacro._

final class Logger(configuration: Configuration) {

  import LogLevel._

  private[zhttp] val logger = new ConsoleLogger(configuration)

  @inline def name = configuration.loggerName

  @inline def isTraceEnabled: Boolean = configuration.logLevel >= TRACE
  @inline def isDebugEnabled: Boolean = configuration.logLevel >= DEBUG
  @inline def isInfoEnabled: Boolean  = configuration.logLevel >= INFO
  @inline def isWarnEnabled: Boolean  = configuration.logLevel >= WARN
  @inline def isErrorEnabled: Boolean = configuration.logLevel >= ERROR

  import scala.language.experimental.macros

  def trace(msg: String): Unit = macro traceImpl
  def debug(msg: String): Unit = macro debugImpl
  def info(msg: String): Unit = macro infoImpl
  def warn(msg: String): Unit = macro warnImpl
  def error(msg: String): Unit = macro errorImpl
  def error(msg: String, throwable: Throwable): Unit = macro errorImplT

}

object Logger {
  final def getLogger(logLevel: LogLevel)                     =
    new Logger(configuration = Configuration(getClass.getSimpleName, logLevel, LogFormat.default))
  final def getLogger(loggerName: String)                     =
    new Logger(configuration = Configuration(loggerName, logLevel = LogLevel.INFO, LogFormat.default))
  final def getLogger(loggerName: String, logLevel: LogLevel) =
    new Logger(configuration = Configuration(loggerName, logLevel, LogFormat.default))
  final def getLogger(
    loggerName: String,
    logLevel: LogLevel,
    logFormat: LogFormat,
  ) = new Logger(configuration = Configuration(loggerName, logLevel, logFormat))
}
