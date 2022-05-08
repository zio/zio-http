package zhttp.logging.macros

import zhttp.logging.macros.LoggerMacroImpl._
import zhttp.logging.Logger

/**
 * Core Logger class.
 */
trait LoggerMacroExtensions{ self: Logger =>
  import scala.language.experimental.macros

  def isEnabled: Boolean

  inline def trace(msg: String): Unit = $ { logTraceImpl('self)('msg) }
  inline def debug(msg: String): Unit = $ { logDebugImpl('self)('msg) }
  inline def info(msg: String): Unit = $ { logInfoImpl('self)('msg) }
  inline def warn(msg: String): Unit = $ { logWarnImpl('self)('msg) }
  inline def error(msg: String): Unit = $ { logErrorImpl('self)('msg) }
  inline def error(msg: String, throwable: Throwable): Unit = ${logErrorWithCauseImpl('self)('throwable)('msg)}
}
