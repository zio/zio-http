package zhttp.logging.macros

import zhttp.logging.Logger
import zhttp.logging.LogLevel

import scala.language.experimental.macros
import scala.quoted._

/** Macros that support the logging system.
 * inspired from log4s macro
 */
private[zhttp] object LoggerMacroImpl {
  final case class SourcePos(file: String, line: Int)

  inline def sourcePos(using qctx: Quotes): Expr[SourcePos] = {
    val rootPosition = qctx.reflect.Position.ofMacroExpansion
    val file = Expr(rootPosition.sourceFile.path.toString)
    val line = Expr(rootPosition.startLine + 1)
    '{SourcePos($file, $line)}
  }

  def logTraceImpl(logger: Expr[Logger])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) = {
    val pos = sourcePos(using qctx)
    '{if ($logger.isEnabled) $logger.dispatch($msg, None, LogLevel.Trace, $tags, $pos.file, $pos.line)}
  }

  def logDebugImpl(logger: Expr[Logger])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) = {
    val pos = sourcePos(using qctx)
    '{if ($logger.isEnabled) $logger.dispatch($msg, None, LogLevel.Debug, $tags, $pos.file, $pos.line)}
    }

  def logInfoImpl(logger: Expr[Logger])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) = {
    val pos = sourcePos(using qctx)
    '{if ($logger.isEnabled) $logger.dispatch($msg, None, LogLevel.Info, $tags, $pos.file, $pos.line)}
    }

  def logWarnImpl(logger: Expr[Logger])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) = {
    val pos = sourcePos(using qctx)
    '{if ($logger.isEnabled) $logger.dispatch($msg, None, LogLevel.Warn, $tags, $pos.file, $pos.line)}
    }

  def logErrorWithCauseImpl(logger: Expr[Logger])(t: Expr[Throwable])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) = {
    val pos = sourcePos(using qctx)
    '{if ($logger.isEnabled) $logger.dispatch($msg, Some($t), LogLevel.Error, $tags, $pos.file, $pos.line)}
    }

  def logErrorImpl(logger: Expr[Logger])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) = {
    val pos = sourcePos(using qctx)
    '{if ($logger.isEnabled) $logger.dispatch($msg, None, LogLevel.Error, $tags, $pos.file, $pos.line)}
    }


}
