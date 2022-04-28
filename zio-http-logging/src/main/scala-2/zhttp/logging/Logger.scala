package zhttp.logging

import zhttp.logging.macros.LoggerMacro._

/**
 * Core Logger class.
 */
final case class Logger(transports: List[LoggerTransport]) { self =>
  import scala.language.experimental.macros
  def foreachTransport(f: LoggerTransport => LoggerTransport): Logger = Logger(transports.map(t => f(t)))
  def withFormat(format: Setup.LogFormat): Logger                     = foreachTransport(_.withFormat(format))
  def withLevel(level: LogLevel): Logger                              = foreachTransport(_.withLevel(level))
  def withTransport(transport: LoggerTransport): Logger               = copy(transports = transport :: self.transports)
  def withFilter(filter: String => Boolean): Logger                   = foreachTransport(_.withFilter(filter))

  def trace(msg: String, tags: List[String]): Unit = macro logTraceImpl
  def debug(msg: String, tags: List[String]): Unit = macro logDebugImpl
  def info(msg: String, tags: List[String]): Unit = macro logInfoImpl
  def warn(msg: String, tags: List[String]): Unit = macro logWarnImpl
  def error(msg: String, tags: List[String]): Unit = macro logErrorImpl
  def error(msg: String, throwable: Throwable, tags: List[String]): Unit =
    macro logErrorWithCauseImpl
}

object Logger {
  def make: Logger              = Logger(Nil)
  def makeConsoleLogger: Logger = Logger(Nil)
    .withTransport(LoggerTransport.console)
}
