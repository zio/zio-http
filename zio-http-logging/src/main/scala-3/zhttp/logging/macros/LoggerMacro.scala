package zhttp.logging.macros

import zhttp.logging.LoggerTransport
import zhttp.logging.Logger

import scala.annotation.tailrec
import scala.language.experimental.macros
import scala.quoted._

/** Macros that support the logging system.
 * inspired from log4s macro
 */
private[zhttp] object LoggerMacro {

  def logTraceImpl(transports: Expr[List[LoggerTransport]])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) =
  '{ $transports.filter(transport => transport.isTraceEnabled).foreach { transport =>
    transport.trace($msg, $tags, "", 1) }
  }

  def logDebugImpl(transports: Expr[List[LoggerTransport]])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) =
  '{ $transports.filter(transport => transport.isDebugEnabled).foreach { transport =>
    transport.debug($msg, $tags, "", 1) }
  }

  def logInfoImpl(transports: Expr[List[LoggerTransport]])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) =
  '{ $transports.filter(transport => transport.isInfoEnabled).foreach { transport =>
    transport.info($msg, $tags, "", 1) }
  }

  def logWarnImpl(transports: Expr[List[LoggerTransport]])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) =
  '{ $transports.filter(transport => transport.isWarnEnabled).foreach { transport =>
    transport.warn($msg, $tags, "", 1) }
  }

  def logErrorWithCauseImpl(transports: Expr[List[LoggerTransport]])(t: Expr[Throwable])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) =
  '{ $transports.filter(transport => transport.isErrorEnabled).foreach { transport =>
    transport.error($msg, $t, $tags, "", 1) }
  }

  def logErrorImpl(transports: Expr[List[LoggerTransport]])(msg: Expr[String])(tags: Expr[List[String]])(using qctx: Quotes) =
  '{ $transports.filter(transport => transport.isErrorEnabled).foreach { transport =>
    transport.error($msg, $tags, "", 1) }
  }


}
