package zhttp.logging

import java.io.{PrintWriter, StringWriter}
import java.time.LocalDateTime

trait LogFrontend { this: LoggerTransport =>
  def log(msg: CharSequence): Unit

  private def buildLines(
    msg: String,
    throwable: Option[Throwable],
    logLevel: LogLevel,
    tags: List[String],
    enclosingClass: String,
    lineNumber: Int,
  ): List[LogLine] = {
    throwable.fold(
      List(
        LogLine(
          LocalDateTime.now(),
          thread,
          logLevel,
          msg,
          tags,
          throwable,
          enclosingClass,
          lineNumber,
        ),
      ),
    )(t =>
      List(
        LogLine(
          LocalDateTime.now(),
          thread,
          logLevel,
          msg,
          tags,
          throwable,
          enclosingClass,
          lineNumber,
        ),
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
      ),
    )
  }

  private final def logMayBe(
    msg: String,
    throwable: Option[Throwable],
    logLevel: LogLevel,
    tags: List[String],
    enclosingClass: String,
    lineNumber: Int,
  ): Unit =
    if (filter(tags.mkString)) {
      buildLines(msg, throwable, logLevel, tags, enclosingClass, lineNumber).foreach { line =>
        this.log(format(line))
      }
    }

  private def stackTraceAsString(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  private def thread = Thread.currentThread()

  final def debug(msg: String, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, None, LogLevel.DEBUG, tags, enclosingClass, lineNumber)

  final def error(msg: String, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, None, LogLevel.ERROR, tags, enclosingClass, lineNumber)

  final def error(
    msg: String,
    throwable: Throwable,
    tags: List[String],
    enclosingClass: String,
    lineNumber: Int,
  ): Unit =
    logMayBe(msg, Some(throwable), LogLevel.ERROR, tags, enclosingClass, lineNumber)

  final def info(msg: String, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, None, LogLevel.INFO, tags, enclosingClass, lineNumber)

  final def trace(msg: String, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, None, LogLevel.TRACE, tags, enclosingClass, lineNumber)

  final def warn(msg: String, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, None, LogLevel.WARN, tags, enclosingClass, lineNumber)

  private[zhttp] final val isDebugEnabled: Boolean = this.level >= LogLevel.DEBUG
  private[zhttp] final val isErrorEnabled: Boolean = this.level >= LogLevel.ERROR
  private[zhttp] final val isInfoEnabled: Boolean  = this.level >= LogLevel.INFO
  private[zhttp] final val isTraceEnabled: Boolean = this.level >= LogLevel.TRACE
  private[zhttp] final val isWarnEnabled: Boolean  = this.level >= LogLevel.WARN
}
