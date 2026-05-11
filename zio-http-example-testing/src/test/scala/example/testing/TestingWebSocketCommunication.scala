package example.testing

import zio._
import zio.http._
import zio.http.ChannelEvent.Read
import zio.test._

object TestingWebSocketCommunication extends ZIOSpecDefault {
  def spec = test("echo server echoes messages") {
    val echoServer: WebSocketApp[Any] = Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Text(message)) =>
          channel.send(Read(WebSocketFrame.text(s"Echo: $message")))
        case _ => ZIO.unit
      }
    }

    val testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
      for {
        _ <- channel.send(Read(WebSocketFrame.text("Hello")))
        response <- channel.receive
        _ <- channel.shutdown
      } yield response
    }

    for {
      _ <- TestClient.installSocketApp(echoServer)
      response <- ZIO.serviceWithZIO[Client](_.socket(testClient))
    } yield assertTrue(response.status == Status.SwitchingProtocols)
  }.provide(TestClient.layer, Scope.default)
}
