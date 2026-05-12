package example.testing

import zio._
import zio.http._
import zio.http.ChannelEvent.Read
import zio.test._

object TestChannelBidirectional extends ZIOSpecDefault {
  def spec = test("client and server exchange multiple messages") {
    // Server counts messages
    val countingServer: WebSocketApp[Any] = Handler.webSocket { channel =>
      var count = 0
      channel.receiveAll {
        case Read(WebSocketFrame.Text(_message)) =>
          count += 1
          channel.send(Read(WebSocketFrame.text(s"Message count: $count")))
        case _ => ZIO.unit
      }
    }

    // Client sends multiple messages
    val testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
      for {
        // Skip handshake complete event
        _ <- channel.receive
        _ <- channel.send(Read(WebSocketFrame.text("First")))
        resp1 <- channel.receive
        _ <- channel.send(Read(WebSocketFrame.text("Second")))
        resp2 <- channel.receive
        _ <- channel.shutdown
      } yield (resp1, resp2)
    }

    for {
      _ <- TestClient.installSocketApp(countingServer)
      response <- ZIO.serviceWithZIO[Client](_.socket(testClient))
    } yield assertTrue(response.status == Status.SwitchingProtocols)
  }.provide(TestClient.layer, Scope.default)
}
