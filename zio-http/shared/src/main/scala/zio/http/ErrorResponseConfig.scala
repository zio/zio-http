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
)

/**
 * The default configurations, the error formats, and the ways to set the
 * configuration in effect.
 */
object ErrorResponseConfig {

  /**
   * The format of an error response body: `Text`, `Html` or `Json`. `mediaType`
   * is the media type of the response.
   */
  sealed trait ErrorFormat { val mediaType: MediaType }

  /** The error formats. */
  object ErrorFormat {

    /** The `text/plain` format. */
    case object Text extends ErrorFormat { val mediaType: MediaType = MediaType.text.`plain` }

    /** The `text/html` format. */
    case object Html extends ErrorFormat { val mediaType: MediaType = MediaType.text.html }

    /** The `application/json` format. */
    case object Json extends ErrorFormat { val mediaType: MediaType = MediaType.application.json }
  }

  /**
   * The default configuration, `ErrorResponseConfig()`: no error body, no stack
   * trace, a stack trace depth of 10, and `ErrorFormat.Html`.
   */
  val default: ErrorResponseConfig = ErrorResponseConfig()

  /**
   * A configuration that includes the error message and the full stack trace in
   * the response body, with `ErrorFormat.Html`.
   */
  val debugConfig: ErrorResponseConfig =
    ErrorResponseConfig(withErrorBody = true, withStackTrace = true, maxStackTraceDepth = 0)

  private[http] val configRef: FiberRef[ErrorResponseConfig] =
    FiberRef.unsafe.make(if (Mode.isDev) debugConfig else default)(Unsafe)

  /** An aspect that sets `debugConfig` before the handler runs. */
  val debug: HandlerAspect[Any, Unit] =
    Middleware.runBefore(setConfig(debugConfig))

  /** A layer that sets `debugConfig` for the effects it is provided to. */
  val debugLayer: ULayer[Unit] =
    ZLayer(setConfig(debugConfig))

  /** An aspect that sets `config` before the handler runs. */
  def withConfig(config: ErrorResponseConfig): HandlerAspect[Any, Unit] =
    Middleware.runBefore(setConfig(config))

  /** Sets `config` as the configuration of the current fiber. */
  def setConfig(config: ErrorResponseConfig): ZIO[Any, Nothing, Unit] =
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (state, _) =>
      val existing = state.getFiberRef(configRef)
      if (existing != config) state.setFiberRef(configRef, config)
      Exit.unit
    }

  /** A layer that sets `config` for the effects it is provided to. */
  def configLayer(config: ErrorResponseConfig): ULayer[Unit] =
    ZLayer(setConfig(config))
}
