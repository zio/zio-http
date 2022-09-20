package zio.http.service.logging

import zio.logging.LogLevel
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

object LogLevelTransform {
  implicit class LogLevelWrapper(level: LogLevel) {
    def toNettyLogLevel: io.netty.handler.logging.LogLevel = level match {
      case zio.logging.LogLevel.Trace => io.netty.handler.logging.LogLevel.TRACE
      case zio.logging.LogLevel.Debug => io.netty.handler.logging.LogLevel.DEBUG
      case zio.logging.LogLevel.Info  => io.netty.handler.logging.LogLevel.INFO
      case zio.logging.LogLevel.Warn  => io.netty.handler.logging.LogLevel.WARN
      case zio.logging.LogLevel.Error => io.netty.handler.logging.LogLevel.ERROR
    }
  }
}
