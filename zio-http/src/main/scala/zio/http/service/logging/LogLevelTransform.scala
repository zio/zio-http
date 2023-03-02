package zio.http.service.logging

import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.logging.LogLevel

object LogLevelTransform {
  implicit class LogLevelWrapper(level: LogLevel) {
    def toNettyLogLevel: io.netty.handler.logging.LogLevel = level match {
      case zio.http.logging.LogLevel.Trace => io.netty.handler.logging.LogLevel.TRACE
      case zio.http.logging.LogLevel.Debug => io.netty.handler.logging.LogLevel.DEBUG
      case zio.http.logging.LogLevel.Info  => io.netty.handler.logging.LogLevel.INFO
      case zio.http.logging.LogLevel.Warn  => io.netty.handler.logging.LogLevel.WARN
      case zio.http.logging.LogLevel.Error => io.netty.handler.logging.LogLevel.ERROR
    }
  }
}
