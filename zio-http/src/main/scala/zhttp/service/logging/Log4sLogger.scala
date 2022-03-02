package zhttp.service.logging

import io.netty.util.internal.logging.AbstractInternalLogger
import zhttp.logging.LogLevel
import zhttp.logging.Logger.getLogger

final case class Log4sLogger(override val name: String, logLevel: LogLevel) extends AbstractInternalLogger(name) {
  private val log = getLogger(name, logLevel)

  override def isTraceEnabled: Boolean                           = log.isTraceEnabled
  override def trace(msg: String): Unit                          = log.trace(msg)
  override def trace(format: String, arg: Any): Unit             = log.trace(format.format(arg))
  override def trace(format: String, argA: Any, argB: Any): Unit = log.trace(format.format(argA, argB))
  override def trace(format: String, arguments: Any*): Unit      = log.trace(format.format(arguments))
  override def trace(msg: String, t: Throwable): Unit            = log.trace(t)(msg)
  override def isDebugEnabled: Boolean                           = log.isDebugEnabled
  override def debug(msg: String): Unit                          = log.debug(msg)
  override def debug(format: String, arg: Any): Unit             = log.debug(format.format(arg))
  override def debug(format: String, argA: Any, argB: Any): Unit = log.debug(format.format(argA, argB))
  override def debug(format: String, arguments: Any*): Unit      = log.debug(format.format(arguments))
  override def debug(msg: String, t: Throwable): Unit            = log.debug(t)(msg)
  override def isInfoEnabled: Boolean                            = log.isInfoEnabled
  override def info(msg: String): Unit                           = log.info(msg)
  override def info(format: String, arg: Any): Unit              = log.info(format.format(arg))
  override def info(format: String, argA: Any, argB: Any): Unit  = log.info(format.format(argA, argB))
  override def info(format: String, arguments: Any*): Unit       = log.info(format.format(arguments))
  override def info(msg: String, t: Throwable): Unit             = log.info(t)(msg)
  override def isWarnEnabled: Boolean                            = log.isWarnEnabled
  override def warn(msg: String): Unit                           = log.warn(msg)
  override def warn(format: String, arg: Any): Unit              = log.warn(format.format(arg))
  override def warn(format: String, arguments: Any*): Unit       = log.warn(format.format(arguments))
  override def warn(format: String, argA: Any, argB: Any): Unit  = log.warn(format.format(argA, argB))
  override def warn(msg: String, t: Throwable): Unit             = log.warn(t)(msg)
  override def isErrorEnabled: Boolean                           = log.isErrorEnabled
  override def error(msg: String): Unit                          = log.error(msg)
  override def error(format: String, arg: Any): Unit             = log.error(format.format(arg))
  override def error(format: String, argA: Any, argB: Any): Unit = log.error(format.format(argA, argB))
  override def error(format: String, arguments: Any*): Unit      = log.error(format.format(arguments))
  override def error(msg: String, t: Throwable): Unit            = log.error(t)(msg)
}
