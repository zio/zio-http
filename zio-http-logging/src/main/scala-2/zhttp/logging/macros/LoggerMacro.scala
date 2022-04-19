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
  )(msg: c.Expr[String], error: Option[c.Expr[Throwable]], tags: c.Expr[List[String]])(logLevel: LogLevel) = {
    import c.universe._

    val frontend          = q"${c.prefix.tree}.frontend"
    val enclosingFullName = q"${c.internal.enclosingOwner.owner.fullName}"
    val locationLine      = q"${c.enclosingPosition.line}"

    val logValues = error match {
      case None    => List(msg.tree, tags.tree, enclosingFullName, locationLine)
      case Some(e) => List(msg.tree, e.tree, tags.tree, enclosingFullName, locationLine)
    }

    val logExpr   = q"$frontend.${TermName(logLevel.methodName)}(..$logValues)"
    val checkExpr = q"${c.prefix.tree}.frontend.config.${TermName(s"is${logLevel.methodName.capitalize}Enabled")}"

    q"if ($checkExpr) $logExpr else ()"

  }

  def debugImpl(c: LogCtx)(msg: c.Expr[String]): c.universe.Tree =
    reflectiveLog(c)(msg, None)(DEBUG)

  def errorImpl(c: LogCtx)(msg: c.Expr[String]): c.universe.Tree =
    reflectiveLog(c)(msg, None)(ERROR)

  def errorImplT(c: LogCtx)(msg: c.Expr[String], throwable: c.Expr[Throwable]): c.universe.Tree =
    reflectiveLog(c)(msg, Some(throwable))(ERROR)

  def infoImpl(c: LogCtx)(msg: c.Expr[String]): c.universe.Tree =
    reflectiveLog(c)(msg, None)(INFO)

  def traceImpl(c: LogCtx)(msg: c.Expr[String]): c.universe.Tree =
    reflectiveLog(c)(msg, None)(TRACE)

  def warnImpl(c: LogCtx)(msg: c.Expr[String]): c.universe.Tree =
    reflectiveLog(c)(msg, None)(WARN)

}
