package zio.http.socket

import zio._
import zio.http._
import zio.http.model.Headers
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class SocketApp[-R](
  decoder: SocketDecoder = SocketDecoder.default,
  protocol: SocketProtocol = SocketProtocol.default,
  message: Option[ChannelEvent[WebSocketFrame, WebSocketFrame] => ZIO[R, Throwable, Any]] = None,
) { self =>

  /**
   * Creates a socket connection on the provided URL. Typically used to connect
   * as a client.
   */
  def connect(
    url: String,
    headers: Headers = Headers.empty,
  )(implicit trace: Trace): ZIO[R with Client with Scope, Throwable, Response] =
    Client.socket(url, self, headers)

  /**
   * Provides the socket app with its required environment, which eliminates its
   * dependency on `R`.
   */
  def provideEnvironment(env: ZEnvironment[R])(implicit trace: Trace): SocketApp[Any] =
    self.copy(message = self.message.map(cb => event => cb(event).provideEnvironment(env)))

  /**
   * Converts the socket app to a HTTP app.
   */
  def toHttp(implicit trace: Trace): HttpApp[R, Nothing] = Http.fromZIO(toResponse)

  /**
   * Creates a new response from the socket app.
   */
  def toResponse(implicit trace: Trace): ZIO[R, Nothing, Response] =
    ZIO.environment[R].flatMap { env =>
      Response.fromSocketApp(self.provideEnvironment(env))
    }

  /**
   * Frame decoder configuration
   */
  def withDecoder(decoder: SocketDecoder): SocketApp[R] =
    copy(decoder = decoder, protocol = protocol.withDecoderConfig(decoder))

  /**
   * Server side websocket configuration
   */
  def withProtocol(protocol: SocketProtocol): SocketApp[R] =
    copy(protocol = protocol)
}

object SocketApp {

  def apply[R](socket: ChannelEvent[WebSocketFrame, WebSocketFrame] => ZIO[R, Throwable, Any]): SocketApp[R] =
    SocketApp(message = Option(socket))
}
