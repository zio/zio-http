package zhttp.logging
import java.time.LocalDateTime

final case class LogLine(
  loggerName: String,
  date: LocalDateTime,
  threadName: String,
  threadId: String,
  logLevel: LogLevel,
  msg: String,
)
