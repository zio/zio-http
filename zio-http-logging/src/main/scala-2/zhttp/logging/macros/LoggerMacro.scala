package zhttp.logging.macros

import zhttp.logging.LogLevel._
import zhttp.logging.{LogLevel, Logger}

import scala.reflect.macros.whitebox

/**
 * Macro inspired from log4s.
 */
private[zhttp] object LoggerMacro {

  /** A macro context that represents a method call on a Logger instance. */
  private[this] type LogCtx = whitebox.Context { type PrefixType = Logger }

  /**
   * Log a message reflectively at a given level.
   *
   * This is the internal workhorse method that does most of the logging for
   * real applications.
   *
   * @param msg
   *   the message that the user wants to log
   * @param error
   *   the `Throwable` that we're logging along with the message, if any
   * @param logLevel
   *   the level of the logging
   */
  private[this] def reflectiveLog(
    c: LogCtx,
  )(msg: c.Expr[String], error: Option[c.Expr[Throwable]])(logLevel: LogLevel) = {
    import c.universe._

    val logger        = q"${c.prefix.tree}"
    val consoleLogger = q"${c.prefix.tree}.logger"
    val logValues     = error match {
      case None    => List(msg.tree)
      case Some(e) => List(msg.tree, e.tree)
    }

    val logExpr   = q"$consoleLogger.${TermName(logLevel.methodName)}(..$logValues)"
    val checkExpr = q"$logger.${TermName(s"is${logLevel.methodName.capitalize}Enabled")}"

    q"if ($checkExpr) $logExpr else ()"

  }

  def debugImpl(c: LogCtx)(msg: c.Expr[String]) =
    reflectiveLog(c)(msg, None)(DEBUG)

  def errorImpl(c: LogCtx)(msg: c.Expr[String]) =
    reflectiveLog(c)(msg, None)(ERROR)

  def errorImplT(c: LogCtx)(msg: c.Expr[String], throwable: c.Expr[Throwable]) =
    reflectiveLog(c)(msg, Some(throwable))(ERROR)

  def infoImpl(c: LogCtx)(msg: c.Expr[String]) =
    reflectiveLog(c)(msg, None)(INFO)

  def traceImpl(c: LogCtx)(msg: c.Expr[String]) =
    reflectiveLog(c)(msg, None)(TRACE)

  def warnImpl(c: LogCtx)(msg: c.Expr[String]) =
    reflectiveLog(c)(msg, None)(WARN)

}
