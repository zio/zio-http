package example.testing

import zio._
import zio.http._
import zio.http.ChannelEvent.Read
import zio.test._

/**
 * Testing HTTP Applications — Testing WebSocket Connections
 *
 * Demonstrates how to test WebSocket bidirectional communication using TestChannel
 * with a simple echo server and client pattern.
 *
 * Run with: sbt "zio-http-example-testing/testOnly example.testing.GuideWebSocketEchoSpec"
 */
object GuideWebSocketEchoSpec extends ZIOSpecDefault {
  def spec = test("echo server echoes messages") {
    // The server receives messages and echoes them back
    val echoServer: WebSocketApp[Any] = Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Text(message)) =>
          // When we receive text, send it back with "Echo: " prefix
          channel.send(Read(WebSocketFrame.text(s"Echo: $message")))
        case _ => ZIO.unit
      }
    }

    // The client sends a message and expects to receive the echo
    val testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
      for {
        // Send a message
        _ <- channel.send(Read(WebSocketFrame.text("Hello, Server!")))
        // Wait to receive the response
        response <- channel.receive
        _ <- channel.shutdown
      } yield response
    }

    for {
      // Install the server handler in TestClient
      _ <- TestClient.installSocketApp(echoServer)
      // The client calls socket() with its handler
      response <- ZIO.serviceWithZIO[Client](_.socket(testClient))
    } yield assertTrue(response.status == Status.SwitchingProtocols)
  }.provide(TestClient.layer, Scope.default)
}
