package zhttp.logging

import zhttp.logging.frontend.ConsoleLogger
import zhttp.logging.macros.LoggerMacro._
import zhttp.logging.macros.LoggerMacro

final class Logger(configuration: Configuration) {

  import LogLevel._

  private[zhttp] val logger = new ConsoleLogger(configuration)

  @inline def name = configuration.name

  @inline def isTraceEnabled: Boolean = configuration.level >= TRACE
  @inline def isDebugEnabled: Boolean = configuration.level >= DEBUG
  @inline def isInfoEnabled: Boolean  = configuration.level >= INFO
  @inline def isWarnEnabled: Boolean  = configuration.level >= WARN
  @inline def isErrorEnabled: Boolean = configuration.level >= ERROR

  import scala.language.experimental.macros

  inline def trace(inline msg: String, inline throwable: Throwable): Unit =  ${traceTM('this)('throwable)('msg)}
  inline def trace(inline msg: String): Unit =
  ${traceM('this)('msg)}

  inline def debug(inline msg: String, inline throwable: Throwable): Unit =  ${debugTM('this)('throwable)('msg)}
  inline def debug(inline msg: String): Unit =
  ${debugM('this)('msg)}

  inline def info(inline msg: String, inline throwable: Throwable): Unit =  ${infoTM('this)('throwable)('msg)}
  inline def info(inline msg: String): Unit =
  ${infoM('this)('msg)}

  inline def warn(inline msg: String, inline throwable: Throwable): Unit =  ${warnTM('this)('throwable)('msg)}
  inline def warn(inline msg: String): Unit =
  ${warnM('this)('msg)}

  inline def error(inline msg: String, inline throwable: Throwable): Unit =  ${errorTM('this)('throwable)('msg)}
  inline def error(inline msg: String): Unit =
  ${errorM('this)('msg)}

}

object Logger {
  import scala.language.experimental.macros
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
