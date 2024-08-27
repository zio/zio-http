package zio.http

import zio.{FiberRef, Unsafe}

/**
 * Configuration for the response generation
 *
 * @param withErrorBody
 *   if true, includes the error message in the response body
 * @param withStackTrace
 *   if true, includes the stack trace in the response body
 * @param maxStackTraceDepth
 *   maximum number of stack trace lines to include in the response body. Set to
 *   0 to include all lines.
 */
final case class ErrorResponseConfig(
  withErrorBody: Boolean = false,
  withStackTrace: Boolean = false,
  maxStackTraceDepth: Int = 10,
  errorFormat: ErrorResponseConfig.ErrorFormat = ErrorResponseConfig.ErrorFormat.Html,
)

object ErrorResponseConfig {
  sealed trait ErrorFormat { val mediaType: MediaType }
  object ErrorFormat       {
    case object Text extends ErrorFormat { val mediaType: MediaType = MediaType.text.`plain`     }
    case object Html extends ErrorFormat { val mediaType: MediaType = MediaType.text.html        }
    case object Json extends ErrorFormat { val mediaType: MediaType = MediaType.application.json }
  }

  val default: ErrorResponseConfig     = ErrorResponseConfig()
  val debugConfig: ErrorResponseConfig =
    ErrorResponseConfig(withErrorBody = true, withStackTrace = true, maxStackTraceDepth = 0)

  lazy val debug: HandlerAspect[Any, Unit] =
    Middleware.runBefore(configRef.set(debugConfig))

  def withConfig(config: ErrorResponseConfig): HandlerAspect[Any, Unit] =
    Middleware.runBefore(configRef.updateSome { case oldConfig if oldConfig != config => config })

  private[http] lazy val configRef: FiberRef[ErrorResponseConfig] =
    FiberRef.unsafe.make(default)(Unsafe)
}
