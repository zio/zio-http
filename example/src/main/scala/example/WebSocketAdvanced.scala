package example

import zhttp.http._
import zhttp.service.Server
import zhttp.socket._
import zio._
import zio.stream.ZStream

object WebSocketAdvanced extends ZIOAppDefault {
  // Message Handlers
  private val open = Socket.succeed(WebSocketFrame.text("Greetings!"))

  private val echo = Socket.collect[WebSocketFrame] { case WebSocketFrame.Text(text) =>
    ZStream.repeat(WebSocketFrame.text(s"Received: $text")).schedule(Schedule.spaced(1 second)).take(3)
  }

  private val fooBar = Socket.collect[WebSocketFrame] {
    case WebSocketFrame.Text("FOO") => ZStream.succeed(WebSocketFrame.text("BAR"))
    case WebSocketFrame.Text("BAR") => ZStream.succeed(WebSocketFrame.text("FOO"))
  }

  // Setup protocol settings
  private val protocol = SocketProtocol.subProtocol("json")

  // Setup decoder settings
  private val decoder = SocketDecoder.allowExtensions

  // Combine all channel handlers together
  private val socketApp = {

    SocketApp(echo merge fooBar) // Called after each message being received on the channel

      // Called after the request is successfully upgraded to websocket
      .onOpen(open)

      // Called after the connection is closed
      .onClose(_ => Console.printLine("Closed!").ignore)

      // Called whenever there is an error on the socket channel
      .onError(_ => Console.printLine("Error!").ignore)

      // Setup websocket decoder config
      .withDecoder(decoder)

      // Setup websocket protocol config
      .withProtocol(protocol)
  }

  private val app =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "greet" / name  => ZIO.succeed(Response.text(s"Greetings ${name}!"))
      case Method.GET -> !! / "subscriptions" => socketApp.toResponse
    }

  override val run =
    Server.start(8090, app)
}
