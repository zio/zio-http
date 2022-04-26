package zhttp.logging.macros

import zhttp.logging.LoggerTransport

import scala.language.experimental.macros
import scala.quoted._

/** Macros that support the logging system.
 * inspired from log4s macro
 */
private[zhttp] object LoggerMacro {
  final case class SourcePos(file: String, line: Int)

  inline def sourcePos(using qctx: Quotes): Expr[SourcePos] = {
    val rootPosition = qctx.reflect.Position.ofMacroExpansion
    val file = Expr(rootPosition.sourceFile.path.toString)
    val line = Expr(rootPosition.startLine + 1)
    '{SourcePos($file, $line)}
  }

  def logTraceImpl(transports: Expr[List[LoggerTransport]])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) = {
    val pos = sourcePos(using qctx)
    '{ $transports.filter(transport => transport.isTraceEnabled).foreach { transport =>
        transport.trace($msg, $tags, $pos.file, $pos.line) }
    }
  }

  def logDebugImpl(transports: Expr[List[LoggerTransport]])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) = {
    val pos = sourcePos(using qctx)
  '{ $transports.filter(transport => transport.isDebugEnabled).foreach { transport =>
    transport.debug($msg, $tags, $pos.file, $pos.line) }
  }
    }

  def logInfoImpl(transports: Expr[List[LoggerTransport]])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) = {
    val pos = sourcePos(using qctx)
  '{ $transports.filter(transport => transport.isInfoEnabled).foreach { transport =>
    transport.info($msg, $tags, $pos.file, $pos.line) }
  }
    }

  def logWarnImpl(transports: Expr[List[LoggerTransport]])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) = {
    val pos = sourcePos(using qctx)
  '{ $transports.filter(transport => transport.isWarnEnabled).foreach { transport =>
    transport.warn($msg, $tags, $pos.file, $pos.line) }
  }
    }

  def logErrorWithCauseImpl(transports: Expr[List[LoggerTransport]])(t: Expr[Throwable])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) = {
    val pos = sourcePos(using qctx)
  '{ $transports.filter(transport => transport.isErrorEnabled).foreach { transport =>
    transport.error($msg, $t, $tags, $pos.file, $pos.line) }
  }
    }

  def logErrorImpl(transports: Expr[List[LoggerTransport]])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) = {
    val pos = sourcePos(using qctx)
  '{ $transports.filter(transport => transport.isErrorEnabled).foreach { transport =>
    transport.error($msg, $tags, $pos.file, $pos.line) }
  }
    }


}
