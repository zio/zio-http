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

  inline def trace(msg: String, tags: List[String]): Unit = $ { logTraceImpl('transports)('msg)('tags) }
  inline def debug(msg: String, tags: List[String]): Unit = $ { logDebugImpl('transports)('msg)('tags) }
  inline def info(msg: String, tags: List[String]): Unit = $ { logInfoImpl('transports)('msg)('tags) }
  inline def warn(msg: String, tags: List[String]): Unit = $ { logWarnImpl('transports)('msg)('tags) }
  inline def error(msg: String, tags: List[String]): Unit = $ { logErrorImpl('transports)('msg)('tags) }
  inline def error(msg: String, throwable: Throwable, tags: List[String]): Unit = ${logErrorWithCauseImpl('transports)('throwable)('msg)('tags)}
}

object Logger {
  def make: Logger                            = Logger(Nil)
  def makeConsoleLogger(name: String): Logger = Logger(Nil)
    .withTransport(LoggerTransport.console(name))
}
