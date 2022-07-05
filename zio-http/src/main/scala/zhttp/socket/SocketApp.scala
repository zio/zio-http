package zhttp.socket

import zhttp.http.{Headers, Http, HttpApp, Response}
import zhttp.service.{ChannelEvent, ChannelFactory, Client, EventLoopGroup}
import zio.{NeedsEnv, ZIO, ZManaged}

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
  ): ZManaged[R with EventLoopGroup with ChannelFactory, Throwable, Response] =
    Client.socket(url, self, headers)

  /**
   * Provides the socket app with its required environment, which eliminates its
   * dependency on `R`.
   */
  def provideEnvironment(env: R)(implicit ev: NeedsEnv[R]): SocketApp[Any] = {
    copy(message = self.message.map(f => f(_).provide(env)))
  }

  /**
   * Converts the socket app to a HTTP app.
   */
  def toHttp: HttpApp[R, Nothing] = Http.fromZIO(toResponse)

  /**
   * Creates a new response from the socket app.
   */
  def toResponse: ZIO[R, Nothing, Response] =
    ZIO.environment[R].flatMap { env =>
      Response.fromSocketApp(self.provideEnvironment(env))
    }

  /**
   * Frame decoder configuration
   */
  def withDecoder(decoder: SocketDecoder): SocketApp[R] =
    copy(decoder = self.decoder ++ decoder)

  /**
   * Server side websocket configuration
   */
  def withProtocol(protocol: SocketProtocol): SocketApp[R] =
    copy(protocol = self.protocol ++ protocol)
}

object SocketApp {

  def apply[R](socket: ChannelEvent[WebSocketFrame, WebSocketFrame] => ZIO[R, Throwable, Any]): SocketApp[R] =
    SocketApp(message = Option(socket))
}
