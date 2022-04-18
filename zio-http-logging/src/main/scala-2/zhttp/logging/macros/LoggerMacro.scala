package zhttp.logging.macros

import zhttp.logging.LogLevel.{DEBUG, ERROR, INFO, TRACE, WARN}
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
  )(msg: c.Expr[String], error: Option[c.Expr[Throwable]], tags: c.Expr[List[String]])(logLevel: LogLevel) = {
    import c.universe._

    val logger        = q"${c.prefix.tree}"
    val consoleLogger = q"${c.prefix.tree}.logger"
    val loggerName    = q"${c.prefix.tree.toString()}"
    val logValues     = error match {
      case None    => List(loggerName, msg.tree, tags.tree)
      case Some(e) => List(loggerName, msg.tree, e.tree, tags.tree)
    }

    val logExpr   = q"$consoleLogger.${TermName(logLevel.methodName)}(..$logValues)"
    val checkExpr = q"$logger.${TermName(s"is${logLevel.methodName.capitalize}Enabled")}"

    q"if ($checkExpr) $logExpr else ()"

  }

  def traceTM(c: LogCtx)(msg: c.Expr[String], throwable: c.Expr[Throwable], tags: c.Expr[List[String]]) =
    reflectiveLog(c)(msg, Some(throwable), tags)(TRACE)
  def traceM(c: LogCtx)(msg: c.Expr[String], tags: c.Expr[List[String]]) = reflectiveLog(c)(msg, None, tags)(TRACE)

  def debugTM(c: LogCtx)(msg: c.Expr[String], throwable: c.Expr[Throwable], tags: c.Expr[List[String]]) =
    reflectiveLog(c)(msg, Some(throwable), tags)(DEBUG)
  def debugM(c: LogCtx)(msg: c.Expr[String], tags: c.Expr[List[String]]) = reflectiveLog(c)(msg, None, tags)(DEBUG)

  def infoTM(c: LogCtx)(msg: c.Expr[String], throwable: c.Expr[Throwable], tags: c.Expr[List[String]]) =
    reflectiveLog(c)(msg, Some(throwable), tags)(INFO)
  def infoM(c: LogCtx)(msg: c.Expr[String], tags: c.Expr[List[String]]) = reflectiveLog(c)(msg, None, tags)(INFO)

  def warnTM(c: LogCtx)(msg: c.Expr[String], throwable: c.Expr[Throwable], tags: c.Expr[List[String]]) =
    reflectiveLog(c)(msg, Some(throwable), tags)(WARN)
  def warnM(c: LogCtx)(msg: c.Expr[String], tags: c.Expr[List[String]]) = reflectiveLog(c)(msg, None, tags)(WARN)

  def errorTM(c: LogCtx)(msg: c.Expr[String], throwable: c.Expr[Throwable], tags: c.Expr[List[String]]) =
    reflectiveLog(c)(msg, Some(throwable), tags)(ERROR)
  def errorM(c: LogCtx)(msg: c.Expr[String], tags: c.Expr[List[String]]) = reflectiveLog(c)(msg, None, tags)(ERROR)

}
