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

    for {
      // Use a Promise to coordinate between the forked client fiber and this test
      receivedFrame <- Promise.make[Throwable, WebSocketFrame]

      // The client sends a message and expects to receive the echo
      testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
        for {
          // Skip handshake complete event
          _ <- channel.receive
          // Send a message
          _ <- channel.send(Read(WebSocketFrame.text("Hello, Server!")))
          // Wait to receive the response
          response <- channel.receive
          // Signal the received frame to the outer test
          _ <- response match {
            case Read(frame) => receivedFrame.succeed(frame)
            case _ => receivedFrame.fail(new Exception("Expected ChannelEvent.Read"))
          }
          _ <- channel.shutdown
        } yield ()
      }

      // Install the server handler in TestClient
      _ <- TestClient.installSocketApp(echoServer)
      // The client calls socket() with its handler
      _ <- ZIO.serviceWithZIO[Client](_.socket(testClient))
      // Wait for the frame and verify it matches the expected echo
      frame <- receivedFrame.await
    } yield assertTrue(
      frame match {
        case WebSocketFrame.Text(msg) => msg == "Echo: Hello, Server!"
        case _ => false
      }
    )
  }.provide(TestClient.layer, Scope.default)
}
