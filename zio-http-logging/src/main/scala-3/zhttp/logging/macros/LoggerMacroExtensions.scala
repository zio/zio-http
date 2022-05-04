package zhttp.logging.macros

import zhttp.logging.macros.LoggerMacroImpl._
import zhttp.logging.Logger

/**
 * Core Logger class.
 */
trait LoggerMacroExtensions{ self: Logger =>
  import scala.language.experimental.macros

  def isEnabled: Boolean

  inline def trace(msg: String, tags: List[String]): Unit = $ { logTraceImpl('self)('msg)('tags) }
  inline def debug(msg: String, tags: List[String]): Unit = $ { logDebugImpl('self)('msg)('tags) }
  inline def info(msg: String, tags: List[String]): Unit = $ { logInfoImpl('self)('msg)('tags) }
  inline def warn(msg: String, tags: List[String]): Unit = $ { logWarnImpl('self)('msg)('tags) }
  inline def error(msg: String, tags: List[String]): Unit = $ { logErrorImpl('self)('msg)('tags) }
  inline def error(msg: String, throwable: Throwable, tags: List[String]): Unit = ${logErrorWithCauseImpl('self)('throwable)('msg)('tags)}
}
