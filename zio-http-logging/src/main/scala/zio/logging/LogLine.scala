package zio.http.logging

import zio.http.logging.Logger.SourcePos

import java.time.LocalDateTime

final case class LogLine(
  timestamp: LocalDateTime,
  thread: Thread,
  level: LogLevel,
  message: String,
  tags: List[String],
  error: Option[Throwable],
  sourceLocation: Option[SourcePos],
)
