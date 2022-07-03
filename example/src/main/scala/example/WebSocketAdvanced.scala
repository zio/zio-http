package example

import zhttp.http._
import zhttp.service.ChannelEvent.{ChannelRead, ExceptionCaught, UserEvent, UserEventTriggered}
import zhttp.service.{Channel, ChannelEvent, Server}
import zhttp.socket._
import zio._
import zio.console.Console

object WebSocketAdvanced extends App {

  private val messageFilter: Http[Any, Nothing, WebSocketChannelEvent, (Channel[WebSocketFrame], String)] =
    Http.collect[WebSocketChannelEvent] { case ChannelEvent(channel, ChannelRead(WebSocketFrame.Text(message))) =>
      (channel, message)
    }

  private val messageSocket: Http[Any, Throwable, WebSocketChannelEvent, Unit] = messageFilter >>>
    Http.collectZIO[(WebSocketChannel, String)] {
      case (ch, "end") => ch.close()

      // Send a "bar" if the server sends a "foo"
      case (ch, "foo") => ch.writeAndFlush(WebSocketFrame.text("bar"))

      // Send a "foo" if the server sends a "bar"
      case (ch, "bar") => ch.writeAndFlush(WebSocketFrame.text("foo"))

      // Echo the same message 10 times if it's not "foo" or "bar"
      // Improve performance by writing multiple frames at once
      // And flushing it on the channel only once.
      case (ch, text) =>
        ch.write(WebSocketFrame.text(text)).repeatN(10) *> ch.flush
    }

  private val channelSocket: Http[Console, Throwable, WebSocketChannelEvent, Unit] =
    Http.collectZIO[WebSocketChannelEvent] {

      // Send a "greeting" message to the server once the connection is established
      case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete))  =>
        ch.writeAndFlush(WebSocketFrame.text("Greetings!"))

      // Log when the channel is getting closed
      case ChannelEvent(_, ChannelRead(WebSocketFrame.Close(status, reason))) =>
        console.putStrLn("Closing channel with status: " + status + " and reason: " + reason)

      // Print the exception if it's not a normal close
      case ChannelEvent(_, ExceptionCaught(cause))                            =>
        console.putStrLn(s"Channel error!: ${cause.getMessage}")
    }

  private val httpSocket: Http[Console, Throwable, WebSocketChannelEvent, Unit] =
    messageSocket ++ channelSocket

  // Setup protocol settings
  private val protocol = SocketProtocol.subProtocol("json")

  // Setup decoder settings
  private val decoder = SocketDecoder.allowExtensions

  // Combine all channel handlers together
  private val socketApp: SocketApp[Console] =
    httpSocket.toSocketApp

      // Setup websocket decoder config
      .withDecoder(decoder)

      // Setup websocket protocol config
      .withProtocol(protocol)

  private val app =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "greet" / name  => UIO(Response.text(s"Greetings ${name}!"))
      case Method.GET -> !! / "subscriptions" => socketApp.toResponse
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
