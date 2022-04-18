package zhttp.logging

final case class LoggerTransport(level: LogLevel, format: _ => CharSequence, transport: Transport) { self =>
  def withFormat(format: LogLine => CharSequence): LoggerTransport = self.copy(format = format)
  def withFormat(format: LogFormat): LoggerTransport               = self.copy(format = LogFormat.run(format)(_))
  def withLevel(level: LogLevel): LoggerTransport                  = self.copy(level = level)
}

object LoggerTransport {

  def console: LoggerTransport = LoggerTransport(
    level = LogLevel.OFF,
    format = LogFormat.default,
    transport = Transport.UnsafeSync(println),
  )

}
