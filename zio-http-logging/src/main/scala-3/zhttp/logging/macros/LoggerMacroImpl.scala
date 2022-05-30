package zhttp.logging.macros

import zhttp.logging.Logger.SourcePos
import zhttp.logging.{LogLevel, Logger}

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


  def logTraceImpl(logger: Expr[Logger], msg: Expr[String])(using qctx: Quotes): quoted.Expr[Any] = {
    if(LogLevel.Trace >= Logger.detectedLevel) {
      val pos = sourcePos(using qctx)
      '{
        if ($logger.isTraceEnabled) $logger.dispatch($msg, LogLevel.Trace, None, Some($pos))
      }
    } else {
      '{}
    }
  }

  def logDebugImpl(logger: Expr[Logger], msg: Expr[String])(using qctx: Quotes): quoted.Expr[Any] =
  if(LogLevel.Debug >= Logger.detectedLevel) {
    '{
      if ($logger.isDebugEnabled) $logger.dispatch($msg, LogLevel.Debug, None, None)
    }
  }else {
    '{}
  }

  def logInfoImpl(logger: Expr[Logger], msg: Expr[String])(using qctx: Quotes): quoted.Expr[Any] =
  if(LogLevel.Info >= Logger.detectedLevel) {
    '{
      if ($logger.isInfoEnabled) $logger.dispatch($msg, LogLevel.Info, None, None)
    }
  }else {
    '{}
  }

  def logWarnImpl(logger: Expr[Logger], msg: Expr[String])(using qctx: Quotes): quoted.Expr[Any] =
    if(LogLevel.Warn >= Logger.detectedLevel) {
      '{
        if ($logger.isWarnEnabled) $logger.dispatch($msg, LogLevel.Warn, None, None)
      }
    }else {
      '{}
    }

  def logErrorWithCauseImpl(logger: Expr[Logger], t: Expr[Throwable], msg: Expr[String])(using qctx: Quotes): quoted.Expr[Any] =
    if(LogLevel.Error >= Logger.detectedLevel) {
      '{
        if ($logger.isErrorEnabled) $logger.dispatch($msg, LogLevel.Error, Some($t), None)
      }
    }else {
      '{}
    }


  def logErrorImpl(logger: Expr[Logger], msg: Expr[String])(using qctx: Quotes): quoted.Expr[Any] =
    if(LogLevel.Error >= Logger.detectedLevel) {
      '{if ($logger.isErrorEnabled) $logger.dispatch($msg, LogLevel.Error, None, None)}
    }else {
      '{}
    }
}
