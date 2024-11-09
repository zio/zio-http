package zio.http

import zio._

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
 * @param errorFormat
 *   the preferred format for the error response. If the context in which the
 *   response is created has access to an Accept header, the header will be used
 *   preferably to determine the format.
 */
final case class ErrorResponseConfig(
  withErrorBody: Boolean = false,
  withStackTrace: Boolean = false,
  maxStackTraceDepth: Int = 10,
  errorFormat: ErrorResponseConfig.ErrorFormat = ErrorResponseConfig.ErrorFormat.Html,
  logCodecErrors: Boolean = false,
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
    ErrorResponseConfig(withErrorBody = true, withStackTrace = true, maxStackTraceDepth = 0, logCodecErrors = true)

  private[http] val configRef: FiberRef[ErrorResponseConfig] =
    FiberRef.unsafe.make(default)(Unsafe)

  val debug: HandlerAspect[Any, Unit] =
    Middleware.runBefore(setConfig(debugConfig))

  val debugLayer: ULayer[Unit] =
    ZLayer(setConfig(debugConfig))

  def withConfig(config: ErrorResponseConfig): HandlerAspect[Any, Unit] =
    Middleware.runBefore(setConfig(config))

  def setConfig(config: ErrorResponseConfig): ZIO[Any, Nothing, Unit] =
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (state, _) =>
      val existing = state.getFiberRef(configRef)
      if (existing != config) state.setFiberRef(configRef, config)
      Exit.unit
    }

  def configLayer(config: ErrorResponseConfig): ULayer[Unit] =
    ZLayer(setConfig(config))
}
