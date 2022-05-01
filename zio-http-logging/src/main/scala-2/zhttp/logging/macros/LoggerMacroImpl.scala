package zhttp.logging.macros

import zhttp.logging.{LogLevel, Logger}

import scala.reflect.macros.whitebox

/**
 * Macro inspired from log4s.
 */
private[zhttp] object LoggerMacroImpl {

  /** A macro context that represents a method call on a Logger instance. */
  private[this] type LogCtx = whitebox.Context { type PrefixType = Logger }

  /**
   * Log a message reflectively at a given level.
   */
  private[this] def reflectiveLog(
    c: LogCtx,
  )(msg: c.Expr[String], error: Option[c.Expr[Throwable]], tags: c.Expr[List[String]])(logLevel: LogLevel) = {
    import c.universe._

    val transports        = q"${c.prefix.tree}.transports"
    val enclosingFullName = q"${c.internal.enclosingOwner.owner.fullName}"
    val locationLine      = q"${c.enclosingPosition.line}"

    val logValues = error match {
      case None    => List(msg.tree, tags.tree, enclosingFullName, locationLine)
      case Some(e) => List(msg.tree, q"$e", tags.tree, enclosingFullName, locationLine)
    }

    q"$transports.filter(transport => transport.${TermName(s"is${logLevel.methodName.capitalize}Enabled")}).foreach(transport => transport.${TermName(logLevel.methodName)}(..$logValues))"

  }

  def logTraceImpl(c: LogCtx)(
    msg: c.Expr[String],
    tags: c.Expr[List[String]],
  ): c.universe.Tree =
    reflectiveLog(c)(msg, None, tags)(LogLevel.Trace)

  def logInfoImpl(c: LogCtx)(
    msg: c.Expr[String],
    tags: c.Expr[List[String]],
  ): c.universe.Tree =
    reflectiveLog(c)(msg, None, tags)(LogLevel.Info)

  def logDebugImpl(c: LogCtx)(
    msg: c.Expr[String],
    tags: c.Expr[List[String]],
  ): c.universe.Tree =
    reflectiveLog(c)(msg, None, tags)(LogLevel.Debug)

  def logWarnImpl(c: LogCtx)(
    msg: c.Expr[String],
    tags: c.Expr[List[String]],
  ): c.universe.Tree =
    reflectiveLog(c)(msg, None, tags)(LogLevel.Warn)

  def logErrorImpl(c: LogCtx)(
    msg: c.Expr[String],
    tags: c.Expr[List[String]],
  ): c.universe.Tree =
    reflectiveLog(c)(msg, None, tags)(LogLevel.Error)

  def logErrorWithCauseImpl(c: LogCtx)(
    msg: c.Expr[String],
    throwable: c.Expr[Throwable],
    tags: c.Expr[List[String]],
  ): c.universe.Tree =
    reflectiveLog(c)(msg, Some(throwable), tags)(LogLevel.Error)
}
