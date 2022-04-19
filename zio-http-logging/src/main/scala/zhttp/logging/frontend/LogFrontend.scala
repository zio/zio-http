package zhttp.logging.frontend
import zhttp.logging.Setup.LogFormat
import zhttp.logging.frontend.LogFrontend.Config
import zhttp.logging.{LogLevel, LogLine}

import java.io.{PrintWriter, StringWriter}
import java.time.LocalDateTime
import scala.io.AnsiColor

trait LogFrontend {
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
        LogLine(config.name, LocalDateTime.now(), thread, logLevel, msg, tags, throwable, enclosingClass, lineNumber),
      ),
    )(t =>
      List(
        LogLine(config.name, LocalDateTime.now(), thread, logLevel, msg, tags, throwable, enclosingClass, lineNumber),
        LogLine(
          config.name,
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
    if (config.filter(config.name)) {
      buildLines(msg, throwable, logLevel, tags, enclosingClass, lineNumber).foreach { line =>
        log(config.format(line).toString)
      }
    }

  private def stackTraceAsString(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  private def thread = Thread.currentThread()

  def config: Config

  def log(msg: String): Unit

  final def debug(msg: String, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, None, LogLevel.DEBUG, tags, enclosingClass, lineNumber)

  final def debug(
    msg: String,
    throwable: Throwable,
    tags: List[String],
    enclosingClass: String,
    lineNumber: Int,
  ): Unit =
    logMayBe(msg, Some(throwable), LogLevel.DEBUG, tags, enclosingClass, lineNumber)

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

  final def info(msg: String, throwable: Throwable, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, Some(throwable), LogLevel.INFO, tags, enclosingClass, lineNumber)

  final def trace(msg: String, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, None, LogLevel.TRACE, tags, enclosingClass, lineNumber)

  final def trace(
    msg: String,
    throwable: Throwable,
    tags: List[String],
    enclosingClass: String,
    lineNumber: Int,
  ): Unit =
    logMayBe(msg, Some(throwable), LogLevel.TRACE, tags, enclosingClass, lineNumber)

  final def warn(msg: String, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, None, LogLevel.WARN, tags, enclosingClass, lineNumber)

  final def warn(msg: String, throwable: Throwable, tags: List[String], enclosingClass: String, lineNumber: Int): Unit =
    logMayBe(msg, Some(throwable), LogLevel.WARN, tags, enclosingClass, lineNumber)
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

    def determine(text: String): String = colors(Math.abs(text.hashCode) % (colors.size - 1))

    override def log(msg: String): Unit = {
      val color = determine(msg)
      val reset = AnsiColor.RESET
      println(s"${color}[${config.name}]${reset} $msg")
    }
  }
}
