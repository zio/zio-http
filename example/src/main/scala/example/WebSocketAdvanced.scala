package example

import zhttp.http._
import zhttp.service.ChannelEvent.{ChannelRead, ExceptionCaught, UserEvent, UserEventTriggered}
import zhttp.service.{ChannelEvent, Server}
import zhttp.socket._
import zio._

object WebSocketAdvanced extends App {

  private val messageSocket: Http[Any, Throwable, (WebSocketChannel, String), Unit] =
    Http.collectZIO[(WebSocketChannel, String)] {
      case (ch, "end") => ch.close()

      // Send a "bar" if the server sends a "foo"
      case (ch, "foo") => ch.writeAndFlush(WebSocketFrame.text("bar"))

      // Send a "foo" if the server sends a "bar"
      case (ch, "bar") => ch.writeAndFlush(WebSocketFrame.text("foo"))

      // Echo the same message 10 times if it's not "foo" or "bar"
      // Improve performance by writing multiple frames at once
      // And flushing it on the channel only once.
      case (ch, text) => ch.write(WebSocketFrame.text(text)).repeatN(10) *> ch.flush
    }

  private val handshakeCompleteSocket: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
    Http.collectZIO[WebSocketChannelEvent] {
      // Send a "greeting" message to the server once the connection is established
      case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete)) =>
        ch.writeAndFlush(WebSocketFrame.text("Greetings!"))
    }

  private val closeSocket: Http[Any, Nothing, WebSocketChannelEvent, Unit] =
    Http.collectZIO[WebSocketChannelEvent] {
      // Log when the channel is getting closed
      case ChannelEvent(_, ChannelRead(WebSocketFrame.Close(status, reason))) =>
        UIO(println("Closing channel with status: " + status + " and reason: " + reason))
    }

  private val exceptionSocket: Http[Any, Nothing, WebSocketChannelEvent, Unit] =
    Http.collectZIO[WebSocketChannelEvent] {
      // Print the exception if it's not a normal close
      case ChannelEvent(_, ExceptionCaught(cause)) =>
        UIO(println(s"Channel error!: ${cause.getMessage}"))
    }

  // A middleware that filters out events containing websocket text frames
  // And only allows them to be passed to the Http app.
  private val webSocketChannelEvent
    : Middleware[Any, Nothing, (WebSocketChannel, String), Unit, WebSocketChannelEvent, Unit] =
    new Middleware[Any, Nothing, (WebSocketChannel, String), Unit, WebSocketChannelEvent, Unit] {
      override def apply[R1 <: Any, E1 >: Nothing](
        http: Http[R1, E1, (WebSocketChannel, String), Unit],
      ): Http[R1, E1, WebSocketChannelEvent, Unit] =
        http.contraCollect[WebSocketChannelEvent] {
          case ChannelEvent(channel, ChannelRead(WebSocketFrame.Text(message))) => (channel, message)
        }
    }

  private val httpSocket: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
    messageSocket @@ webSocketChannelEvent ++ handshakeCompleteSocket ++ closeSocket ++ exceptionSocket

  // Setup protocol settings
  private val protocol = SocketProtocol.subProtocol("json")

  // Setup decoder settings
  private val decoder = SocketDecoder.allowExtensions

  // Combine all channel handlers together
  private val socketApp = {

    httpSocket.toSocketApp // Called after each message being received on the channel

      // Setup websocket decoder config
      .withDecoder(decoder)

      // Setup websocket protocol config
      .withProtocol(protocol)
  }

  private val app =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "greet" / name  => UIO(Response.text(s"Greetings ${name}!"))
      case Method.GET -> !! / "subscriptions" => socketApp.toResponse
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
