package zio.http.logging

import java.time.LocalDateTime

import zio.http.logging.Logger.SourcePos

final case class LogLine(
  timestamp: LocalDateTime,
  thread: Thread,
  level: LogLevel,
  message: String,
  tags: List[String],
  error: Option[Throwable],
  sourceLocation: Option[SourcePos],
)
