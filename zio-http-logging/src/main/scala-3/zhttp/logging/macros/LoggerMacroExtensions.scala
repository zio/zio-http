package zhttp.logging.macros

import zhttp.logging.macros.LoggerMacro._
import zhttp.logging.LoggerTransport

/**
 * Core Logger class.
 */
trait LoggerMacroExtensions{ self =>
  import scala.language.experimental.macros

  def transports: List[LoggerTransport]

  inline def trace(msg: String, tags: List[String]): Unit = $ { logTraceImpl('transports)('msg)('tags) }
  inline def debug(msg: String, tags: List[String]): Unit = $ { logDebugImpl('transports)('msg)('tags) }
  inline def info(msg: String, tags: List[String]): Unit = $ { logInfoImpl('transports)('msg)('tags) }
  inline def warn(msg: String, tags: List[String]): Unit = $ { logWarnImpl('transports)('msg)('tags) }
  inline def error(msg: String, tags: List[String]): Unit = $ { logErrorImpl('transports)('msg)('tags) }
  inline def error(msg: String, throwable: Throwable, tags: List[String]): Unit = ${logErrorWithCauseImpl('transports)('throwable)('msg)('tags)}
}
