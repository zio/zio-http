package example

import zio._
import zio.http.ChannelEvent.{ChannelRead, ExceptionCaught, UserEvent, UserEventTriggered}
import zio.http._
import zio.http.model.Method
import zio.http.socket._

/**
 * To interact with this program from the terminal:
 *    websocat ws://127.0.0.1:8080/subscription
 */
object WebSocketAdvanced extends ZIOAppDefault {
  val messageFilter: Http[Any, Nothing, WebSocketChannelEvent, (Channel[WebSocketFrame], String)] =
    Http.collect[WebSocketChannelEvent] { case ChannelEvent(channel, ChannelRead(WebSocketFrame.Text(message))) =>
      (channel, message)
    }

  val messageSocket: Http[Any, Throwable, WebSocketChannelEvent, Unit] = messageFilter >>>
    Http.collectZIO[(WebSocketChannel, String)] {
      case (ch, text) if text.contains("end") =>
        ZIO.debug("User text: " + text) *>
        ch.writeAndFlush(WebSocketFrame.text("Ending at user's request."), await = true) *>
        ZIO.debug("Ending connection") *>
        ch.close()

      // Send a "bar" if the server sends a "foo"
      case (ch, "foo") => ch.writeAndFlush(WebSocketFrame.text("bar"))
      case (ch, text) if text.contains("bar") => ch.writeAndFlush(WebSocketFrame.text("Bar message: " + text))

      // Send a "foo" if the server sends a "bar"
      case (ch, text) if text.contains("foo") => ch.writeAndFlush(WebSocketFrame.text("Foo message: " + text))

      // Echo the same message 10 times if it's not "foo" or "bar"
      // Improve performance by writing multiple frames at once
      // And flushing it on the channel only once.
      case (ch, text) =>
        println("Unrecognized message: " + text)
        (ch.write(WebSocketFrame.text(text)) *> ZIO.sleep(100.millis) *> ch.flush).repeatN(2) *> ch.writeAndFlush(WebSocketFrame.text("Ok. Done Spamming!"))
    }

  val channelSocket: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
    Http.collectZIO[WebSocketChannelEvent] {

      // Send a "greeting" message to the server once the connection is established
      case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete))  =>
        ch.writeAndFlush(WebSocketFrame.text("Greetings!"))

      case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeTimeout))  =>
        ch.write("Should this be possible?")

      // Log when the channel is getting closed
      case ChannelEvent(_, ChannelRead(WebSocketFrame.Close(status, reason))) =>
        Console.printLine("Closing channel with status: " + status + " and reason: " + reason)

      // Print the exception if it's not a normal close
      case ChannelEvent(_, ExceptionCaught(cause))                            =>
        Console.printLine(s"Channel error!: ${cause.getMessage}")
    }

  val httpSocket: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
    messageSocket ++ channelSocket

  val protocol = SocketProtocol.default.withSubProtocol(Some("json")) // Setup protocol settings

  val decoder = SocketDecoder.default.withExtensions(allowed = true) // Setup decoder settings

  val socketApp: SocketApp[Any] = // Combine all channel handlers together
    httpSocket.toSocketApp
      .withDecoder(decoder)   // Setup websocket decoder config
      .withProtocol(protocol) // Setup websocket protocol config

  val app: Http[Any, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "greet" / name  => ZIO.succeed(Response.text(s"Greetings ${name}!"))
      case Method.GET -> !! / "subscriptions" => socketApp.toResponse
    }

  override val run = Server.serve(app).provide(Server.default)
}
