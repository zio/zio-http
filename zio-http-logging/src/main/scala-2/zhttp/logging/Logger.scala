package zhttp.logging

import zhttp.logging.frontend.ConsoleLogger
import zhttp.logging.macros.LoggerMacro
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

  def trace(msg: String): Unit = macro traceM
  def trace(msg: String, throwable: Throwable): Unit = macro traceTM
  def debug(msg: String): Unit = macro debugM
  def debug(msg: String, throwable: Throwable): Unit = macro debugTM
  def info(msg: String): Unit = macro infoM
  def info(msg: String, throwable: Throwable): Unit = macro infoTM
  def warn(msg: String): Unit = macro warnM
  def warn(msg: String, throwable: Throwable): Unit = macro warnTM
  def error(msg: String): Unit = macro errorM
  def error(msg: String, throwable: Throwable): Unit = macro errorTM

}

object Logger {
  import scala.language.experimental.macros
  final def getLogger: Logger = macro LoggerMacro.getLoggerImpl
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
