package zhttp.logging.macros

import zhttp.logging.Logger
import zhttp.logging.LogLevel
import zhttp.logging.Logger.SourcePos


import scala.language.experimental.macros
import scala.quoted._

/** Macros that support the logging system.
 * inspired from log4s macro
 */
private[zhttp] object LoggerMacroImpl {

  inline def sourcePos(using qctx: Quotes): Expr[SourcePos] = {
    val rootPosition = qctx.reflect.Position.ofMacroExpansion
    val file = Expr(rootPosition.sourceFile.path.toString)
    val line = Expr(rootPosition.startLine + 1)
    '{SourcePos($file, $line)}
  }

  def logTraceImpl(logger: Expr[Logger])(msg: Expr[String])(using qctx: Quotes) = {
    val pos = sourcePos(using qctx)
    '{if ($logger.isEnabled) $logger.dispatch($msg, None, LogLevel.Trace, Some($pos))}
  }

  def logDebugImpl(logger: Expr[Logger])(msg: Expr[String])(using qctx: Quotes) =
  '{if ($logger.isEnabled) $logger.dispatch($msg, None, LogLevel.Debug, None)}

  def logInfoImpl(logger: Expr[Logger])(msg: Expr[String])(using qctx: Quotes) =
  '{if ($logger.isEnabled) $logger.dispatch($msg, None, LogLevel.Info, None)}

  def logWarnImpl(logger: Expr[Logger])(msg: Expr[String])(using qctx: Quotes) =
  '{if ($logger.isEnabled) $logger.dispatch($msg, None, LogLevel.Warn, None)}

  def logErrorWithCauseImpl(logger: Expr[Logger])(t: Expr[Throwable])(msg: Expr[String])(using qctx: Quotes) = {
    '{if ($logger.isEnabled) $logger.dispatch($msg, Some($t), LogLevel.Error, None)}
    }

  def logErrorImpl(logger: Expr[Logger])(msg: Expr[String])(using qctx: Quotes) =
  '{if ($logger.isEnabled) $logger.dispatch($msg, None, LogLevel.Error, None)}

}
