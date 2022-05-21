package zhttp.service.server
import zhttp.logging.LogLevel

object LogLevelTransform {
  implicit class LogLevelWrapper(level: LogLevel) {
    def toNettyLogLevel: io.netty.handler.logging.LogLevel = level match {
      case zhttp.logging.LogLevel.Trace => io.netty.handler.logging.LogLevel.TRACE
      case zhttp.logging.LogLevel.Debug => io.netty.handler.logging.LogLevel.DEBUG
      case zhttp.logging.LogLevel.Info  => io.netty.handler.logging.LogLevel.INFO
      case zhttp.logging.LogLevel.Warn  => io.netty.handler.logging.LogLevel.WARN
      case zhttp.logging.LogLevel.Error => io.netty.handler.logging.LogLevel.ERROR
    }
  }

}
