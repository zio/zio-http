package zhttp.logging
import java.time.LocalDateTime

final case class LogLine(
  name: String,
  timestamp: LocalDateTime,
  thread: Thread,
  level: LogLevel,
  message: String,
  tags: List[String],
  error: Option[Throwable],
  enclosingClass: String,
  lineNumber: Int,
)
