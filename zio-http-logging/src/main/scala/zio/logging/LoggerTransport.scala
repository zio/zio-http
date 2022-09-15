package zio.logging

import zio.logging.Logger.SourcePos
import zio.{LogLevel => _, _}

import java.io.{PrintWriter, StringWriter}
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.LocalDateTime
import java.util

/**
 * Provides a way to build and configure transports for logging. Transports are
 * used to, format and serialize LogLines and them to a backend.
 */
private[logging] trait LoggerTransport { self =>
  def dispatch(
    msg: String,
    cause: Option[Throwable],
    level: LogLevel,
    sourceLocation: Option[SourcePos],
  ): Unit

  /**
   * Converts the current LoggerTransport to a Logger.
   */
  final def toLogger: Logger = Logger(List(self))

  def addTags(tags: Iterable[String]): LoggerTransport
  def withFilter(filter: String => Boolean): LoggerTransport
  def withFormat(format: LogFormat): LoggerTransport
  def withLevel(level: LogLevel): LoggerTransport
  def withTags(tags: List[String]): LoggerTransport

  val level: LogLevel

  private[zio] val isDebugEnabled: Boolean
  private[zio] val isErrorEnabled: Boolean
  private[zio] val isInfoEnabled: Boolean
  private[zio] val isTraceEnabled: Boolean
  private[zio] val isWarnEnabled: Boolean
}

object LoggerTransport {
  private[logging] abstract class DefaultLoggerTransport(
    format: LogFormat = LogFormat.inlineMinimal,
    val level: LogLevel = LogLevel.Error,
    filter: String => Boolean = _ => true,
    tags: List[String] = Nil,
  ) extends LoggerTransport {
    self =>

    final private[zio] val isDebugEnabled: Boolean = self.level <= LogLevel.Debug
    final private[zio] val isErrorEnabled: Boolean = self.level <= LogLevel.Error
    final private[zio] val isInfoEnabled: Boolean  = self.level <= LogLevel.Info
    final private[zio] val isTraceEnabled: Boolean = self.level <= LogLevel.Trace
    final private[zio] val isWarnEnabled: Boolean  = self.level <= LogLevel.Warn

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

    final private def stackTraceAsString(throwable: Throwable): String = {
      val sw = new StringWriter
      throwable.printStackTrace(new PrintWriter(sw))
      sw.toString
    }

    final private def thread = Thread.currentThread()

    protected def run(charSequence: CharSequence): Unit

    final def addTags(tags: Iterable[String]): LoggerTransport = self.copy(tags = self.tags ++ tags)

    final def copy(
      format: LogFormat = self.format,
      level: LogLevel = self.level,
      filter: String => Boolean = self.filter,
      tags: List[String] = self.tags,
    ): LoggerTransport = {
      new DefaultLoggerTransport(format, level, filter, tags) {
        override def run(charSequence: CharSequence): Unit = self.run(charSequence)
      }
    }

    final def dispatch(
      msg: String,
      cause: Option[Throwable],
      level: LogLevel,
      sourceLocation: Option[SourcePos],
    ): Unit =
      if (self.level <= level) {
        buildLines(msg, cause, level, self.tags, sourceLocation).foreach { line =>
          val formatted = format(line)
          if (filter(formatted)) run(formatted)
        }
      }

    final def withFilter(filter: String => Boolean): LoggerTransport = self.copy(filter = filter)

    final def withFormat(format: LogFormat): LoggerTransport = self.copy(format = format)

    final def withLevel(level: LogLevel): LoggerTransport = self.copy(level = level)

    final def withTags(tags: List[String]): LoggerTransport = self.copy(tags = tags)
  }

  private[logging] class ZioLoggerTransport(
    loggers: Set[ZLogger[String, Any]],
    format: LogFormat = LogFormat.inlineMinimal,
    val level: LogLevel = LogLevel.Error,
    filter: String => Boolean = _ => true,
    tags: List[String] = Nil,
  ) extends LoggerTransport {
    self =>

    final private[zio] val isDebugEnabled: Boolean = self.level <= LogLevel.Debug
    final private[zio] val isErrorEnabled: Boolean = self.level <= LogLevel.Error
    final private[zio] val isInfoEnabled: Boolean  = self.level <= LogLevel.Info
    final private[zio] val isTraceEnabled: Boolean = self.level <= LogLevel.Trace
    final private[zio] val isWarnEnabled: Boolean  = self.level <= LogLevel.Warn

    final def addTags(tags: Iterable[String]): LoggerTransport = self.copy(tags = self.tags ++ tags)

    final def copy(
      format: LogFormat = self.format,
      level: LogLevel = self.level,
      filter: String => Boolean = self.filter,
      tags: List[String] = self.tags,
    ): LoggerTransport = {
      new ZioLoggerTransport(loggers, format, level, filter, tags) {
        override def dispatch(
          msg: String,
          cause: Option[Throwable],
          level: LogLevel,
          sourceLocation: Option[SourcePos],
        ): Unit =
          self.dispatch(msg, cause, level, sourceLocation)
      }
    }

    override def dispatch(
      msg: String,
      cause: Option[Throwable],
      level: LogLevel,
      sourceLocation: Option[SourcePos],
    ): Unit = {
      if (self.level <= level) {
        if (filter(msg)) {
          for (logger <- loggers) {
            logger(
              sourceLocation.map(pos => Trace.apply("zio-http", pos.file, pos.line)).getOrElse(Trace.empty),
              FiberId.None,
              toZioLogLevel(level),
              () => msg,
              cause.map(Cause.die(_)).getOrElse(Cause.empty),
              FiberRefs.empty,
              List(),
              self.tags.map(_ -> "").toMap,
            )
          }
        }
      }
    }

    final def withFilter(filter: String => Boolean): LoggerTransport = self.copy(filter = filter)

    final def withFormat(format: LogFormat): LoggerTransport = self.copy(format = format)

    final def withLevel(level: LogLevel): LoggerTransport = self.copy(level = level)

    final def withTags(tags: List[String]): LoggerTransport = self.copy(tags = tags)

    private def toZioLogLevel(level: LogLevel): _root_.zio.LogLevel =
      level match {
        case LogLevel.Trace => _root_.zio.LogLevel.Trace
        case LogLevel.Debug => _root_.zio.LogLevel.Debug
        case LogLevel.Info  => _root_.zio.LogLevel.Info
        case LogLevel.Warn  => _root_.zio.LogLevel.Warning
        case LogLevel.Error => _root_.zio.LogLevel.Error
      }
  }

  def console: DefaultLoggerTransport = new DefaultLoggerTransport() {
    override def run(charSequence: CharSequence): Unit = println(charSequence)
  }

  def empty: DefaultLoggerTransport = new DefaultLoggerTransport() {
    override def run(charSequence: CharSequence): Unit = ()
  }

  def file(path: Path): DefaultLoggerTransport = new DefaultLoggerTransport() { self =>
    override def run(charSequence: CharSequence): Unit = Files.write(
      path,
      util.Arrays.asList(charSequence),
      StandardOpenOption.APPEND,
      StandardOpenOption.CREATE,
    ): Unit
  }

  def zio: ZIO[Any, Nothing, LoggerTransport] =
    ZIO.loggers.map { loggers =>
      new ZioLoggerTransport(loggers)
    }
}
