package zhttp.service.logging
import io.netty.util.internal.logging.{InternalLogger, InternalLoggerFactory}
import zhttp.logging.LogLevel

final case class Log4sLoggerFactory(logLevel: LogLevel) extends InternalLoggerFactory {
  override def newInstance(name: String): InternalLogger = Log4sLogger(name, logLevel)
}
