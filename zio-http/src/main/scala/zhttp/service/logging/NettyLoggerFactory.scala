package zhttp.service.logging

import io.netty.util.internal.logging.{AbstractInternalLogger, InternalLogger, InternalLoggerFactory}
import zhttp.logging.{LogLevel, Logger}
import zhttp.service.logging.NettyLoggerFactory.Live

/**
 * Custom implementation that uses the zhttp logger's transport for logging
 * netty messages.
 */
final case class NettyLoggerFactory(logger: Logger) extends InternalLoggerFactory {
  override def newInstance(name: String): InternalLogger = new Live(name, logger: Logger)
}

object NettyLoggerFactory {
  private final class Live(override val name: String, logger: Logger) extends AbstractInternalLogger(name) {
    private val log                                                = logger.withTags("Netty")
    override def debug(msg: String): Unit                          = log.debug(msg)
    override def debug(format: String, arg: Any): Unit             = log.debug(format.format(arg))
    override def debug(format: String, argA: Any, argB: Any): Unit = log.debug(format.format(argA, argB))
    override def debug(format: String, arguments: Object*): Unit   = log.debug(format.format(arguments))
    override def debug(msg: String, t: Throwable): Unit            = log.error(msg + "(debug)", t)
    override def error(msg: String): Unit                          = log.error(msg)
    override def error(format: String, arg: Any): Unit             = log.error(format.format(arg))
    override def error(format: String, argA: Any, argB: Any): Unit = log.error(format.format(argA, argB))
    override def error(format: String, arguments: Object*): Unit   = log.error(format.format(arguments))
    override def error(msg: String, t: Throwable): Unit            = log.error(msg, t)
    override def info(msg: String): Unit                           = log.info(msg)
    override def info(format: String, arg: Any): Unit              = log.info(format.format(arg))
    override def info(format: String, argA: Any, argB: Any): Unit  = log.info(format.format(argA, argB))
    override def info(format: String, arguments: Object*): Unit    = log.info(format.format(arguments))
    override def info(msg: String, t: Throwable): Unit             = log.error(msg + "(info)", t)
    override def isDebugEnabled: Boolean                           = log.transports.exists(_.level == LogLevel.Debug)
    override def isErrorEnabled: Boolean                           = log.transports.exists(_.level == LogLevel.Error)
    override def isInfoEnabled: Boolean                            = log.transports.exists(_.level == LogLevel.Info)
    override def isTraceEnabled: Boolean                           = log.transports.exists(_.level == LogLevel.Trace)
    override def isWarnEnabled: Boolean                            = log.transports.exists(_.level == LogLevel.Warn)
    override def trace(msg: String): Unit                          = log.trace(msg)
    override def trace(format: String, arg: Any): Unit             = log.trace(format.format(arg))
    override def trace(format: String, argA: Any, argB: Any): Unit = log.trace(format.format(argA, argB))
    override def trace(format: String, arguments: Object*): Unit   = log.trace(format.format(arguments))
    override def trace(msg: String, t: Throwable): Unit            = log.error(msg + "(trace)", t)
    override def warn(msg: String): Unit                           = log.warn(msg)
    override def warn(format: String, arg: Any): Unit              = log.warn(format.format(arg))
    override def warn(format: String, arguments: Object*): Unit    = log.warn(format.format(arguments))
    override def warn(format: String, argA: Any, argB: Any): Unit  = log.warn(format.format(argA, argB))
    override def warn(msg: String, t: Throwable): Unit             = log.error(msg + "(warn)", t)
  }
}
