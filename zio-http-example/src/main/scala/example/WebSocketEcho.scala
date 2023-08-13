package example

import zio._

import zio.http.ChannelEvent.Read
import zio.http._
import zio.http.codec.PathCodec.string

object WebSocketEcho extends ZIOAppDefault {
  private val socketApp: WebSocketApp[Any] =
    Handler.webSocket { channel =>
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

  private val app: HttpApp[Any] =
    Routes(
      Method.GET / "greet" / string("name") -> handler { (name: String, _: Request) =>
        Response.text(s"Greetings {$name}!")
      },
      Method.GET / "subscriptions"          -> handler(socketApp.toResponse),
    ).toHttpApp

  override val run = Server.serve(app).provide(Server.default)
}
