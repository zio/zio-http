package zhttp.logging.frontend

import zhttp.logging.{Configuration, LogFormat, LogLevel, LogLine}

import java.io.{PrintWriter, StringWriter}
import java.time.LocalDateTime

private[zhttp] final class ConsoleLogger(configuration: Configuration) {

  private val threadName = Thread.currentThread().getName
  private val threadId   = Thread.currentThread().getId.toString

  def trace(msg: String): Unit = log(msg, None, LogLevel.TRACE)

  def trace(msg: String, throwable: Throwable): Unit = log(msg, Some(throwable), LogLevel.TRACE)

  def debug(msg: String): Unit = log(msg, None, LogLevel.DEBUG)

  def debug(msg: String, throwable: Throwable): Unit = log(msg, Some(throwable), LogLevel.DEBUG)

  def info(msg: String): Unit = log(msg, None, LogLevel.INFO)

  def info(msg: String, throwable: Throwable): Unit = log(msg, Some(throwable), LogLevel.INFO)

  def warn(msg: String): Unit = log(msg, None, LogLevel.WARN)

  def warn(msg: String, throwable: Throwable): Unit = log(msg, Some(throwable), LogLevel.WARN)

  def error(msg: String): Unit = log(msg, None, LogLevel.ERROR)

  def error(msg: String, throwable: Throwable): Unit = log(msg, Some(throwable), LogLevel.ERROR)

  private def log(msg: String, throwable: Option[Throwable], logLevel: LogLevel): Unit = {
    buildLines(msg, throwable, logLevel).foreach { line =>
      Console.println(LogFormat.run(configuration.logFormat)(line))
    }
  }

  private def buildLines(msg: String, throwable: Option[Throwable], logLevel: LogLevel): List[LogLine] = {
    throwable.fold(List(LogLine(configuration.loggerName, LocalDateTime.now(), threadName, threadId, logLevel, msg)))(
      t =>
        List(
          LogLine(configuration.loggerName, LocalDateTime.now(), threadName, threadId, logLevel, msg),
          LogLine(configuration.loggerName, LocalDateTime.now(), threadName, threadId, logLevel, stackTraceAsString(t)),
        ),
    )

  }

  private def stackTraceAsString(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    sw.toString
  }
}
