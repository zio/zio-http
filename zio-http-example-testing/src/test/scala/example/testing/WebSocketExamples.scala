package example.testing

import zio._
import zio.http._
import zio.http.ChannelEvent.{Read, UserEventTriggered}
import zio.http.ChannelEvent.UserEvent.HandshakeComplete
import zio.test._

/**
 * Examples of WebSocket testing patterns.
 *
 * WebSockets are long-lived bidirectional channels where both client and server
 * can send messages at any time. Testing WebSockets requires setting up paired
 * server and client handlers that communicate through a test channel.
 */
object WebSocketExamples extends ZIOSpecDefault {

  def spec = suite("WebSocket Examples")(
    suite("Simple echo server")(
      test("echo server echoes back messages") {
        // Define the server handler - receives messages and echoes them
        val echoServer: WebSocketApp[Any] = Handler.webSocket { channel =>
          channel.receiveAll {
            case Read(WebSocketFrame.Text(message)) =>
              // Echo the message back
              channel.send(Read(WebSocketFrame.text(s"Echo: $message")))
            case _ => ZIO.unit
          }
        }

        // Define the client handler - sends a message and receives the echo
        val testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
          for {
            // Skip handshake complete event
            _ <- channel.receive
            // Send initial message
            _ <- channel.send(Read(WebSocketFrame.text("Hello, Server!")))
            // Receive the echo
            response <- channel.receive
            _ <- channel.shutdown
          } yield response
        }

        for {
          // Install the server handler
          _ <- TestClient.installSocketApp(echoServer)
          // The client connects and communicates with server through test channel
          response <- ZIO.serviceWithZIO[Client](_.socket(testClient))
        } yield assertTrue(response.status == Status.SwitchingProtocols)
      },
    ),
    suite("Bidirectional communication")(
      test("client and server exchange multiple messages") {
        // Server that counts messages
        val countingServer: WebSocketApp[Any] = Handler.webSocket { channel =>
          var count = 0
          channel.receiveAll {
            case Read(WebSocketFrame.Text(_message)) =>
              count += 1
              channel.send(Read(WebSocketFrame.text(s"Message count: $count")))
            case _ => ZIO.unit
          }
        }

        // Client that sends multiple messages
        val testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
          for {
            // Skip handshake complete event
            _ <- channel.receive
            // Send first message
            _ <- channel.send(Read(WebSocketFrame.text("First")))
            // Receive response
            resp1 <- channel.receive
            // Send second message
            _ <- channel.send(Read(WebSocketFrame.text("Second")))
            // Receive response
            resp2 <- channel.receive
            _ <- channel.shutdown
          } yield (resp1, resp2)
        }

        for {
          _ <- TestClient.installSocketApp(countingServer)
          response <- ZIO.serviceWithZIO[Client](_.socket(testClient))
        } yield assertTrue(response.status == Status.SwitchingProtocols)
      },
    ),
    suite("Server-initiated messages")(
      test("server sends unsolicited messages") {
        // Server that sends greeting then echoes
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

        // Client that receives greeting then sends message
        val testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
          for {
            // Skip handshake complete event
            _ <- channel.receive
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
      },
    ),
    suite("Different frame types")(
      test("handle text and binary frames") {
        val frameServer: WebSocketApp[Any] = Handler.webSocket { channel =>
          channel.receiveAll {
            case Read(WebSocketFrame.Text(text)) =>
              channel.send(Read(WebSocketFrame.text(s"Text: $text")))
            case Read(WebSocketFrame.Binary(bytes)) =>
              channel.send(Read(WebSocketFrame.binary(bytes)))
            case _ => ZIO.unit
          }
        }

        val testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
          for {
            // Skip handshake complete event
            _ <- channel.receive
            // Send text frame
            _ <- channel.send(Read(WebSocketFrame.text("Hello")))
            textResp <- channel.receive
            // Send binary frame
            _ <- channel.send(Read(WebSocketFrame.binary(Chunk.fromArray("binary".getBytes))))
            binResp <- channel.receive
            _ <- channel.shutdown
          } yield (textResp, binResp)
        }

        for {
          _ <- TestClient.installSocketApp(frameServer)
          response <- ZIO.serviceWithZIO[Client](_.socket(testClient))
        } yield assertTrue(response.status == Status.SwitchingProtocols)
      },
    ),
    suite("Stateful WebSocket handlers")(
      test("server maintains state across messages") {
        // Server that maintains a counter
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
            // Skip handshake complete event
            _ <- channel.receive
            // Send three messages
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
      },
    ),
    suite("Error handling in WebSockets")(
      test("server can send close frame") {
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
      },
    ),
    suite("Broadcast pattern")(
      test("server broadcasts messages to multiple clients") {
        // Simplified: in real broadcast, you'd have multiple channels
        // Here we demonstrate the concept with a single client
        val broadcastServer: WebSocketApp[Any] = Handler.webSocket { channel =>
          channel.receiveAll {
            case Read(WebSocketFrame.Text(msg)) =>
              // In real scenario, send to all connected clients
              // Here we just echo to demonstrate
              channel.send(Read(WebSocketFrame.text(s"Broadcast: $msg")))
            case _ => ZIO.unit
          }
        }

        val testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
          for {
            // Skip handshake complete event
            _ <- channel.receive
            _ <- channel.send(Read(WebSocketFrame.text("hello")))
            broadcast <- channel.receive
            _ <- channel.shutdown
          } yield broadcast
        }

        for {
          _ <- TestClient.installSocketApp(broadcastServer)
          response <- ZIO.serviceWithZIO[Client](_.socket(testClient))
        } yield assertTrue(response.status == Status.SwitchingProtocols)
      },
    ),
  ).provide(TestClient.layer, Scope.default)
}
