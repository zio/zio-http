package example

import zio._

import zio.http.ChannelEvent.{ExceptionCaught, Read, UserEvent, UserEventTriggered}
import zio.http._

object WebSocketAdvanced extends ZIOAppDefault {

  val httpSocket: Http[Any, Throwable, WebSocketChannel, Unit] =
    Http.collectZIO[WebSocketChannel] { case channel =>
      channel.receive.flatMap {
        case Read(WebSocketFrame.Text("end"))                =>
          channel.shutdown

        // Send a "bar" if the server sends a "foo"
        case Read(WebSocketFrame.Text("foo"))                =>
          channel.send(Read(WebSocketFrame.text("bar")))

        // Send a "foo" if the server sends a "bar"
        case Read(WebSocketFrame.Text("bar"))                =>
          channel.send(Read(WebSocketFrame.text("foo")))

        // Echo the same message 10 times if it's not "foo" or "bar"
        case Read(WebSocketFrame.Text(text))                 =>
          channel.send(Read(WebSocketFrame.text(text))).repeatN(10)

        // Send a "greeting" message to the server once the connection is established
        case UserEventTriggered(UserEvent.HandshakeComplete) =>
          channel.send(Read(WebSocketFrame.text("Greetings!")))

        // Log when the channel is getting closed
        case Read(WebSocketFrame.Close(status, reason))      =>
          Console.printLine("Closing channel with status: " + status + " and reason: " + reason)

        // Print the exception if it's not a normal close
        case ExceptionCaught(cause)                          =>
          Console.printLine(s"Channel error!: ${cause.getMessage}")

        case _ =>
          ZIO.unit
      }.forever
    }

  val socketApp: SocketApp[Any] =
    httpSocket.toSocketApp

  val app: Http[Any, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "greet" / name  => ZIO.succeed(Response.text(s"Greetings ${name}!"))
      case Method.GET -> !! / "subscriptions" => socketApp.toResponse
    }

  override val run = Server.serve(app).provide(Server.default)
}
