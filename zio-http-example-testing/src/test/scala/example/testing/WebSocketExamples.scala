package example.testing

import zio._
import zio.http._
import zio.http.ChannelEvent.Read
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

        for {
          receivedFrame <- Promise.make[Nothing, WebSocketFrame]

          // Define the client handler - sends a message and receives the echo
          testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
            for {
              // Skip handshake complete event
              _ <- channel.receive
              // Send initial message
              _ <- channel.send(Read(WebSocketFrame.text("Hello, Server!")))
              // Receive the echo
              response <- channel.receive
              _ <- receivedFrame.succeed(response.asInstanceOf[Read].frame)
              _ <- channel.shutdown
            } yield ()
          }

          // Install the server handler
          _ <- TestClient.installSocketApp(echoServer)
          // The client connects and communicates with server through test channel
          _ <- ZIO.serviceWithZIO[Client](_.socket(testClient))
          frame <- receivedFrame.await
        } yield assertTrue(
          frame match {
            case WebSocketFrame.Text(msg) => msg == "Echo: Hello, Server!"
            case _ => false
          }
        )
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

        for {
          resp1Frame <- Promise.make[Nothing, WebSocketFrame]
          resp2Frame <- Promise.make[Nothing, WebSocketFrame]

          // Client that sends multiple messages
          testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
            for {
              // Skip handshake complete event
              _ <- channel.receive
              // Send first message
              _ <- channel.send(Read(WebSocketFrame.text("First")))
              // Receive response
              resp1 <- channel.receive
              _ <- resp1Frame.succeed(resp1.asInstanceOf[Read].frame)
              // Send second message
              _ <- channel.send(Read(WebSocketFrame.text("Second")))
              // Receive response
              resp2 <- channel.receive
              _ <- resp2Frame.succeed(resp2.asInstanceOf[Read].frame)
              _ <- channel.shutdown
            } yield ()
          }

          _ <- TestClient.installSocketApp(countingServer)
          _ <- ZIO.serviceWithZIO[Client](_.socket(testClient))
          frame1 <- resp1Frame.await
          frame2 <- resp2Frame.await
        } yield assertTrue(
          (frame1 match {
            case WebSocketFrame.Text(msg) => msg == "Message count: 1"
            case _ => false
          }) && (frame2 match {
            case WebSocketFrame.Text(msg) => msg == "Message count: 2"
            case _ => false
          })
        )
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

        for {
          greetingFrame <- Promise.make[Nothing, WebSocketFrame]
          echoFrame <- Promise.make[Nothing, WebSocketFrame]

          // Client that receives greeting then sends message
          testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
            for {
              // Skip handshake complete event
              _ <- channel.receive
              // Receive greeting
              greeting <- channel.receive
              _ <- greetingFrame.succeed(greeting.asInstanceOf[Read].frame)
              // Send a message
              _ <- channel.send(Read(WebSocketFrame.text("Hello!")))
              // Receive echo
              echo <- channel.receive
              _ <- echoFrame.succeed(echo.asInstanceOf[Read].frame)
              _ <- channel.shutdown
            } yield ()
          }

          _ <- TestClient.installSocketApp(greetingServer)
          _ <- ZIO.serviceWithZIO[Client](_.socket(testClient))
          greeting <- greetingFrame.await
          echo <- echoFrame.await
        } yield assertTrue(
          (greeting match {
            case WebSocketFrame.Text(msg) => msg == "Welcome to the server!"
            case _ => false
          }) && (echo match {
            case WebSocketFrame.Text(msg) => msg == "You said: Hello!"
            case _ => false
          })
        )
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

        for {
          textRespFrame <- Promise.make[Nothing, WebSocketFrame]
          binRespFrame <- Promise.make[Nothing, WebSocketFrame]

          testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
            for {
              // Skip handshake complete event
              _ <- channel.receive
              // Send text frame
              _ <- channel.send(Read(WebSocketFrame.text("Hello")))
              textResp <- channel.receive
              _ <- textRespFrame.succeed(textResp.asInstanceOf[Read].frame)
              // Send binary frame
              _ <- channel.send(Read(WebSocketFrame.binary(Chunk.fromArray("binary".getBytes))))
              binResp <- channel.receive
              _ <- binRespFrame.succeed(binResp.asInstanceOf[Read].frame)
              _ <- channel.shutdown
            } yield ()
          }

          _ <- TestClient.installSocketApp(frameServer)
          _ <- ZIO.serviceWithZIO[Client](_.socket(testClient))
          textFrame <- textRespFrame.await
          binFrame <- binRespFrame.await
        } yield assertTrue(
          (textFrame match {
            case WebSocketFrame.Text(msg) => msg == "Text: Hello"
            case _ => false
          }) && (binFrame match {
            case WebSocketFrame.Binary(bytes) => bytes.mkString == "binary"
            case _ => false
          })
        )
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

        for {
          count1Frame <- Promise.make[Nothing, WebSocketFrame]
          count2Frame <- Promise.make[Nothing, WebSocketFrame]
          count3Frame <- Promise.make[Nothing, WebSocketFrame]

          testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
            for {
              // Skip handshake complete event
              _ <- channel.receive
              // Send three messages
              _ <- channel.send(Read(WebSocketFrame.text("msg1")))
              count1 <- channel.receive
              _ <- count1Frame.succeed(count1.asInstanceOf[Read].frame)
              _ <- channel.send(Read(WebSocketFrame.text("msg2")))
              count2 <- channel.receive
              _ <- count2Frame.succeed(count2.asInstanceOf[Read].frame)
              _ <- channel.send(Read(WebSocketFrame.text("msg3")))
              count3 <- channel.receive
              _ <- count3Frame.succeed(count3.asInstanceOf[Read].frame)
              _ <- channel.shutdown
            } yield ()
          }

          _ <- TestClient.installSocketApp(statefulServer)
          _ <- ZIO.serviceWithZIO[Client](_.socket(testClient))
          frame1 <- count1Frame.await
          frame2 <- count2Frame.await
          frame3 <- count3Frame.await
        } yield assertTrue(
          (frame1 match {
            case WebSocketFrame.Text(msg) => msg == "Count: 1"
            case _ => false
          }) && (frame2 match {
            case WebSocketFrame.Text(msg) => msg == "Count: 2"
            case _ => false
          }) && (frame3 match {
            case WebSocketFrame.Text(msg) => msg == "Count: 3"
            case _ => false
          })
        )
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

        for {
          echoFrame <- Promise.make[Nothing, WebSocketFrame]

          testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
            for {
              // Skip handshake complete event
              _ <- channel.receive
              _ <- channel.send(Read(WebSocketFrame.text("hello")))
              echo <- channel.receive
              _ <- echoFrame.succeed(echo.asInstanceOf[Read].frame)
              _ <- channel.send(Read(WebSocketFrame.text("close")))
              _ <- channel.shutdown
            } yield ()
          }

          _ <- TestClient.installSocketApp(closeServer)
          _ <- ZIO.serviceWithZIO[Client](_.socket(testClient))
          frame <- echoFrame.await
        } yield assertTrue(
          frame match {
            case WebSocketFrame.Text(msg) => msg == "Echo: hello"
            case _ => false
          }
        )
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

        for {
          broadcastFrame <- Promise.make[Nothing, WebSocketFrame]

          testClient: WebSocketApp[Any] = Handler.webSocket { channel =>
            for {
              // Skip handshake complete event
              _ <- channel.receive
              _ <- channel.send(Read(WebSocketFrame.text("hello")))
              broadcast <- channel.receive
              _ <- broadcastFrame.succeed(broadcast.asInstanceOf[Read].frame)
              _ <- channel.shutdown
            } yield ()
          }

          _ <- TestClient.installSocketApp(broadcastServer)
          _ <- ZIO.serviceWithZIO[Client](_.socket(testClient))
          frame <- broadcastFrame.await
        } yield assertTrue(
          frame match {
            case WebSocketFrame.Text(msg) => msg == "Broadcast: hello"
            case _ => false
          }
        )
      },
    ),
  ).provide(TestClient.layer, Scope.default)
}
