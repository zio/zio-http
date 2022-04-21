package zhttp.logging.macros

import zhttp.logging.{LogFormat, Logger}
import zhttp.logging.frontend.LogFrontend

import scala.annotation.tailrec
import scala.language.experimental.macros
import scala.quoted._

/** Macros that support the logging system.
 * inspired from log4s macro
 */
private[zhttp] object LoggerMacro {

  final case class SourcePos(file: String, line: Int)

  def sourcePos(using ctx: Quotes): Expr[SourcePos] = {
    val rootPosition = ctx.reflect.Position.ofMacroExpansion
    val file = Expr(rootPosition.sourceFile.jpath.toString)
    val line = Expr(rootPosition.startLine + 1)
    '{SourcePos($file, $line)}
  }

  def traceM(frontend: Expr[LogFrontend])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) =
  '{ if ($frontend.config.isTraceEnabled) {
    val pos:Expr[SourcePos] = sourcePos
    $frontend.trace($msg, $tags, $pos.file, $pos.line) }
  }

  def debugM(frontend: Expr[LogFrontend])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) =
  '{ if ($frontend.config.isDebugEnabled) {
    val pos = sourcePos
    $frontend.debug($msg, $tags, $pos.file, $pos.line) }
  }

  def infoM(frontend: Expr[LogFrontend])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) =
  '{ if ($frontend.config.isInfoEnabled) {
    val pos = sourcePos
    $frontend.info($msg, $tags, $pos.file, $pos.line) }
  }

  def warnM(frontend: Expr[LogFrontend])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) =
  '{ if ($frontend.config.isWarnEnabled) {
    val pos = sourcePos
    $frontend.warn($msg, $tags, $pos.file, $pos.line) }
  }

  def errorTM(frontend: Expr[LogFrontend])(t: Expr[Throwable])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) =
  '{ if ($frontend.config.isErrorEnabled) {
    val pos = sourcePos
    $frontend.error($msg, $t, $tags, $pos.file, $pos.line) }
  }
  def errorM(frontend: Expr[LogFrontend])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) =
  '{ if ($frontend.config.isErrorEnabled) {
    val pos = sourcePos
    $frontend.error($msg, $tags, $pos.file, $pos.line) }
  }


}
