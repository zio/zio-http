package zio.http

import zio._

final case class WebSocketApp[-R](
  handler: Handler[R, Throwable, WebSocketChannel, Any],
  customConfig: Option[WebSocketConfig],
) { self =>

  /**
   * Creates a socket connection on the provided URL. Typically used to connect
   * as a client.
   */
  def connect(
    url: String,
    headers: Headers = Headers.empty,
  )(implicit
    trace: Trace,
  ): ZIO[R with Client with Scope, Throwable, Response] =
    ZIO.fromEither(URL.decode(url)).orDie.flatMap(connect(_, headers))

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

  def provideEnvironment(r: ZEnvironment[R])(implicit trace: Trace): WebSocketApp[Any] =
    WebSocketApp(handler.provideEnvironment(r), customConfig)

  def provideLayer[R0](layer: ZLayer[R0, Throwable, R])(implicit
    trace: Trace,
  ): WebSocketApp[R0] =
    WebSocketApp(handler.provideLayer(layer), customConfig)

  def provideSomeEnvironment[R1](f: ZEnvironment[R1] => ZEnvironment[R])(implicit
    trace: Trace,
  ): WebSocketApp[R1] =
    WebSocketApp(handler.provideSomeEnvironment(f), customConfig)

  def provideSomeLayer[R0, R1: Tag](
    layer: ZLayer[R0, Throwable, R1],
  )(implicit ev: R0 with R1 <:< R, trace: Trace): WebSocketApp[R0] =
    WebSocketApp(handler.provideSomeLayer(layer), customConfig)

  def tapErrorCauseZIO[R1 <: R](
    f: Cause[Throwable] => ZIO[R1, Throwable, Any],
  )(implicit trace: Trace): WebSocketApp[R1] =
    WebSocketApp(handler.tapErrorCauseZIO(f), customConfig)

  /**
   * Returns a Handler that effectfully peeks at the failure of this SocketApp.
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

  def toHttpAppWS(implicit trace: Trace): HttpApp[R] =
    Handler.fromZIO(self.toResponse).toHttpApp

  def withConfig(config: WebSocketConfig): WebSocketApp[R] =
    copy(customConfig = Some(config))
}

object WebSocketApp {
  def apply[R](handler: Handler[R, Throwable, WebSocketChannel, Any]): WebSocketApp[R] =
    WebSocketApp(handler, None)

  val unit: WebSocketApp[Any] = WebSocketApp(Handler.unit)
}
