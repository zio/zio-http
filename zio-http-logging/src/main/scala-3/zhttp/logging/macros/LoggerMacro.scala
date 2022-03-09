package zhttp.logging.macros

import zhttp.logging.Logger
import zhttp.logging.frontend.ConsoleLogger

import scala.annotation.tailrec
import scala.language.experimental.macros
import scala.quoted._

/** Macros that support the logging system.
 * inspired from log4s macro
 */
private[zhttp] object LoggerMacro {

  def traceTM(logger: Expr[Logger])(t: Expr[Throwable])(msg: Expr[String])(using qctx: Quotes) =
  '{ if ($logger.isTraceEnabled) $logger.logger.trace($msg, $t) }
  def traceM(logger: Expr[Logger])(msg: Expr[String])(using qctx: Quotes) =
  '{ if ($logger.isTraceEnabled) $logger.logger.trace($msg) }

  def debugTM(logger: Expr[Logger])(t: Expr[Throwable])(msg: Expr[String])(using qctx: Quotes) =
  '{ if ($logger.isDebugEnabled) $logger.logger.debug($msg, $t) }
  def debugM(logger: Expr[Logger])(msg: Expr[String])(using qctx: Quotes) =
  '{ if ($logger.isDebugEnabled) $logger.logger.debug($msg) }

  def infoTM(logger: Expr[Logger])(t: Expr[Throwable])(msg: Expr[String])(using qctx: Quotes) =
  '{ if ($logger.isInfoEnabled) $logger.logger.info($msg, $t) }
  def infoM(logger: Expr[Logger])(msg: Expr[String])(using qctx: Quotes) =
  '{ if ($logger.isInfoEnabled) $logger.logger.info($msg) }

  def warnTM(logger: Expr[Logger])(t: Expr[Throwable])(msg: Expr[String])(using qctx: Quotes) =
  '{ if ($logger.isWarnEnabled) $logger.logger.warn($msg, $t) }
  def warnM(logger: Expr[Logger])(msg: Expr[String])(using qctx: Quotes) =
  '{ if ($logger.isWarnEnabled) $logger.logger.warn($msg) }

  def errorTM(logger: Expr[Logger])(t: Expr[Throwable])(msg: Expr[String])(using qctx: Quotes) =
  '{ if ($logger.isErrorEnabled) $logger.logger.error($msg, $t) }
  def errorM(logger: Expr[Logger])(msg: Expr[String])(using qctx: Quotes) =
  '{ if ($logger.isErrorEnabled) $logger.logger.error($msg) }
}
