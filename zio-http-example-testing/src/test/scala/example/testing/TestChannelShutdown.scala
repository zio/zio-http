package example.testing

import zio._
import zio.http._
import zio.http.ChannelEvent.Read
import zio.test._

object TestChannelShutdown extends ZIOSpecDefault {
  def spec = test("server can shutdown connection") {
    val closeServer: WebSocketApp[Any] = Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Text("close")) =>
          channel.shutdown
        case Read(WebSocketFrame.Text(msg)) =>
          channel.send(Read(WebSocketFrame.text(s"Echo: $msg")))
        case _ => ZIO.unit
      }
    }

    val testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
      for {
        // Skip handshake complete event
        _ <- channel.receive
        _ <- channel.send(Read(WebSocketFrame.text("hello")))
        _ <- channel.receive
        _ <- channel.send(Read(WebSocketFrame.text("close")))
        _ <- channel.shutdown
      } yield ()
    }

    for {
      _ <- TestClient.installSocketApp(closeServer)
      response <- ZIO.serviceWithZIO[Client](_.socket(testClient))
    } yield assertTrue(response.status == Status.SwitchingProtocols)
  }.provide(TestClient.layer, Scope.default)
}
