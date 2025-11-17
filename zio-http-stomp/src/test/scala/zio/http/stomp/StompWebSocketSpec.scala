/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.stomp

import zio._
import zio.test._

import zio.http._ // Import syntax extensions

/**
 * Tests for STOMP protocol over WebSocket
 */
object StompWebSocketSpec extends ZIOSpecDefault {

  override def spec = suite("STOMP WebSocket App")(
    test("STOMP server handles CONNECT and responds with CONNECTED") {
      val stompApp = Handler.webSocket { channel =>
        channel.receiveAll {
          case ChannelEvent.Read(frame: WebSocketFrame.Binary) =>
            frame.asStompFrame.flatMap { stompFrame =>
              stompFrame.command match {
                case StompCommand.Connect =>
                  val connectedFrame = StompFrame.connected(
                    version = stompFrame.header("accept-version").getOrElse("1.2"),
                    session = Some("test-session-123"),
                  )
                  channel.send(ChannelEvent.Read(connectedFrame.toWebSocketFrame))

                case _ =>
                  ZIO.unit
              }
            }

          case _ =>
            ZIO.unit
        }
      }

      val routes = Routes(
        Method.GET / "stomp" -> handler(stompApp.toResponse),
      )

      for {
        port     <- Server.installRoutes(routes)
        client   <- ZIO.service[Client]
        messages <- Ref.make(List.empty[StompFrame])
        url = URL.decode(s"ws://localhost:$port/stomp").toOption.get

        _ <- client
          .url(url)
          .socket(
            Handler.webSocket { channel =>
              // Send CONNECT frame
              val connectFrame = StompFrame.connect(
                host = "localhost",
                login = Some("testuser"),
                passcode = Some("testpass"),
              )

              for {
                _ <- channel.send(ChannelEvent.Read(connectFrame.toWebSocketFrame))
                _ <- channel.receiveAll {
                  case ChannelEvent.Read(frame: WebSocketFrame.Binary) =>
                    frame.asStompFrame.flatMap { stompFrame =>
                      messages.update(_ :+ stompFrame)
                    }
                  case _                                               =>
                    ZIO.unit
                }
              } yield ()
            },
          )
          .fork

        // Wait for response
        received <- messages.get.repeatUntil(_.nonEmpty)
      } yield assertTrue(
        received.head.command == StompCommand.Connected,
        received.head.header("version").contains("1.2"),
        received.head.header("session").contains("test-session-123"),
      )
    } @@ TestAspect.withLiveClock,
    test("STOMP server handles SEND and MESSAGE flow") {
      val stompApp = Handler.webSocket { channel =>
        channel.receiveAll {
          case ChannelEvent.Read(frame: WebSocketFrame.Binary) =>
            frame.asStompFrame.flatMap { stompFrame =>
              stompFrame.command match {
                case StompCommand.Connect =>
                  val connectedFrame = StompFrame.connected("1.2", Some("session-456"))
                  channel.send(ChannelEvent.Read(connectedFrame.toWebSocketFrame))

                case StompCommand.Subscribe =>
                  // Client subscribed, send them a message
                  val messageFrame = StompFrame.message(
                    destination = stompFrame.header("destination").getOrElse("/topic/test"),
                    messageId = "msg-1",
                    subscription = stompFrame.header("id").getOrElse("sub-0"),
                    body = Chunk.fromArray("Hello from server!".getBytes(Charsets.Utf8)),
                  )
                  channel.send(ChannelEvent.Read(messageFrame.toWebSocketFrame))

                case StompCommand.Send =>
                  // Echo back as MESSAGE
                  val destination  = stompFrame.header("destination").getOrElse("/topic/echo")
                  val messageFrame = StompFrame.message(
                    destination = destination,
                    messageId = "echo-1",
                    subscription = "sub-0",
                    body = stompFrame.body.getOrElse(Chunk.empty),
                  )
                  channel.send(ChannelEvent.Read(messageFrame.toWebSocketFrame))

                case _ =>
                  ZIO.unit
              }
            }

          case _ =>
            ZIO.unit
        }
      }

      val routes = Routes(
        Method.GET / "stomp" -> handler(stompApp.toResponse),
      )

      for {
        port     <- Server.installRoutes(routes)
        client   <- ZIO.service[Client]
        messages <- Ref.make(List.empty[StompFrame])
        url = URL.decode(s"ws://localhost:$port/stomp").toOption.get

        _ <- client
          .url(url)
          .socket(
            Handler.webSocket { channel =>
              for {
                // Send CONNECT
                _ <- channel.send(
                  ChannelEvent.Read(StompFrame.connect("localhost").toWebSocketFrame),
                )
                // Wait for CONNECTED
                _ <- ZIO.sleep(50.millis)

                // Send SUBSCRIBE
                _ <- channel.send(
                  ChannelEvent.Read(
                    StompFrame.subscribe("/topic/test", "sub-0").toWebSocketFrame,
                  ),
                )
                // Wait for MESSAGE
                _ <- ZIO.sleep(50.millis)

                // Send SEND
                _ <- channel.send(
                  ChannelEvent.Read(
                    StompFrame.send("/topic/echo", "Test message", None, Map.empty[String, String]).toWebSocketFrame,
                  ),
                )

                // Receive all responses
                _ <- channel.receiveAll {
                  case ChannelEvent.Read(frame: WebSocketFrame.Binary) =>
                    frame.asStompFrame.flatMap { stompFrame =>
                      messages.update(_ :+ stompFrame)
                    }
                  case _                                               =>
                    ZIO.unit
                }
              } yield ()
            },
          )
          .fork

        received <- messages.get.repeatUntil(_.size >= 3)
      } yield {
        val connectedFrames = received.filter(_.command == StompCommand.Connected)
        val messageFrames   = received.filter(_.command == StompCommand.Message)

        assertTrue(
          connectedFrames.size == 1,
          messageFrames.nonEmpty,
          messageFrames.exists(_.header("destination").contains("/topic/test")),
        )
      }
    } @@ TestAspect.withLiveClock,
    test("STOMP client can disconnect gracefully") {
      val stompApp = Handler.webSocket { channel =>
        channel.receiveAll {
          case ChannelEvent.Read(frame: WebSocketFrame.Binary) =>
            frame.asStompFrame.flatMap { stompFrame =>
              stompFrame.command match {
                case StompCommand.Connect =>
                  val connectedFrame = StompFrame.connected("1.2", Some("session-789"))
                  channel.send(ChannelEvent.Read(connectedFrame.toWebSocketFrame))

                case StompCommand.Disconnect =>
                  // Send receipt if requested
                  stompFrame.header("receipt") match {
                    case Some(receiptId) =>
                      val receiptFrame = StompFrame.receipt(receiptId)
                      channel.send(ChannelEvent.Read(receiptFrame.toWebSocketFrame))
                    case _               => ZIO.unit
                  }

                case _ =>
                  ZIO.unit
              }
            }

          case _ =>
            ZIO.unit
        }
      }

      val routes = Routes(
        Method.GET / "stomp" -> handler(stompApp.toResponse),
      )

      for {
        port     <- Server.installRoutes(routes)
        client   <- ZIO.service[Client]
        messages <- Ref.make(List.empty[StompFrame])
        url = URL.decode(s"ws://localhost:$port/stomp").toOption.get

        _        <- client
          .url(url)
          .socket(
            Handler.webSocket { channel =>
              for {
                // Send CONNECT
                _ <- channel.send(
                  ChannelEvent.Read(StompFrame.connect("localhost").toWebSocketFrame),
                )
                _ <- ZIO.sleep(50.millis)

                // Send DISCONNECT with receipt
                _ <- channel.send(
                  ChannelEvent.Read(
                    StompFrame.disconnect(Some("disconnect-123")).toWebSocketFrame,
                  ),
                )

                // Receive responses
                _ <- channel.receiveAll {
                  case ChannelEvent.Read(frame: WebSocketFrame.Binary) =>
                    frame.asStompFrame.flatMap { stompFrame =>
                      messages.update(_ :+ stompFrame)
                    }
                  case _                                               =>
                    ZIO.unit
                }
              } yield ()
            },
          )
          .fork
        received <- messages.get.repeatUntil(_.size >= 2)
      } yield {
        val receiptFrames = received.filter(_.command == StompCommand.Receipt)

        assertTrue(
          received.exists(_.command == StompCommand.Connected),
          receiptFrames.exists(_.header("receipt-id").contains("disconnect-123")),
        )
      }
    } @@ TestAspect.withLiveClock,
  ).provide(
    Server.default,
    Client.default,
    Scope.default,
  ) @@ TestAspect.sequential
}
