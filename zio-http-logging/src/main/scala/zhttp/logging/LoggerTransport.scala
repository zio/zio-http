package zhttp.logging

import zhttp.logging.Logger.SourcePos
import zhttp.logging.LoggerTransport.Transport

import java.io.{PrintWriter, StringWriter}
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.LocalDateTime
import java.util

/**
 * Provides a way to build and configure transports for logging. Transports are
 * used to, format and serialize LogLines and them to a backend.
 */
private[logging] final case class LoggerTransport(
  format: Setup.LogFormat,
  level: LogLevel = LogLevel.Disable,
  filter: String => Boolean = _ => true,
  transport: Transport = Transport.empty,
  tags: List[String] = Nil,
) { self =>
  private[zhttp] val isDebugEnabled: Boolean = self.level >= LogLevel.Debug
  private[zhttp] val isErrorEnabled: Boolean = self.level >= LogLevel.Error
  private[zhttp] val isInfoEnabled: Boolean  = self.level >= LogLevel.Info
  private[zhttp] val isTraceEnabled: Boolean = self.level >= LogLevel.Trace
  private[zhttp] val isWarnEnabled: Boolean  = self.level >= LogLevel.Warn
  private[zhttp] val isEnabled: Boolean      = self.level != LogLevel.Disable

  private def buildLines(
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

  private def stackTraceAsString(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  private def thread = Thread.currentThread()

  def addTags(tags: Iterable[String]): LoggerTransport = self.copy(tags = self.tags ++ tags)

  def log(
    msg: String,
    cause: Option[Throwable],
    level: LogLevel,
    sourceLocation: Option[SourcePos],
  ): Unit =
    if (this.level >= level) {
      buildLines(msg, cause, level, self.tags.sorted, sourceLocation).foreach { line =>
        if (filter(format(line).toString)) transport.run(format(line))
      }
    }

  def withFilter(filter: String => Boolean): LoggerTransport = self.copy(filter = filter)

  def withFormat(format: LogFormat): LoggerTransport = self.copy(format = LogFormat.run(format))

  def withFormat(format: LogLine => CharSequence): LoggerTransport = self.copy(format = format)

  def withLevel(level: LogLevel): LoggerTransport = self.copy(level = level)

  def withTags(tags: List[String]): LoggerTransport = self.copy(tags = tags)

}

object LoggerTransport {
  def console: LoggerTransport = LoggerTransport(
    format = LogFormat.default,
    level = LogLevel.Disable,
    filter = _ => true,
    transport = Transport.unsafeSync(println),
  )

  def file(filePath: Path): LoggerTransport = LoggerTransport(
    format = LogFormat.simple,
    level = LogLevel.Disable,
    filter = _ => true,
    transport = Transport.unsafeFileSync(filePath),
  )

  trait Transport { self =>
    def run(line: CharSequence): Unit
  }

  object Transport {
    val empty: Transport = (_: CharSequence) => ()

    def unsafeFileSync(path: Path): Transport = unsafeSync { line =>
      Files.write(
        path,
        util.Arrays.asList(line),
        StandardOpenOption.APPEND,
        StandardOpenOption.CREATE,
      ): Unit
    }

    def unsafeSync(log: CharSequence => Unit): Transport = (line: CharSequence) => log(line)
  }
}
