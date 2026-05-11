package example.testing

import zio._
import zio.http._
import zio.http.ChannelEvent.Read
import zio.test._

object TestChannelBasicWebSocket extends ZIOSpecDefault {
  def spec = test("echo server echoes back messages") {
    // Server handler - receives messages and echoes them
    val echoServer: WebSocketApp[Any] = Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Text(message)) =>
          channel.send(Read(WebSocketFrame.text(s"Echo: $message")))
        case _ => ZIO.unit
      }
    }

    // Client handler - sends message and receives echo
    val testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
      for {
        _ <- channel.send(Read(WebSocketFrame.text("Hello, Server!")))
        response <- channel.receive
        _ <- channel.shutdown
      } yield response
    }

    for {
      // Install the server handler
      _ <- TestClient.installSocketApp(echoServer)
      // Client connects and communicates with server
      response <- ZIO.serviceWithZIO[Client](_.socket(testClient))
    } yield assertTrue(response.status == Status.SwitchingProtocols)
  }.provide(TestClient.layer, Scope.default)
}
