package zhttp.logging.macros

import zhttp.logging.macros.LoggerMacroImpl._

trait LoggerMacroExtensions { self =>
  import scala.language.experimental.macros
  def isEnabled: Boolean

  final def trace(msg: String): Unit = macro logTraceImpl
  final def debug(msg: String): Unit = macro logDebugImpl
  final def info(msg: String): Unit = macro logInfoImpl
  final def warn(msg: String): Unit = macro logWarnImpl
  final def error(msg: String): Unit = macro logErrorImpl
  final def error(msg: String, throwable: Throwable): Unit =
    macro logErrorWithCauseImpl
}
