package zhttp.logging

import zhttp.logging.frontend.LogFrontend
import zhttp.logging.frontend.LogFrontend.Config
import zhttp.logging.macros.LoggerMacro._

final case class Logger(config: Config, frontend: LogFrontend) {

  import LogLevel._

  private[zhttp] val logger = frontend

  @inline def name = config.name

  @inline def isTraceEnabled: Boolean = config.level >= TRACE
  @inline def isDebugEnabled: Boolean = config.level >= DEBUG
  @inline def isInfoEnabled: Boolean  = config.level >= INFO
  @inline def isWarnEnabled: Boolean  = config.level >= WARN
  @inline def isErrorEnabled: Boolean = config.level >= ERROR

  import scala.language.experimental.macros

  def trace(msg: String, tags: List[String]): Unit = macro traceM
  def trace(msg: String, throwable: Throwable, tags: List[String]): Unit = macro traceTM
  def debug(msg: String, tags: List[String]): Unit = macro debugM
  def debug(msg: String, throwable: Throwable, tags: List[String]): Unit = macro debugTM
  def info(msg: String, tags: List[String]): Unit = macro infoM
  def info(msg: String, throwable: Throwable, tags: List[String]): Unit = macro infoTM
  def warn(msg: String, tags: List[String]): Unit = macro warnM
  def warn(msg: String, throwable: Throwable, tags: List[String]): Unit = macro warnTM
  def error(msg: String, tags: List[String]): Unit = macro errorM
  def error(msg: String, throwable: Throwable, tags: List[String]): Unit = macro errorTM

}

object Logger {

  def make(
    name: String,
    level: LogLevel = LogLevel.ERROR,
    format: Setup.LogFormat = LogFormat.default,
    filter: String => Boolean = _ => true,
  ): Logger =
    make(Config(name, level, format, filter))

  def make(config: Config): Logger =
    new Logger(config, LogFrontend.console(config))

}
