package zio.http.socket

import zio.{ZIO, _}
import zio.http._
import zio.http.model.Headers
import zio.http.socket.SocketApp.{SocketAppHandler, SocketHandler}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class SocketApp[-R](
  decoder: SocketDecoder = SocketDecoder.default,
  protocol: SocketProtocol = SocketProtocol.default,
  message: SocketAppEvent => Option[ZIO[R, Throwable, SocketAppAction]] = SocketAppHandler.noOp.lift
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
    self.copy(message = self.message.andThen(_.map(_.provideEnvironment(env))))

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

  def || [R1](that: SocketApp[R1]): SocketApp[R with R1] = copy(message = e => message(e).orElse(that.message(e)))
  def && [R1](that: SocketApp[R1])(implicit trace: Trace): SocketApp[R with R1] = copy(message = { e =>
    val effects = message(e) ++ that.message(e)
    if(effects.isEmpty) None else Some (
      ZIO.collectAll(effects).map(_.foldLeft(SocketAppAction.NoOp: SocketAppAction)((a, b) => a + b))
    )
  })
}

object SocketApp {

  type SocketHandler[-R] = PartialFunction[SocketAppEvent, ZIO[R, Throwable, SocketAppAction]]

  object SocketAppHandler {
    val noOp: SocketHandler[Any] = { case _ => ZIO.succeed(SocketAppAction.NoOp)(Trace.empty) }
  }

}

trait SocketAppChannel {
  def action(socketAction: SocketAppAction)(implicit trace: Trace): Task[Unit]
}

object SocketAppChannel {
  def apply(channel: Channel[WebSocketFrame]): SocketAppChannel =
    new SocketAppChannel {
      override def action(socketAction: SocketAppAction)(implicit trace: Trace): Task[Unit] =
        socketAction match {
          case SocketAppAction.CloseSocket => channel.close()
          case SocketAppAction.SendFrame(message) => channel.write(message)
          case SocketAppAction.NoOp => ZIO.unit
          case SocketAppAction.Multiple(actions) => ZIO.foreachDiscard(actions)(action)
          case SocketAppAction.Flush => channel.flush
        }
    }
}

sealed trait SocketAppEvent
object SocketAppEvent {
  final case class Connected(channel: SocketAppChannel) extends SocketAppEvent
  case object Disconnected extends SocketAppEvent
  final case class FrameReceived(frame: WebSocketFrame) extends SocketAppEvent
  final case class Error(cause: Throwable) extends SocketAppEvent
}

sealed trait SocketAppAction {
  def + (other: SocketAppAction): SocketAppAction = SocketAppAction.Multiple(Seq(this, other))
  def withFlush: SocketAppAction = this + SocketAppAction.Flush
}
object SocketAppAction {
  case object NoOp extends SocketAppAction
  case object CloseSocket extends SocketAppAction
  final case class SendFrame(frame: WebSocketFrame) extends SocketAppAction
  final case class Multiple(actions: Seq[SocketAppAction]) extends SocketAppAction {
    override def + (other: SocketAppAction): SocketAppAction = SocketAppAction.Multiple(actions :+ other)
  }
  case object Flush extends SocketAppAction
  object Multiple {
    def apply(action: SocketAppAction, actions: SocketAppAction*): Multiple = Multiple(action +: actions)
  }
}


