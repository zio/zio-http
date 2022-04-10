package zhttp.logging

import zhttp.logging.LogFrontend.Config
import zhttp.logging.macros.LoggerMacro._

final class Logger(val frontend: LogFrontend) {
  import scala.language.experimental.macros
  def trace(msg: String): Unit = macro traceImpl
  def debug(msg: String): Unit = macro debugImpl
  def info(msg: String): Unit = macro infoImpl
  def warn(msg: String): Unit = macro warnImpl
  def error(msg: String): Unit = macro errorImpl
  def error(msg: String, throwable: Throwable): Unit = macro errorImplT
}

object Logger {
  def make(
    name: String,
    level: LogLevel = LogLevel.ERROR,
    format: LogFormat = LogFormat.default,
    filter: String => Boolean = _ => true,
  ): Logger =
    make(Config(name, level, format, filter))

  def make(config: Config): Logger =
    new Logger(LogFrontend.console(config))
}
