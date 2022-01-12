package zhttp.http

final case class LogConfig(
  logRequest: Boolean,
  logResponse: Boolean,
  logRequestConfig: LogContentConfig,
  logResponseConfig: LogContentConfig,
)
final case class LogContentConfig(
  logHeaders: Boolean,
  filterHeaders: Headers => Headers,
)

final case class LogStep(lines: List[String])

object LOG {

  val DefaultLogContentConfig =
    LogContentConfig(
      logHeaders = true,
      filterHeaders = identity,
    )

  val DefaultLogConfig = LogConfig(
    logRequest = true,
    logResponse = true,
    logRequestConfig = DefaultLogContentConfig,
    logResponseConfig = DefaultLogContentConfig,
  )
}

sealed trait LogLevel
object LogLevel {
  case object Error extends LogLevel
  case object Info  extends LogLevel
}
