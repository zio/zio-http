package zhttp.logging

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
  level: LogLevel,
  format: Setup.LogFormat,
  filter: String => Boolean,
  transport: Transport,
) { self =>
  private[zhttp] val isDebugEnabled: Boolean = self.level >= LogLevel.Debug
  private[zhttp] val isErrorEnabled: Boolean = self.level >= LogLevel.Error
  private[zhttp] val isInfoEnabled: Boolean  = self.level >= LogLevel.Info
  private[zhttp] val isTraceEnabled: Boolean = self.level >= LogLevel.Trace
  private[zhttp] val isWarnEnabled: Boolean  = self.level >= LogLevel.Warn

  private def buildLines(
    msg: String,
    throwable: Option[Throwable],
    logLevel: LogLevel,
    tags: List[String],
    enclosingClass: String,
    lineNumber: Int,
  ): List[LogLine] = {
    throwable.fold(
      List(LogLine(LocalDateTime.now(), thread, logLevel, msg, tags, throwable, enclosingClass, lineNumber)),
    ) { t =>
      List(
        LogLine(LocalDateTime.now(), thread, logLevel, msg, tags, throwable, enclosingClass, lineNumber),
        LogLine(
          LocalDateTime.now(),
          thread,
          logLevel,
          stackTraceAsString(t),
          tags,
          throwable,
          enclosingClass,
          lineNumber,
        ),
      )
    },
  }

  private def logMayBe(
    msg: String,
    throwable: Option[Throwable],
    logLevel: LogLevel,
    tags: List[String],
    enclosingClass: String,
    lineNumber: Int,
  ): Unit =
    if (filter(tags.mkString)) {
      buildLines(msg, throwable, logLevel, tags, enclosingClass, lineNumber).foreach { line =>
        self.log(format(line))
      }
    }

  private def stackTraceAsString(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  private def thread = Thread.currentThread()

  def debug(msg: String, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, None, LogLevel.Debug, tags, enclosingClass, lineNumber)

  def error(msg: String, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, None, LogLevel.Error, tags, enclosingClass, lineNumber)

  def error(
    msg: String,
    throwable: Throwable,
    tags: List[String],
    enclosingClass: String,
    lineNumber: Int,
  ): Unit =
    logMayBe(msg, Some(throwable), LogLevel.Error, tags, enclosingClass, lineNumber)

  def info(msg: String, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, None, LogLevel.Info, tags, enclosingClass, lineNumber)

  def log(msg: CharSequence): Unit = if (filter(msg.toString)) transport.run(msg)

  def trace(msg: String, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, None, LogLevel.Trace, tags, enclosingClass, lineNumber)

  def warn(msg: String, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, None, LogLevel.Warn, tags, enclosingClass, lineNumber)

  def withFilter(filter: String => Boolean): LoggerTransport = self.copy(filter = filter)

  def withFormat(format: LogLine => CharSequence): LoggerTransport = self.copy(format = format)

  def withFormat(format: LogFormat): LoggerTransport = self.copy(format = LogFormat.run(format))

  def withLevel(level: LogLevel): LoggerTransport = self.copy(level = level)
}

object LoggerTransport {
  def console: LoggerTransport = LoggerTransport(
    level = LogLevel.Disable,
    format = LogFormat.default,
    filter = _ => true,
    transport = Transport.UnsafeSync(println),
  )

  def file(filePath: Path): LoggerTransport = LoggerTransport(
    level = LogLevel.Disable,
    format = LogFormat.simple,
    filter = _ => true,
    transport = Transport.UnsafeFileSync(filePath),
  )

  trait Transport { self =>
    def run(line: CharSequence): Unit
  }

  object Transport {
    final case class UnsafeSync(log: CharSequence => Unit) extends Transport {
      override def run(line: CharSequence): Unit = log(line)

    }

    final case class UnsafeFileSync(path: Path) extends Transport {
      override def run(line: CharSequence): Unit = {
        Files.write(
          path,
          util.Arrays.asList(line),
          StandardOpenOption.APPEND,
          StandardOpenOption.CREATE,
        ): Unit
      }
    }

    case object Empty extends Transport {
      override def run(line: CharSequence): Unit = ()
    }
  }
}
