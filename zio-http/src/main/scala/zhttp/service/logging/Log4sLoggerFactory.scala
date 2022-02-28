package zhttp.service.logging
import io.netty.util.internal.logging.{InternalLogger, InternalLoggerFactory}

object Log4sLoggerFactory extends InternalLoggerFactory {
  override def newInstance(name: String): InternalLogger = Log4sLogger(name)
}
