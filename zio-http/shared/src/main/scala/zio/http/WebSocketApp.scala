package zio.http

import zio._

/**
 * A WebSocket application: a handler that receives the `WebSocketChannel` of a connection, with an optional `WebSocketConfig` of its own. It serves a connection on the server (`toResponse`, `toRoutes`) and opens one on the client (`connect`).
 *
 * @param handler
 *   runs for each connection, with the channel of that connection
 * @param customConfig
 *   the `WebSocketConfig` for this application. With `None`, the server's `Server.Config#webSocketConfig` or the client's `ZClient.Config#webSocketConfig` applies.
 *
 * @see [[https://zio.dev/zio-http/reference/socket/ Socket]]
 */
final case class WebSocketApp[-R](
  handler: Handler[R, Throwable, WebSocketChannel, Any],
  customConfig: Option[WebSocketConfig],
) { self =>

  /**
   * Creates a socket connection on the provided URL. Typically used to connect
   * as a client. A `url` that is not a valid URL ends the effect with a
   * defect.
   */
  def connect(
    url: String,
    headers: Headers = Headers.empty,
  )(implicit
    trace: Trace,
  ): ZIO[R with Client with Scope, Throwable, Response] =
    ZIO.fromEither(URL.decode(url)).orDie.flatMap(connect(_, headers))

  /**
   * Creates a socket connection on the provided URL. Typically used to connect as a client. A relative `url` is added to the URL of the client in the environment, and an absolute one replaces it.
   */
  def connect(
    url: URL,
    headers: Headers,
  )(implicit
    trace: Trace,
  ): ZIO[R with Client with Scope, Throwable, Response] =
    ZIO.serviceWithZIO[Client] { client =>
      val client2 = if (url.isAbsolute) client.url(url) else client.addUrl(url)

      client2.addHeaders(headers).socket(self)
    }

  /** Provides the environment to the handler. The custom config is kept. */
  def provideEnvironment(r: ZEnvironment[R])(implicit trace: Trace): WebSocketApp[Any] =
    WebSocketApp(handler.provideEnvironment(r), customConfig)

  /** Provides the layer to the handler. The custom config is kept. */
  def provideLayer[R0](layer: ZLayer[R0, Throwable, R])(implicit
    trace: Trace,
  ): WebSocketApp[R0] =
    WebSocketApp(handler.provideLayer(layer), customConfig)

  /** Transforms the environment the handler needs with `f`. The custom config is kept. */
  def provideSomeEnvironment[R1](f: ZEnvironment[R1] => ZEnvironment[R])(implicit
    trace: Trace,
  ): WebSocketApp[R1] =
    WebSocketApp(handler.provideSomeEnvironment(f), customConfig)

  /**
   * Provides part of the environment of the handler with the layer. The custom config is kept.
   */
  def provideSomeLayer[R0, R1: Tag](
    layer: ZLayer[R0, Throwable, R1],
  )(implicit ev: R0 with R1 <:< R, trace: Trace): WebSocketApp[R0] =
    WebSocketApp(handler.provideSomeLayer(layer), customConfig)

  /** Runs `f` with the cause when the handler fails. */
  def tapErrorCauseZIO[R1 <: R](
    f: Cause[Throwable] => ZIO[R1, Throwable, Any],
  )(implicit trace: Trace): WebSocketApp[R1] =
    WebSocketApp(handler.tapErrorCauseZIO(f), customConfig)

  /**
   * Runs `f` with the error when the handler fails with a `Throwable`. A defect
   * or an interruption is not passed to `f`.
   */
  def tapErrorZIO[R1 <: R](
    f: Throwable => ZIO[R1, Throwable, Any],
  )(implicit trace: Trace): WebSocketApp[R1] =
    self.tapErrorCauseZIO(cause => cause.failureOption.fold[ZIO[R1, Throwable, Any]](ZIO.unit)(f))

  /**
   * Creates a new response from a socket handler.
   */
  def toResponse(implicit
    trace: Trace,
  ): ZIO[R, Nothing, Response] =
    ZIO.environment[R].flatMap { env =>
      Response.fromSocketApp(self.provideEnvironment(env))
    }

  /**
   * Returns `Routes` with a single route that matches every method and path and answers with `toResponse`.
   */
  def toRoutes(implicit trace: Trace): Routes[R, Response] =
    Handler.fromZIO(self.toResponse).toRoutes

  /** Returns a copy that uses `config` as its custom config. */
  def withConfig(config: WebSocketConfig): WebSocketApp[R] =
    copy(customConfig = Some(config))
}

/** Constructors for `WebSocketApp`. */
object WebSocketApp {
  /** Creates an application from the handler, without a custom config. */
  def apply[R](handler: Handler[R, Throwable, WebSocketChannel, Any]): WebSocketApp[R] =
    WebSocketApp(handler, None)

  /** An application whose handler does nothing. */
  val unit: WebSocketApp[Any] = WebSocketApp(Handler.unit)
}
