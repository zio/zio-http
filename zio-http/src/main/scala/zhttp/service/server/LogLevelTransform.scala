package zhttp.service.server
import zhttp.logging.LogLevel

object LogLevelTransform {
  implicit class LogLevelWrapper(level: LogLevel) {
    def toNettyLogLevel: io.netty.handler.logging.LogLevel = level match {
      case zhttp.logging.LogLevel.OFF   => io.netty.handler.logging.LogLevel.ERROR
      case zhttp.logging.LogLevel.TRACE => io.netty.handler.logging.LogLevel.TRACE
      case zhttp.logging.LogLevel.DEBUG => io.netty.handler.logging.LogLevel.DEBUG
      case zhttp.logging.LogLevel.INFO  => io.netty.handler.logging.LogLevel.INFO
      case zhttp.logging.LogLevel.WARN  => io.netty.handler.logging.LogLevel.WARN
      case zhttp.logging.LogLevel.ERROR => io.netty.handler.logging.LogLevel.ERROR
    }
  }

}
