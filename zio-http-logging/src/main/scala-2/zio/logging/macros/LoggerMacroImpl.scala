package zio.logging.macros

import zio.logging.{LogLevel, Logger}

import scala.reflect.macros.whitebox

/**
 * Macro inspired from log4s.
 */
private[zio] object LoggerMacroImpl {

  /** A macro context that represents a method call on a Logger instance. */
  private[this] type LogCtx = whitebox.Context { type PrefixType = Logger }

  /**
   * Log a message reflectively at a given level.
   */
  private[this] def reflectiveLog(
    c: LogCtx,
  )(msg: c.Expr[String], error: Option[c.Expr[Throwable]])(logLevel: LogLevel) = {
    import c.universe._
    type Tree = c.universe.Tree

    val cname: Tree          = q"${c.internal.enclosingOwner.owner.fullName}"
    val lno: Tree            = q"${c.enclosingPosition.line}"
    val sourceLocation: Tree =
      if (logLevel == LogLevel.Trace)
        q"Some(_root_.zio.logging.Logger.SourcePos($cname, $lno))"
      else
        q"None"
    val logLevelName         = logLevel.name.toLowerCase.capitalize
    val level: Tree          = q"_root_.zio.logging.LogLevel.${TermName(logLevelName)}"
    val isEnabled: Tree      = q"""${c.prefix.tree}.${TermName(s"is${logLevelName}Enabled")}"""

    // LogLevel Hierarchy: Trace < Debug < Info < Warn < Error
    //
    // We add the log statement if and only if the level in the statement is
    // greater than or equal to the detected level from the env/props.
    //
    // Eg: If the level is set to Info, then only info, warn and error
    // statements will be allowed.
    //
    // If the level is set to `Debug` then only debug, info, warn and error will
    // be allowed.
    if (logLevel >= Logger.detectedLevel)
      q"""
      if($isEnabled) {
        val logMsg = ${msg.tree}
        ${c.prefix.tree}.dispatch(logMsg, $level, $error, ${sourceLocation})
      }
    """
    else q"()"
  }

  def logTraceImpl(c: LogCtx)(msg: c.Expr[String]): c.universe.Tree =
    reflectiveLog(c)(msg, None)(LogLevel.Trace)

  def logInfoImpl(c: LogCtx)(msg: c.Expr[String]): c.universe.Tree =
    reflectiveLog(c)(msg, None)(LogLevel.Info)

  def logDebugImpl(c: LogCtx)(msg: c.Expr[String]): c.universe.Tree =
    reflectiveLog(c)(msg, None)(LogLevel.Debug)

  def logWarnImpl(c: LogCtx)(msg: c.Expr[String]): c.universe.Tree =
    reflectiveLog(c)(msg, None)(LogLevel.Warn)

  def logErrorImpl(c: LogCtx)(msg: c.Expr[String]): c.universe.Tree =
    reflectiveLog(c)(msg, None)(LogLevel.Error)

  def logErrorWithCauseImpl(c: LogCtx)(msg: c.Expr[String], throwable: c.Expr[Throwable]): c.universe.Tree =
    reflectiveLog(c)(msg, Some(throwable))(LogLevel.Error)
}
