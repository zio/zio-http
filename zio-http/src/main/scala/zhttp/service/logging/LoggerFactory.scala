package zhttp.service.logging

import io.netty.util.internal.logging.{AbstractInternalLogger, InternalLogger, InternalLoggerFactory}
import zhttp.logging.{LogLevel, Logger, LoggerTransport}
import zhttp.service.logging.LoggerFactory.Live

final case class LoggerFactory(logLevel: LogLevel) extends InternalLoggerFactory {
  override def newInstance(name: String): InternalLogger = new Live(name, logLevel)
}

object LoggerFactory {
  final class Live(override val name: String, logLevel: LogLevel) extends AbstractInternalLogger(name) {
    private val loggerTransport = LoggerTransport.console
      .withLevel(logLevel)
    private val log             = Logger.make
      .withTransport(loggerTransport)
    private val nettyTag        = List("netty")

    override def isTraceEnabled: Boolean                           = loggerTransport.level == LogLevel.Trace
    override def trace(msg: String): Unit                          = log.trace(msg, nettyTag)
    override def trace(format: String, arg: Any): Unit             = log.trace(format.format(arg), nettyTag)
    override def trace(format: String, argA: Any, argB: Any): Unit = log.trace(format.format(argA, argB), nettyTag)
    override def trace(format: String, arguments: Object*): Unit   = log.trace(format.format(arguments), nettyTag)
    override def trace(msg: String, t: Throwable): Unit            = log.error(msg + "(trace)", t, nettyTag)
    override def isDebugEnabled: Boolean                           = loggerTransport.level == LogLevel.Debug
    override def debug(msg: String): Unit                          = log.debug(msg, nettyTag)
    override def debug(format: String, arg: Any): Unit             = log.debug(format.format(arg), nettyTag)
    override def debug(format: String, argA: Any, argB: Any): Unit = log.debug(format.format(argA, argB), nettyTag)
    override def debug(format: String, arguments: Object*): Unit   = log.debug(format.format(arguments), nettyTag)
    override def debug(msg: String, t: Throwable): Unit            = log.error(msg + "(debug)", t, nettyTag)
    override def isInfoEnabled: Boolean                            = loggerTransport.level == LogLevel.Info
    override def info(msg: String): Unit                           = log.info(msg, nettyTag)
    override def info(format: String, arg: Any): Unit              = log.info(format.format(arg), nettyTag)
    override def info(format: String, argA: Any, argB: Any): Unit  = log.info(format.format(argA, argB), nettyTag)
    override def info(format: String, arguments: Object*): Unit    = log.info(format.format(arguments), nettyTag)
    override def info(msg: String, t: Throwable): Unit             = log.error(msg + "(info)", t, nettyTag)
    override def isWarnEnabled: Boolean                            = loggerTransport.level == LogLevel.Warn
    override def warn(msg: String): Unit                           = log.warn(msg, nettyTag)
    override def warn(format: String, arg: Any): Unit              = log.warn(format.format(arg), nettyTag)
    override def warn(format: String, arguments: Object*): Unit    = log.warn(format.format(arguments), nettyTag)
    override def warn(format: String, argA: Any, argB: Any): Unit  = log.warn(format.format(argA, argB), nettyTag)
    override def warn(msg: String, t: Throwable): Unit             = log.error(msg + "(warn)", t, nettyTag)
    override def isErrorEnabled: Boolean                           = loggerTransport.level == LogLevel.Error
    override def error(msg: String): Unit                          = log.error(msg, nettyTag)
    override def error(format: String, arg: Any): Unit             = log.error(format.format(arg), nettyTag)
    override def error(format: String, argA: Any, argB: Any): Unit = log.error(format.format(argA, argB), nettyTag)
    override def error(format: String, arguments: Object*): Unit   = log.error(format.format(arguments), nettyTag)
    override def error(msg: String, t: Throwable): Unit            = log.error(msg, t, nettyTag)
  }
}
