package zhttp.logging

import zhttp.logging.Logger.SourcePos

import java.io.{PrintWriter, StringWriter}
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.LocalDateTime
import java.util

/**
 * Provides a way to build and configure transports for logging. Transports are
 * used to, format and serialize LogLines and them to a backend.
 */
private[logging] abstract class LoggerTransport(
  format: LogFormat = LogFormat.minimal,
  val level: LogLevel = LogLevel.Error,
  filter: String => Boolean = _ => true,
  tags: List[String] = Nil,
) { self =>

  def run(charSequence: CharSequence): Unit

  final private[zhttp] val isDebugEnabled: Boolean = self.level >= LogLevel.Debug
  final private[zhttp] val isErrorEnabled: Boolean = self.level >= LogLevel.Error
  final private[zhttp] val isInfoEnabled: Boolean  = self.level >= LogLevel.Info
  final private[zhttp] val isTraceEnabled: Boolean = self.level >= LogLevel.Trace
  final private[zhttp] val isWarnEnabled: Boolean  = self.level >= LogLevel.Warn

  final private def buildLines(
    msg: String,
    throwable: Option[Throwable],
    logLevel: LogLevel,
    tags: List[String],
    sourceLocation: Option[SourcePos],
  ): List[LogLine] = {
    throwable.fold(
      List(LogLine(LocalDateTime.now(), thread, logLevel, msg, tags, throwable, sourceLocation)),
    ) { t =>
      List(
        LogLine(LocalDateTime.now(), thread, logLevel, msg, tags, throwable, sourceLocation),
        LogLine(
          LocalDateTime.now(),
          thread,
          logLevel,
          stackTraceAsString(t),
          tags,
          throwable,
          sourceLocation,
        ),
      )
    }
  }

  final def copy(
    format: LogFormat = self.format,
    level: LogLevel = self.level,
    filter: String => Boolean = self.filter,
    tags: List[String] = self.tags,
  ): LoggerTransport = new LoggerTransport(format, level, filter, tags) {
    override def run(charSequence: CharSequence): Unit = self.run(charSequence)
  }

  final private def stackTraceAsString(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  final private def thread = Thread.currentThread()

  final def addTags(tags: Iterable[String]): LoggerTransport = self.copy(tags = self.tags ++ tags)

  final def dispatch(
    msg: String,
    cause: Option[Throwable],
    level: LogLevel,
    sourceLocation: Option[SourcePos],
  ): Unit =
    if (self.level <= level) {
      buildLines(msg, cause, level, self.tags, sourceLocation).foreach { line =>
        if (filter(format(line))) run(format(line))
      }
    }

  /**
   * Converts the current LoggerTransport to a Logger.
   */
  final def toLogger: Logger = Logger(List(self))

  final def withFilter(filter: String => Boolean): LoggerTransport = self.copy(filter = filter)

  final def withFormat(format: LogFormat): LoggerTransport = self.copy(format = format)

  final def withLevel(level: LogLevel): LoggerTransport = self.copy(level = level)

  final def withTags(tags: List[String]): LoggerTransport = self.copy(tags = tags)

}

object LoggerTransport {
  val empty: LoggerTransport = new LoggerTransport() {
    override def run(charSequence: CharSequence): Unit = ()
  }

  def console: LoggerTransport = new LoggerTransport(format = LogFormat.colored) {
    override def run(charSequence: CharSequence): Unit = println(charSequence)
  }

  def file(path: Path): LoggerTransport = new LoggerTransport() {
    override def run(charSequence: CharSequence): Unit = Files.write(
      path,
      util.Arrays.asList(charSequence),
      StandardOpenOption.APPEND,
      StandardOpenOption.CREATE,
    ): Unit
  }
}
