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

    for {
      receivedFrame <- Promise.make[Nothing, WebSocketFrame]
      testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
        for {
          // Skip handshake complete event
          _ <- channel.receive
          _ <- channel.send(Read(WebSocketFrame.text("Hello")))
          response <- channel.receive
          _ <- response match {
            case Read(frame) => receivedFrame.succeed(frame)
            case _ => receivedFrame.fail(new Exception("Expected ChannelEvent.Read"))
          }
          _ <- channel.shutdown
        } yield ()
      }

      _ <- TestClient.installSocketApp(echoServer)
      _ <- ZIO.serviceWithZIO[Client](_.socket(testClient))
      frame <- receivedFrame.await
    } yield assertTrue(
      frame match {
        case WebSocketFrame.Text(msg) => msg == "Echo: Hello"
        case _ => false
      }
    )
  }.provide(TestClient.layer, Scope.default)
}
