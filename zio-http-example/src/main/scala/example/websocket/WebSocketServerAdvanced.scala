package example.websocket

import scala.annotation.nowarn

import zio._

import zio.http.ChannelEvent.{ExceptionCaught, Read, UserEvent, UserEventTriggered}
import zio.http._
import zio.http.codec.PathCodec.string

object WebSocketServerAdvanced extends ZIOAppDefault {

  val socketApp: WebSocketApp[Any] =
    Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Text("end"))                =>
          channel.shutdown

        // Send a "bar" if the client sends a "foo"
        case Read(WebSocketFrame.Text("foo"))                =>
          channel.send(Read(WebSocketFrame.text("bar")))

        // Send a "foo" if the client sends a "bar"
        case Read(WebSocketFrame.Text("bar"))                =>
          channel.send(Read(WebSocketFrame.text("foo")))

        // Echo the same message 10 times if it's not "foo" or "bar"
        case Read(WebSocketFrame.Text(text))                 =>
          channel
            .send(Read(WebSocketFrame.text(s"echo $text")))
            .repeatN(10)
            .catchSomeCause { case cause =>
              ZIO.logErrorCause(s"failed sending", cause)
            }

        // Send a "greeting" message to the client once the connection is established
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
      }
    }

  val routes: Routes[Any, Response] =
    Routes(
      Method.GET / "greet" / string("name") -> handler { (name: String, _: Request) =>
        Response.text(s"Greetings ${name}!")
      },
      Method.GET / "subscriptions"          -> handler(socketApp.toResponse),
    )

  override val run = Server.serve(routes).provide(Server.default)
}
