package zhttp.logging

final case class Configuration(
  loggerName: String,
  logLevel: LogLevel,
  logFormat: LogFormat,
  filter: String => Boolean = _ => true,
)
