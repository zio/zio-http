package zhttp.logging.frontend
import zhttp.logging.{LogLevel, LogLine}
import zhttp.logging.Setup.LogFormat
import zhttp.logging.frontend.LogFrontend.Config

import java.io.{PrintWriter, StringWriter}
import java.time.LocalDateTime
import scala.io.AnsiColor

trait LogFrontend {
  private def buildLines(
    name: String,
    msg: String,
    throwable: Option[Throwable],
    logLevel: LogLevel,
    tags: List[String],
  ): List[LogLine] = {
    throwable.fold(List(LogLine(name, LocalDateTime.now(), thread, logLevel, msg, tags, throwable)))(t =>
      List(
        LogLine(name, LocalDateTime.now(), thread, logLevel, msg, tags, throwable),
        LogLine(name, LocalDateTime.now(), thread, logLevel, stackTraceAsString(t), tags, throwable),
      ),
    )
  }

  private final def logMayBe(
    msg: String,
    throwable: Option[Throwable],
    logLevel: LogLevel,
    tags: List[String],
  ): Unit =
    if (config.filter(config.name)) {
      buildLines(msg, throwable, logLevel, tags).foreach { line => log(config.format(line).toString) }
    }

  private def stackTraceAsString(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  private def thread = Thread.currentThread()

  def config: Config

  def log(msg: String): Unit

  final def debug(name: String, msg: String, tags: List[String]): Unit = logMayBe(msg, None, LogLevel.DEBUG, tags)

  final def debug(name: String, msg: String, throwable: Throwable, tags: List[String]): Unit =
    logMayBe(name, msg, Some(throwable), LogLevel.DEBUG, tags)

  final def error(name: String, msg: String, tags: List[String]): Unit = logMayBe(name, msg, None, LogLevel.ERROR, tags)

  final def error(name: String, msg: String, throwable: Throwable, tags: List[String]): Unit =
    logMayBe(name, msg, Some(throwable), LogLevel.ERROR, tags)

  final def info(name: String, msg: String, tags: List[String]): Unit = logMayBe(name, msg, None, LogLevel.INFO, tags)

  final def info(name: String, msg: String, throwable: Throwable, tags: List[String]): Unit =
    logMayBe(name, msg, Some(throwable), LogLevel.INFO, tags)

  final def trace(name: String, msg: String, tags: List[String]): Unit = logMayBe(name, msg, None, LogLevel.TRACE, tags)

  final def trace(name: String, msg: String, throwable: Throwable, tags: List[String]): Unit =
    logMayBe(name, msg, Some(throwable), LogLevel.TRACE, tags)

  final def warn(name: String, msg: String, tags: List[String]): Unit = logMayBe(name, msg, None, LogLevel.WARN, tags)

  final def warn(name: String, msg: String, throwable: Throwable, tags: List[String]): Unit =
    logMayBe(name, msg, Some(throwable), LogLevel.WARN, tags)
}

object LogFrontend {

  def console(config: Config): LogFrontend = new ConsoleLogger(config)

  final case class Config(
    name: String,
    level: LogLevel = LogLevel.ERROR,
    format: LogFormat = zhttp.logging.LogFormat.default,
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

    def determine(text: String): String = colors(text.hashCode % (colors.size - 1))

    override def log(msg: String): Unit = {
      val color = determine(msg)
      val reset = AnsiColor.RESET
      println(s"${color}[${config.name}]${reset} $msg")
    }
  }
}
