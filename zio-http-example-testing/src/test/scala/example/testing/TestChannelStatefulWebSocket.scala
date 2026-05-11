package example.testing

import zio._
import zio.http._
import zio.http.ChannelEvent.Read
import zio.test._

object TestChannelStatefulWebSocket extends ZIOSpecDefault {
  def spec = test("server maintains counter across messages") {
    val statefulServer: WebSocketApp[Any] = Handler.webSocket { channel =>
      for {
        counter <- Ref.make(0)
        _ <- channel.receiveAll {
          case Read(WebSocketFrame.Text(_)) =>
            for {
              count <- counter.updateAndGet(_ + 1)
              _ <- channel.send(Read(WebSocketFrame.text(s"Count: $count")))
            } yield ()
          case _ => ZIO.unit
        }
      } yield ()
    }

    val testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
      for {
        _ <- channel.send(Read(WebSocketFrame.text("msg1")))
        count1 <- channel.receive
        _ <- channel.send(Read(WebSocketFrame.text("msg2")))
        count2 <- channel.receive
        _ <- channel.send(Read(WebSocketFrame.text("msg3")))
        count3 <- channel.receive
        _ <- channel.shutdown
      } yield (count1, count2, count3)
    }

    for {
      _ <- TestClient.installSocketApp(statefulServer)
      response <- ZIO.serviceWithZIO[Client](_.socket(testClient))
    } yield assertTrue(response.status == Status.SwitchingProtocols)
  }.provide(TestClient.layer, Scope.default)
}
