package zhttp.logging

import zhttp.logging.Logger.SourcePos
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
   * Modifies each transport
   */
  private def foreach(f: LoggerTransport => LoggerTransport): Logger = Logger(transports.map(f(_)))

  /**
   * Combines to loggers into one
   */
  def ++(other: Logger): Logger = self combine other

  /**
   * Combines to loggers into one
   */
  def combine(other: Logger): Logger = Logger(self.transports ++ other.transports)

  /**
   * Modifies the transports to read the log level from the passed environment
   * variable.
   */
  def detectLevelFromEnv(env: String): Logger = withLevel(LogLevel.detectFromEnv(env))

  /**
   * Dispatches the parameters to all the transports. Internally invoked by the
   * macro.
   */
  def dispatch(
    msg: String,
    cause: Option[Throwable],
    level: LogLevel,
    sourceLocation: Option[SourcePos],
  ): Unit = transports.foreach(_.log(msg, cause, level, sourceLocation))

  def isEnabled: Boolean = transports.exists(_.isEnabled)

  /**
   * Creates a new logger that will log messages that start with the given
   * prefix.
   */
  def startsWith(prefix: String): Logger = withFilter(_.startsWith(prefix))

  /**
   * Creates a new logger that only log messages that are accepted by the
   * provided filter.
   */
  def withFilter(filter: String => Boolean): Logger = foreach(_.withFilter(filter))

  /**
   * Modifies all the transports to support the given log format
   */
  def withFormat(format: Setup.LogFormat): Logger = foreach(_.withFormat(format))

  /**
   * Modifies the level for each transport. Messages that don't meet that level
   * will not be logged by any of the transports
   */
  def withLevel(level: LogLevel): Logger = foreach(_.withLevel(level))

  /**
   * Creates a new Logger with the provided tag
   */
  def withTag(tag: String): Logger = foreach(_.addTags(List(tag)))

  /**
   * Creates a new Logger with the provided tags
   */
  def withTags(tags: Iterable[String]): Logger = foreach(_.addTags(tags))

  /**
   * Creates a new Logger with the provided tags
   */
  def withTags(tags: String *): Logger = foreach(_.addTags(tags))



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
