package zhttp.logging.macros

import zhttp.logging.Logger
import zhttp.logging.macros.LoggerMacroImpl._

/**
 * Core Logger class.
 */
trait LoggerMacroExtensions{ self: Logger =>
  import scala.language.experimental.macros

  val isDebugEnabled: Boolean
  val isErrorEnabled: Boolean
  val isInfoEnabled: Boolean
  val isTraceEnabled: Boolean
  val isWarnEnabled: Boolean

  inline def trace(inline msg: String): Unit = $ { logTraceImpl('self, 'msg) }
  inline def debug(inline msg: String): Unit = $ { logDebugImpl('self, 'msg) }
  inline def info(inline msg: String): Unit = $ { logInfoImpl('self, 'msg) }
  inline def warn(inline msg: String): Unit = $ { logWarnImpl('self, 'msg) }
  inline def error(inline msg: String): Unit = $ { logErrorImpl('self, 'msg) }
  inline def error(inline msg: String, throwable: Throwable): Unit = ${logErrorWithCauseImpl('self, 'throwable, 'msg)}
}
