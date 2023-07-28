package zio.http

import zio._

final case class SocketApp[-R](
  handler: Handler[R, Throwable, WebSocketChannel, Any],
  customConfig: Handler[R, Throwable, Request, Option[WebSocketConfig]],
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

  def provideEnvironment(r: ZEnvironment[R])(implicit trace: Trace): SocketApp[Any] =
    SocketApp(handler.provideEnvironment(r), customConfig.provideEnvironment(r))

  def provideLayer[R0](layer: ZLayer[R0, Throwable, R])(implicit
    trace: Trace,
  ): SocketApp[R0] =
    SocketApp(handler.provideLayer(layer), customConfig.provideLayer(layer))

  def provideSomeEnvironment[R1](f: ZEnvironment[R1] => ZEnvironment[R])(implicit
    trace: Trace,
  ): SocketApp[R1] =
    SocketApp(handler.provideSomeEnvironment(f), customConfig.provideSomeEnvironment(f))

  def provideSomeLayer[R0, R1: Tag](
    layer: ZLayer[R0, Throwable, R1],
  )(implicit ev: R0 with R1 <:< R, trace: Trace): SocketApp[R0] =
    SocketApp(handler.provideSomeLayer(layer), customConfig.provideSomeLayer(layer))

  def tapErrorCauseZIO[R1 <: R](
    f: Cause[Throwable] => ZIO[R1, Throwable, Any],
  )(implicit trace: Trace): SocketApp[R1] =
    SocketApp(handler.tapErrorCauseZIO(f), customConfig.tapErrorCauseZIO(f))

  /**
   * Returns a Handler that effectfully peeks at the failure of this SocketApp.
   */
  def tapErrorZIO[R1 <: R](
    f: Throwable => ZIO[R1, Throwable, Any],
  )(implicit trace: Trace): SocketApp[R1] =
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
}

object SocketApp {
  def apply[R](handler: Handler[R, Throwable, WebSocketChannel, Any]): SocketApp[R] =
    SocketApp(handler, Handler.succeed(None))

  val unit: SocketApp[Any] = SocketApp(Handler.unit)
}
