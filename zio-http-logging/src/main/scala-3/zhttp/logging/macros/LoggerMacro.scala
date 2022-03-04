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

  /** Get a logger by reflecting the enclosing class name. */
  final def getLoggerImpl(using qctx: Quotes): Expr[Logger] = {
    import qctx.reflect._

    @tailrec def findEnclosingClass(sym: Symbol): Symbol = {
      sym match {
        case s if s.isNoSymbol =>
          report.errorAndAbort("Couldn't find an enclosing class or module for the logger")
        case s if s.isClassDef =>
          s
        case other =>
          /* We're not in a module or a class, so we're probably inside a member definition. Recurse upward. */
          findEnclosingClass(other.owner)
      }
    }

    def logger(s: Symbol): Expr[Logger] = {
      def fullName(s: Symbol): String = {
        val flags = s.flags
        if (flags.is(Flags.Package)) {
          s.fullName
        }
        else if (s.isClassDef) {
          if (flags.is(Flags.Module)) {
            if (s.name == "package$") {
              fullName(s.owner)
            }
            else {
              val chomped = s.name.stripSuffix("$")
              fullName(s.owner) + "." + chomped
            }
          }
          else {
            fullName(s.owner) + "." + s.name
          }
        }
        else {
          fullName(s.owner)
        }
      }

      val name = Expr(fullName(s))
      '{ Logger.getLogger($name) }
    }

    val cls = findEnclosingClass(Symbol.spliceOwner)
    logger(cls)
  }

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
