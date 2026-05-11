package example.testing

import zio._
import zio.http._
import zio.http.ChannelEvent.Read
import zio.test._

object TestChannelServerInitiatedMessages extends ZIOSpecDefault {
  def spec = test("server sends greeting then echoes client messages") {
    val greetingServer: WebSocketApp[Any] = Handler.webSocket { channel =>
      for {
        // Send greeting immediately
        _ <- channel.send(Read(WebSocketFrame.text("Welcome to the server!")))
        // Then handle incoming messages
        _ <- channel.receiveAll {
          case Read(WebSocketFrame.Text(msg)) =>
            channel.send(Read(WebSocketFrame.text(s"You said: $msg")))
          case _ => ZIO.unit
        }
      } yield ()
    }

    val testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
      for {
        // Receive greeting
        greeting <- channel.receive
        // Send a message
        _ <- channel.send(Read(WebSocketFrame.text("Hello!")))
        // Receive echo
        echo <- channel.receive
        _ <- channel.shutdown
      } yield (greeting, echo)
    }

    for {
      _ <- TestClient.installSocketApp(greetingServer)
      response <- ZIO.serviceWithZIO[Client](_.socket(testClient))
    } yield assertTrue(response.status == Status.SwitchingProtocols)
  }.provide(TestClient.layer, Scope.default)
}
