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

package zio.http

import zio._
import zio.test.Assertion.equalTo
import zio.test.TestAspect._
import zio.test.{TestClock, assertCompletes, assertTrue, assertZIO, testClock}

import zio.http.ChannelEvent.UserEvent.HandshakeComplete
import zio.http.ChannelEvent.{Read, Unregistered, UserEvent, UserEventTriggered}
import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}

object WebSocketSpec extends HttpRunnableSpec {

  private val websocketSpec = suite("WebsocketSpec")(
    test("channel events between client and server") {
      for {
        msg <- MessageCollector.make[WebSocketChannelEvent]
        url <- DynamicServer.wsURL
        id  <- DynamicServer.deploy {
          Handler.webSocket { channel =>
            channel.receiveAll {
              case event @ Read(frame)  => channel.send(Read(frame)) *> msg.add(event)
              case event @ Unregistered => msg.add(event, true)
              case event                => msg.add(event)
            }
          }.toHttpAppWS
        }

        res <- ZIO.scoped {
          Handler.webSocket { channel =>
            channel.receiveAll {
              case UserEventTriggered(HandshakeComplete) =>
                channel.send(Read(WebSocketFrame.text("FOO")))
              case Read(WebSocketFrame.Text("FOO"))      =>
                channel.send(Read(WebSocketFrame.text("BAR")))
              case Read(WebSocketFrame.Text("BAR"))      =>
                channel.shutdown
              case _                                     =>
                ZIO.unit
            }
          }.connect(url, Headers(DynamicServer.APP_ID, id)) *> {
            for {
              events <- msg.await
              expected = List(
                UserEventTriggered(HandshakeComplete),
                Read(WebSocketFrame.text("FOO")),
                Read(WebSocketFrame.text("BAR")),
                Unregistered,
              )
            } yield assertTrue(events == expected)
          }
        }
      } yield res
    },
    test("on close interruptibility") {
      for {

        // Maintain a flag to check if the close handler was completed
        isSet     <- Promise.make[Nothing, Unit]
        isStarted <- Promise.make[Nothing, Unit]
        clock     <- testClock

        // Setup websocket server

        serverHttp   = Handler.webSocket { channel =>
          channel.receiveAll {
            case Unregistered =>
              isStarted.succeed(()) <&> isSet.succeed(()).delay(5 seconds).withClock(clock)
            case _            =>
              ZIO.unit
          }
        }.toHttpAppWS.deployWS

        // Setup Client
        // Client closes the connection after 1 second
        clientSocket = Handler.webSocket { channel =>
          channel.receiveAll {
            case UserEventTriggered(HandshakeComplete) =>
              channel.send(Read(WebSocketFrame.close(1000))).delay(1 second).withClock(clock)
            case _                                     =>
              ZIO.unit
          }
        }

        // Deploy the server and send it a socket request
        _ <- serverHttp.runZIO(clientSocket)

        // Wait for the close handler to complete
        _ <- TestClock.adjust(2 seconds)
        _ <- isStarted.await
        _ <- TestClock.adjust(5 seconds)
        _ <- isSet.await

        // Check if the close handler was completed
      } yield assertCompletes
    } @@ nonFlaky,
    test("Multiple websocket upgrades") {
      val app   =
        Handler.webSocket(channel => channel.send(ChannelEvent.Read(WebSocketFrame.text("BAR")))).toHttpAppWS.deployWS
      val codes = ZIO
        .foreach(1 to 1024)(_ => app.runZIO(WebSocketApp.unit).map(_.status))
        .map(_.count(_ == Status.SwitchingProtocols))

      assertZIO(codes)(equalTo(1024))
    } @@ ignore,
    test("channel events between client and server when the provided URL is HTTP") {
      for {
        msg <- MessageCollector.make[WebSocketChannelEvent]
        url <- DynamicServer.httpURL
        id  <- DynamicServer.deploy {
          Handler.webSocket { channel =>
            channel.receiveAll {
              case event @ Read(frame)  => channel.send(Read(frame)) *> msg.add(event)
              case event @ Unregistered => msg.add(event, true)
              case event                => msg.add(event)
            }
          }.toHttpAppWS
        }

        res <- ZIO.scoped {
          Handler.webSocket { channel =>
            channel.receiveAll {
              case UserEventTriggered(HandshakeComplete) =>
                channel.send(Read(WebSocketFrame.text("FOO")))
              case Read(WebSocketFrame.Text("FOO"))      =>
                channel.send(Read(WebSocketFrame.text("BAR")))
              case Read(WebSocketFrame.Text("BAR"))      =>
                channel.shutdown
              case _                                     =>
                ZIO.unit
            }
          }.connect(url, Headers(DynamicServer.APP_ID, id)) *> {
            for {
              events <- msg.await
              expected = List(
                UserEventTriggered(HandshakeComplete),
                Read(WebSocketFrame.text("FOO")),
                Read(WebSocketFrame.text("BAR")),
                Unregistered,
              )
            } yield assertTrue(events == expected)
          }
        }
      } yield res
    },
    test("Client connection is interruptible") {
      for {
        url <- DynamicServer.httpURL
        id  <- DynamicServer.deploy {
          Handler.webSocket { channel =>
            ZIO.debug("receiveAll") *>
              channel.receiveAll { evt =>
                println(evt)
                evt match {
                  case ChannelEvent.UserEventTriggered(UserEvent.HandshakeComplete) =>
                    ZIO.debug("registered") *>
                      ZIO
                        .foreachDiscard(1 to 100) { idx =>
                          ZIO.debug(s"sending $idx") *>
                            channel.send(Read(WebSocketFrame.text(idx.toString))) *> ZIO.sleep(100.millis)
                        }
                        .forkScoped
                  case _                                                            => ZIO.unit
                }
              }
          }.toHttpAppWS
        }

        queue1 <- Queue.unbounded[String]
        queue2 <- Queue.unbounded[String]
        _      <- ZIO.scoped {
          Handler.webSocket { channel =>
            channel.receiveAll {
              case Read(WebSocketFrame.Text(s)) =>
                println(s"read $s")
                queue1.offer(s)
              case _                            =>
                ZIO.unit
            }.onInterrupt(ZIO.debug("ws interrupted"))
          }.connect(url, Headers(DynamicServer.APP_ID, id)) *>
            queue1.take
              .tap(s =>
                ZIO.debug(s"got $s") *>
                  queue2.offer(s),
              )
              .repeatUntil(_ == "5")
              .timeout(1.second)
              .debug
        }
        result <- queue2.takeAll
      } yield assertTrue(result == Chunk("1", "2", "3", "4", "5"))
    },
  )

  override def spec = suite("Server") {
    ZIO.scoped {
      serve.as(List(websocketSpec))
    }
  }
    .provideShared(DynamicServer.live, severTestLayer, Client.default, Scope.default) @@
    timeout(30 seconds) @@ diagnose(30.seconds) @@ withLiveClock @@ sequential

  final class MessageCollector[A](ref: Ref[List[A]], promise: Promise[Nothing, Unit]) {
    def add(a: A, isDone: Boolean = false): UIO[Unit] = ref.update(_ :+ a) <* promise.succeed(()).when(isDone)
    def await: UIO[List[A]]                           = promise.await *> ref.get
    def done: UIO[Boolean]                            = promise.succeed(())
  }

  object MessageCollector {
    def make[A]: ZIO[Any, Nothing, MessageCollector[A]] = for {
      ref <- Ref.make(List.empty[A])
      prm <- Promise.make[Nothing, Unit]
    } yield new MessageCollector(ref, prm)
  }
}
