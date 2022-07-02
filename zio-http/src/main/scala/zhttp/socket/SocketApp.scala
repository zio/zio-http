package zhttp.socket

import zhttp.http.{Http, Response}
import zhttp.service.{ChannelEvent, ChannelFactory, Client, EventLoopGroup}
import zio.{NeedsEnv, ZIO, ZManaged}

import java.net.SocketAddress

final case class SocketApp[-R](
  decoder: SocketDecoder = SocketDecoder.default,
  protocol: SocketProtocol = SocketProtocol.default,
  message: Option[ChannelEvent[WebSocketFrame, WebSocketFrame] => ZIO[R, Throwable, Any]] = None,
) { self =>

  /**
   * Creates a socket connection on the provided URL. Typically used to connect
   * as a client.
   */
  def connect(url: String): ZManaged[R with EventLoopGroup with ChannelFactory, Throwable, Response] =
    Client.socket(url, self)

  /**
   * Provides the socket app with its required environment, which eliminates its
   * dependency on `R`.
   */
  def provideEnvironment(env: R)(implicit ev: NeedsEnv[R]): SocketApp[Any] = {
    copy(message = self.message.map(f => f(_).provide(env)))
  }

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
  type Connection = SocketAddress

  def apply[R](socket: ChannelEvent[WebSocketFrame, WebSocketFrame] => ZIO[R, Throwable, Any]): SocketApp[R] =
    SocketApp(message = Some(socket))

  def apply[R](socket: Http[R, Throwable, ChannelEvent[WebSocketFrame, WebSocketFrame], Unit]): SocketApp[R] =
    SocketApp(message =
      Some(event =>
        socket(event).catchAll {
          case Some(value) => ZIO.fail(value)
          case None        => ZIO.unit
        },
      ),
    )
}
