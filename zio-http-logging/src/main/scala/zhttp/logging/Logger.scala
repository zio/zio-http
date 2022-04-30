package zhttp.logging

import zhttp.logging.macros.LoggerMacroExtensions

import java.nio.file.Path

/**
 * This is the base class for all logging operations. Logger is a collection of
 * LoggerTransports. Internally whenever a message needs to be logged, it is
 * broadcasted to all the available transports. The transports can internally
 * decide what to do with the mssage and discard it if the message or the level
 * is not relevant to the transport.
 */
final case class Logger(transports: List[LoggerTransport]) extends LoggerMacroExtensions { self =>

  /**
   * Combines to loggers into one
   */
  def ++(other: Logger): Logger = self combine other

  /**
   * Modifies the transports to read the log level from the environment variable
   * "ZHTTP_LOG"
   */
  def autodetectLevel: Logger = withLevel(LogLevel.detectFromEnv)

  /**
   * Combines to loggers into one
   */
  def combine(other: Logger): Logger = Logger(self.transports ++ other.transports)

  /**
   * Modifies each transport
   */
  def foreachTransport(f: LoggerTransport => LoggerTransport): Logger = Logger(transports.map(t => f(t)))

  /**
   * Creates a new logger that will log messages that start with the given
   * prefix.
   */
  def startsWith(prefix: String): Logger = withFilter(_.startsWith(prefix))

  /**
   * Modifies all the transports to only log messages that are accepted by the
   * provided filter.
   */
  def withFilter(filter: String => Boolean): Logger = foreachTransport(_.withFilter(filter))

  /**
   * Modifies all the transports to support the given log format
   */
  def withFormat(format: Setup.LogFormat): Logger = foreachTransport(_.withFormat(format))

  /**
   * Modifies the level for each transport. Messages that don't meet that level
   * will not be logged by any of the transports
   */
  def withLevel(level: LogLevel): Logger = foreachTransport(_.withLevel(level))

  /**
   * Adds a new transport to the logger
   */
  def withTransport(transport: LoggerTransport): Logger = copy(transports = transport :: self.transports)
}

object Logger {
  def apply(transport: LoggerTransport): Logger = Logger(List(transport))

  def console: Logger = Logger(List(LoggerTransport.console))

  def file(path: Path): Logger = Logger(List(LoggerTransport.file(path)))

  def make: Logger = Logger(Nil)

  final case class SourcePos(file: String, line: Int)
}
