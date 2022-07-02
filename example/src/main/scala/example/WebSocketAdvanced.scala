package example

import zhttp.http._
import zhttp.service.ChannelEvent.Event.{ChannelRead, ExceptionCaught, UserEventTriggered}
import zhttp.service.ChannelEvent.UserEvent
import zhttp.service.{ChannelEvent, Server}
import zhttp.socket._
import zio._

object WebSocketAdvanced extends App {

  val httpSocket: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
    Http

      // Listen for all websocket channel events
      .collectZIO[WebSocketChannelEvent] {

        // Send a "greeting" message to the server once the connection is established
        case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete)) =>
          ch.writeAndFlush(WebSocketFrame.text("Greetings!"))

        case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("end")))          =>
          ch.close()

        // Send a "bar" if the server sends a "foo"
        case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("foo")))          =>
          ch.writeAndFlush(WebSocketFrame.text("bar"))

        // Send a "foo" if the server sends a "bar"
        case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("bar")))          =>
          ch.writeAndFlush(WebSocketFrame.text("foo"))

        // Echo the same message 10 times if it's not "foo" or "bar"
        // Improve performance by writing multiple frames at once
        // And flushing it on the channel only once.
        case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text(text)))           =>
          ch.write(WebSocketFrame.text(text)).repeatN(10) *> ch.flush

        // Log when the channel is getting closed
        case ChannelEvent(_, ChannelRead(WebSocketFrame.Close(status, reason))) =>
          UIO(println("Closing channel with status: " + status + " and reason: " + reason))

        // Print the exception if it's not a normal close
        case ChannelEvent(_, ExceptionCaught(cause))                            =>
          UIO(println(s"Channel error!: ${cause.getMessage}"))
      }

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
