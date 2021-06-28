package zhttp.socket

import io.netty.channel.{ChannelHandler => JChannelHandler}
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.{
  HandshakeComplete => JHandshakeComplete,
  ServerHandshakeStateEvent => JServerHandshakeStateEvent,
}
import zhttp.core.{JChannelHandlerContext, JSimpleChannelInboundHandler, JWebSocketFrame}
import zhttp.http.Response
import zhttp.service.{ChannelFuture, UnsafeChannelExecutor}
import zhttp.socket.WebSocketFrame
import zio._
import zio.stream.ZStream

import java.net.{SocketAddress => JSocketAddress}

sealed trait SocketApp[-R, +E] { self =>
  import SocketApp._
  def ++[R1 <: R, E1 >: E](other: SocketApp[R1, E1]): SocketApp[R1, E1] = SocketApp.Concat(self, other)

  def asResponse: Response[R, E] = Response.SocketResponse(self)

  def config: SocketApp.SocketConfig[R, E] = {
    def loop(config: SocketApp[R, E], s: SocketConfig[R, E]): SocketConfig[R, E] =
      config match {
        case Empty => s

        case Decoder(decoder) => s.copy(decoder = s.decoder ++ decoder)

        case Protocol(protocol) => s.copy(protocol = s.protocol ++ protocol)

        case OnTimeout(onTimeout) =>
          s.copy(onTimeout = s.onTimeout.fold(Option(onTimeout))(v => Option(v &> onTimeout)))

        case a: Open[_, _] =>
          s.copy(onOpen = s.onOpen match {
            case Some(b) => Option(b merge a)
            case None    => Option(a)
          })

        case OnMessage(a) =>
          s.copy(onMessage = s.onMessage match {
            case Some(b) => Option(b merge a)
            case None    => Option(a)
          })

        case OnError(onError) =>
          s.copy(onError = s.onError.fold(Option(onError))(v => Option(c => v(c) &> onError(c))))

        case OnClose(onClose) =>
          s.copy(onClose = s.onClose.fold(Option(onClose))(v => Option(c => v(c) &> onClose(c))))

        case Concat(a, b) =>
          loop(b, loop(a, s))
      }

    loop(self, SocketConfig[R, E]())
  }

}

object SocketApp {
  type Connection = JSocketAddress
  type Cause      = Option[Throwable]

  private[zhttp] sealed trait Open[-R, +E] extends SocketApp[R, E] { self =>
    import Open._
    private def sock: Open[R, E] = self match {
      case WithEffect(f)     => WithSocket(Socket.fromFunction(c => ZStream.fromEffect(f(c)) *> ZStream.empty))
      case s @ WithSocket(_) => s
    }

    def merge[R1 <: R, E1 >: E](other: Open[R1, E1]): Open[R1, E1] = {
      (self, other) match {
        case (WithSocket(s0), WithSocket(s1)) => WithSocket(s0 merge s1)
        case (WithEffect(f0), WithEffect(f1)) => WithEffect(c => f0(c) <&> f1(c))
        case (a, b)                           => a.sock merge b.sock
      }
    }
  }

  private[zhttp] object Open {
    final case class WithEffect[R, E](f: Connection => ZIO[R, E, Any])             extends Open[R, E]
    final case class WithSocket[R, E](s: Socket[R, E, Connection, WebSocketFrame]) extends Open[R, E]
  }
  private final case class Concat[R, E](a: SocketApp[R, E], b: SocketApp[R, E]) extends SocketApp[R, E]
  private final case class OnMessage[R, E](onMessage: Socket[R, E, WebSocketFrame, WebSocketFrame])
      extends SocketApp[R, E]
  private final case class OnError[R](onError: Throwable => ZIO[R, Nothing, Any])  extends SocketApp[R, Nothing]
  private final case class OnClose[R](onClose: Connection => ZIO[R, Nothing, Any]) extends SocketApp[R, Nothing]
  private final case class OnTimeout[R](onTimeout: ZIO[R, Nothing, Any])           extends SocketApp[R, Nothing]
  private final case class Protocol(protocol: SocketProtocol)                      extends SocketApp[Any, Nothing]
  private final case class Decoder(decoder: SocketDecoder)                         extends SocketApp[Any, Nothing]
  private case object Empty                                                        extends SocketApp[Any, Nothing]

  /**
   * Called when the connection is successfully upgrade to a websocket one. In case of a failure on the returned stream,
   * the socket is forcefully closed.
   */
  def open[R, E](onOpen: Socket[R, E, Connection, WebSocketFrame]): SocketApp[R, E] =
    SocketApp.Open.WithSocket(onOpen)

  /**
   * Called when the connection is successfully upgrade to a websocket one. In case of a failure on the returned stream,
   * the socket is forcefully closed.
   */
  def open[R, E](onOpen: Connection => ZIO[R, E, Any]): SocketApp[R, E] =
    SocketApp.Open.WithEffect(onOpen)

  /**
   * Called when the handshake gets timeout.
   */
  def timeout[R](onTimeout: ZIO[R, Nothing, Any]): SocketApp[R, Nothing] = SocketApp.OnTimeout(onTimeout)

  /**
   * Called on every incoming WebSocketFrame. In case of a failure on the returned stream, the socket is forcefully
   * closed.
   */
  def message[R, E](onMessage: Socket[R, E, WebSocketFrame, WebSocketFrame]): SocketApp[R, E] =
    SocketApp.OnMessage(onMessage)

  /**
   * Called whenever there is an error on the channel after a successful upgrade to websocket.
   */
  def error[R](onError: Throwable => ZIO[R, Nothing, Any]): SocketApp[R, Nothing] = SocketApp.OnError(onError)

  /**
   * Called when the websocket connection is closed successfully.
   */
  def close[R](onClose: (Connection) => ZIO[R, Nothing, Any]): SocketApp[R, Nothing] =
    SocketApp.OnClose(onClose)

  /**
   * Frame decoder configuration
   */
  def decoder(decoder: SocketDecoder): SocketApp[Any, Nothing] = Decoder(decoder)

  /**
   * Server side websocket configuration
   */
  def protocol(protocol: SocketProtocol): SocketApp[Any, Nothing] = Protocol(protocol)

  def protocol(name: String): SocketApp[Any, Nothing] = Protocol(SocketProtocol.subProtocol(name))

  /**
   * Creates a new empty socket handler
   */
  def empty: SocketApp[Any, Nothing] = Empty

  // TODO: rename to HandlerConfig
  final case class SocketConfig[-R, +E](
    onTimeout: Option[ZIO[R, Nothing, Any]] = None,
    onOpen: Option[Open[R, E]] = None,
    onMessage: Option[Socket[R, E, WebSocketFrame, WebSocketFrame]] = None,
    onError: Option[Throwable => ZIO[R, Nothing, Any]] = None,
    onClose: Option[Connection => ZIO[R, Nothing, Any]] = None,
    decoder: SocketDecoder = SocketDecoder.default,
    protocol: SocketProtocol = SocketProtocol.default,
  )

  private[zhttp] def compile[R](zExec: UnsafeChannelExecutor[R], app: SocketApp[R, Throwable]): JChannelHandler =
    return new JSimpleChannelInboundHandler[JWebSocketFrame] {
      val ss = app.config

      /**
       * Unsafe channel reader for WSFrame
       */

      private def writeAndFlush(ctx: JChannelHandlerContext, stream: ZStream[R, Throwable, WebSocketFrame]): Unit =
        zExec.unsafeExecute_(ctx)(
          stream
            .mapM(frame => ChannelFuture.unit(ctx.writeAndFlush(frame.toJWebSocketFrame)))
            .runDrain,
        )

      override def channelRead0(ctx: JChannelHandlerContext, msg: JWebSocketFrame): Unit =
        ss.onMessage match {
          case Some(v) =>
            WebSocketFrame.fromJFrame(msg) match {
              case Some(frame) => writeAndFlush(ctx, v(frame))
              case _           => ()
            }
          case None    => ()
        }

      override def exceptionCaught(ctx: JChannelHandlerContext, x: Throwable): Unit = {
        ss.onError match {
          case Some(v) => zExec.unsafeExecute_(ctx)(v(x).uninterruptible)
          case None    => ctx.fireExceptionCaught(x)
        }
        ()
      }

      override def channelUnregistered(ctx: JChannelHandlerContext): Unit = {
        ss.onClose match {
          case Some(v) => zExec.unsafeExecute_(ctx)(v(ctx.channel().remoteAddress()).uninterruptible)
          case None    => ctx.fireChannelUnregistered()
        }
        ()
      }

      override def userEventTriggered(ctx: JChannelHandlerContext, event: AnyRef): Unit = {

        event match {
          case _: JHandshakeComplete                                                              =>
            ss.onOpen match {
              case Some(v) =>
                v match {
                  case Open.WithEffect(f) => zExec.unsafeExecute_(ctx)(f(ctx.channel().remoteAddress()))
                  case Open.WithSocket(s) => writeAndFlush(ctx, s(ctx.channel().remoteAddress()))
                }
              case None    => ctx.fireUserEventTriggered(event)
            }
          case m: JServerHandshakeStateEvent if m == JServerHandshakeStateEvent.HANDSHAKE_TIMEOUT =>
            ss.onTimeout match {
              case Some(v) => zExec.unsafeExecute_(ctx)(v)
              case None    => ctx.fireUserEventTriggered(event)
            }
          case event                                                                              => ctx.fireUserEventTriggered(event)
        }
        ()
      }
    }

}
