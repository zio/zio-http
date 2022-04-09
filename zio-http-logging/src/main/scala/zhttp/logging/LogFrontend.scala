package zhttp.logging

import zhttp.logging.LogFrontend.Config

import java.io.{PrintWriter, StringWriter}
import java.time.LocalDateTime

trait LogFrontend {
  def config: Config

  def debug(msg: String): Unit

  def debug(msg: String, throwable: Throwable): Unit

  def error(msg: String): Unit

  def error(msg: String, throwable: Throwable): Unit

  def info(msg: String): Unit

  def info(msg: String, throwable: Throwable): Unit

  def trace(msg: String): Unit

  def trace(msg: String, throwable: Throwable): Unit

  def warn(msg: String): Unit

  def warn(msg: String, throwable: Throwable): Unit
}

object LogFrontend {
  def console(config: Config): LogFrontend = new ConsoleLogger(config)

  final case class Config(group: String, level: LogLevel, format: LogFormat) {
    val isDebugEnabled: Boolean = level >= LogLevel.DEBUG
    val isErrorEnabled: Boolean = level >= LogLevel.ERROR
    val isInfoEnabled: Boolean  = level >= LogLevel.INFO
    val isTraceEnabled: Boolean = level >= LogLevel.TRACE
    val isWarnEnabled: Boolean  = level >= LogLevel.WARN
  }

  private final class ConsoleLogger(override val config: Config) extends LogFrontend {

    private val threadName = Thread.currentThread().getName
    private val threadId   = Thread.currentThread().getId.toString

    private def buildLines(msg: String, throwable: Option[Throwable], logLevel: LogLevel): List[LogLine] = {
      throwable.fold(List(LogLine(config.group, LocalDateTime.now(), threadName, threadId, logLevel, msg)))(t =>
        List(
          LogLine(config.group, LocalDateTime.now(), threadName, threadId, logLevel, msg),
          LogLine(config.group, LocalDateTime.now(), threadName, threadId, logLevel, stackTraceAsString(t)),
        ),
      )

    }

    private def log(msg: String, throwable: Option[Throwable], logLevel: LogLevel): Unit = {
      buildLines(msg, throwable, logLevel).foreach { line =>
        Console.println(LogFormat.run(config.format)(line))
      }
    }

    private def stackTraceAsString(throwable: Throwable): String = {
      val sw = new StringWriter
      throwable.printStackTrace(new PrintWriter(sw))
      sw.toString
    }

    def debug(msg: String): Unit = log(msg, None, LogLevel.DEBUG)

    def debug(msg: String, throwable: Throwable): Unit = log(msg, Some(throwable), LogLevel.DEBUG)

    def error(msg: String): Unit = log(msg, None, LogLevel.ERROR)

    def error(msg: String, throwable: Throwable): Unit = log(msg, Some(throwable), LogLevel.ERROR)

    def info(msg: String): Unit = log(msg, None, LogLevel.INFO)

    def info(msg: String, throwable: Throwable): Unit = log(msg, Some(throwable), LogLevel.INFO)

    def trace(msg: String): Unit = log(msg, None, LogLevel.TRACE)

    def trace(msg: String, throwable: Throwable): Unit = log(msg, Some(throwable), LogLevel.TRACE)

    def warn(msg: String): Unit = log(msg, None, LogLevel.WARN)

    def warn(msg: String, throwable: Throwable): Unit = log(msg, Some(throwable), LogLevel.WARN)
  }
}
