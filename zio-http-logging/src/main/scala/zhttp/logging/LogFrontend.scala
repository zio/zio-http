package zhttp.logging

import zhttp.logging.LogFrontend.{Config, LogLine}

import java.io.{PrintWriter, StringWriter}
import java.time.LocalDateTime
import scala.reflect.internal.TypeDebugging.AnsiColor

trait LogFrontend {
  private def buildLines(msg: String, throwable: Option[Throwable], logLevel: LogLevel): List[LogLine] = {
    throwable.fold(List(LogLine(LocalDateTime.now(), threadName, threadId, logLevel, msg)))(t =>
      List(
        LogLine(LocalDateTime.now(), threadName, threadId, logLevel, msg),
        LogLine(LocalDateTime.now(), threadName, threadId, logLevel, stackTraceAsString(t)),
      ),
    )
  }

  private final def logMayBe(msg: String, throwable: Option[Throwable], logLevel: LogLevel): Unit =
    if (config.filter(config.name)) {
      buildLines(msg, throwable, logLevel).foreach { line => log(config.format(line)) }
    }

  private def stackTraceAsString(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  private def threadId = Thread.currentThread().getId.toString

  private def threadName = Thread.currentThread().getName

  def config: Config

  def log(msg: String): Unit

  final def debug(msg: String): Unit = logMayBe(msg, None, LogLevel.DEBUG)

  final def debug(msg: String, throwable: Throwable): Unit = logMayBe(msg, Some(throwable), LogLevel.DEBUG)

  final def error(msg: String): Unit = logMayBe(msg, None, LogLevel.ERROR)

  final def error(msg: String, throwable: Throwable): Unit = logMayBe(msg, Some(throwable), LogLevel.ERROR)

  final def info(msg: String): Unit = logMayBe(msg, None, LogLevel.INFO)

  final def info(msg: String, throwable: Throwable): Unit = logMayBe(msg, Some(throwable), LogLevel.INFO)

  final def trace(msg: String): Unit = logMayBe(msg, None, LogLevel.TRACE)

  final def trace(msg: String, throwable: Throwable): Unit = logMayBe(msg, Some(throwable), LogLevel.TRACE)

  final def warn(msg: String): Unit = logMayBe(msg, None, LogLevel.WARN)

  final def warn(msg: String, throwable: Throwable): Unit = logMayBe(msg, Some(throwable), LogLevel.WARN)
}

object LogFrontend {

  def console(config: Config): LogFrontend = new ConsoleLogger(config)

  final case class LogLine(
    date: LocalDateTime,
    threadName: String,
    threadId: String,
    logLevel: LogLevel,
    msg: String,
  )

  final case class Config(
    name: String,
    level: LogLevel = LogLevel.ERROR,
    format: LogFormat = LogFormat.default,
    filter: String => Boolean = _ => true,
  ) { self =>
    val isDebugEnabled: Boolean = level >= LogLevel.DEBUG
    val isErrorEnabled: Boolean = level >= LogLevel.ERROR
    val isInfoEnabled: Boolean  = level >= LogLevel.INFO
    val isTraceEnabled: Boolean = level >= LogLevel.TRACE
    val isWarnEnabled: Boolean  = level >= LogLevel.WARN

    def startsWith(name: String): Config      = self.copy(filter = _.startsWith(name))
    def withFormat(format: LogFormat): Config = self.copy(format = format)
    def withLevel(level: LogLevel): Config    = self.copy(level = level)
    def withName(name: String): Config        = self.copy(name = name)
  }

  private final class ConsoleLogger(override val config: Config) extends LogFrontend {
    def colors = List(
      AnsiColor.BLUE,
      AnsiColor.CYAN,
      AnsiColor.GREEN,
      AnsiColor.MAGENTA,
      AnsiColor.YELLOW,
    )

    def determine(text: String): String = colors(text.hashCode % colors.size)

    override def log(msg: String): Unit = {
      val color = determine(msg)
      val reset = AnsiColor.RESET
      println(s"${color}[${config.name}]${reset} $msg")
    }
  }
}
