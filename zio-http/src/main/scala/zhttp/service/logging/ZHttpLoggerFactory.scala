package zhttp.service.logging
import io.netty.util.internal.logging.{InternalLogger, InternalLoggerFactory}
import zhttp.logging.LogLevel

final case class ZHttpLoggerFactory(logLevel: LogLevel) extends InternalLoggerFactory {
  override def newInstance(name: String): InternalLogger = ZHttpLogger(name, logLevel)
}
