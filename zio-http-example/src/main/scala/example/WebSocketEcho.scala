package example

import zio._

import zio.http.ChannelEvent.Read
import zio.http._

object WebSocketEcho extends ZIOAppDefault {
  private val socket: Http[Any, Throwable, WebSocketChannel, Unit] =
    Http.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Text("FOO")) =>
          channel.send(Read(WebSocketFrame.Text("BAR")))
        case Read(WebSocketFrame.Text("BAR")) =>
          channel.send(Read(WebSocketFrame.Text("FOO")))
        case Read(WebSocketFrame.Text(text))  =>
          channel.send(Read(WebSocketFrame.Text(text))).repeatN(10)
        case _                                =>
          ZIO.unit
      }
    }

  private val app: Http[Any, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "greet" / name  => ZIO.succeed(Response.text(s"Greetings {$name}!"))
      case Method.GET -> !! / "subscriptions" => socket.toSocketApp.toResponse
    }

  override val run = Server.serve(app).provide(Server.default)
}
