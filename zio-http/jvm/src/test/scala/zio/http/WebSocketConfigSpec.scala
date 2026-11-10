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
import zio.test.TestAspect._
import zio.test.assertTrue

import zio.http.ChannelEvent.{Read, UserEvent, UserEventTriggered}
import zio.http.internal.{DynamicServer, RoutesRunnableSpec, serverTestLayer}
import zio.http.netty.NettyConfig

object WebSocketConfigSpec extends RoutesRunnableSpec {

  val closeFrame = Read(WebSocketFrame.Close(1000, Some("goodbye")))

  private val webSocketConfigSpec = suite("WebSocketConfigSpec")(
    test("Close frames are received when WebSocketConfig.forwardCloseFrames is true") {
      for {
        msg <- MessageCollector.make[WebSocketChannelEvent]
        url <- DynamicServer.wsURL
        id  <- DynamicServer.deploy {
          Handler.webSocket { channel =>
            channel.receiveAll {
              case UserEventTriggered(UserEvent.HandshakeComplete) =>
                channel.send(closeFrame)
              case _                                               => ZIO.unit
            }
          }.toRoutes
        }

        res <- ZIO.scoped {
          Handler.webSocket { channel =>
            channel.receiveAll {
              case event @ Read(WebSocketFrame.Close(_, _)) =>
                msg.add(event, true)
              case _                                        => ZIO.unit
            }
          }.connect(url, Headers(DynamicServer.APP_ID, id)) *> {
            for {
              events <- msg.await
              expected = List(closeFrame)
            } yield assertTrue(events == expected)
          }
        }
      } yield res
    },
    suite("compression")(
      test("negotiates permessage-deflate when both peers enable it") {
        for {
          url      <- DynamicServer.wsURL
          id       <- DynamicServer.deploy(echo.withConfig(compressed(maxAllocation = 0)).toRoutes)
          received <- Promise.make[Nothing, String]
          response <- ZIO.scoped {
            Handler.webSocket { channel =>
              channel.receiveAll {
                case UserEventTriggered(UserEvent.HandshakeComplete) =>
                  channel.send(Read(WebSocketFrame.text(largeMessage)))
                case Read(WebSocketFrame.Text(text))                 =>
                  received.succeed(text).unit
                case _                                               => ZIO.unit
              }
            }
              .withConfig(compressed(maxAllocation = 0))
              .connect(url, Headers(DynamicServer.APP_ID, id))
              .zipLeft(received.await)
          }
          echoed   <- received.await
        } yield assertTrue(
          response.rawHeader("sec-websocket-extensions").exists(_.startsWith("permessage-deflate")),
          echoed == largeMessage,
        )
      },
      test("a server without compression declines the extension") {
        for {
          url      <- DynamicServer.wsURL
          id       <- DynamicServer.deploy(echo.toRoutes)
          received <- Promise.make[Nothing, String]
          response <- ZIO.scoped {
            Handler.webSocket { channel =>
              channel.receiveAll {
                case UserEventTriggered(UserEvent.HandshakeComplete) =>
                  channel.send(Read(WebSocketFrame.text(largeMessage)))
                case Read(WebSocketFrame.Text(text))                 =>
                  received.succeed(text).unit
                case _                                               => ZIO.unit
              }
            }
              .withConfig(compressed(maxAllocation = 0))
              .connect(url, Headers(DynamicServer.APP_ID, id))
              .zipLeft(received.await)
          }
          echoed   <- received.await
        } yield assertTrue(
          response.rawHeader("sec-websocket-extensions").isEmpty,
          echoed == largeMessage,
        )
      },
      test("closes the connection when a message inflates beyond maxAllocation") {
        for {
          url            <- DynamicServer.wsURL
          serverReceived <- Ref.make(false)
          id             <- DynamicServer.deploy {
            Handler.webSocket { channel =>
              channel.receiveAll {
                case Read(WebSocketFrame.Text(_)) => serverReceived.set(true)
                case _                            => ZIO.unit
              }
            }
              .withConfig(compressed(maxAllocation = 1024))
              .toRoutes
          }
          closed         <- Promise.make[Nothing, Unit]
          _              <- ZIO.scoped {
            Handler.webSocket { channel =>
              channel.receiveAll {
                case UserEventTriggered(UserEvent.HandshakeComplete) =>
                  channel.send(Read(WebSocketFrame.text(largeMessage)))
                case ChannelEvent.Unregistered                       =>
                  closed.succeed(()).unit
                case _                                               => ZIO.unit
              }
            }
              .withConfig(compressed(maxAllocation = 0))
              .connect(url, Headers(DynamicServer.APP_ID, id))
              .zipLeft(closed.await)
          }
          received       <- serverReceived.get
        } yield assertTrue(!received)
      },
    ),
  )

  private val largeMessage = "a" * 10000

  private def compressed(maxAllocation: Int): WebSocketConfig =
    WebSocketConfig.default.compression(WebSocketConfig.Compression(maxAllocation))

  private val echo = Handler.webSocket { channel =>
    channel.receiveAll {
      case Read(WebSocketFrame.Text(text)) => channel.send(Read(WebSocketFrame.text(text)))
      case _                               => ZIO.unit
    }
  }

  def clientWithCloseFrames =
    ZLayer.succeed(
      ZClient.Config.default.webSocketConfig(
        WebSocketConfig.default
          .forwardCloseFrames(true),
      ),
    ) ++
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown) ++
      DnsResolver.default >>>
      Client.live

  override def spec = suite("Server") {
    ZIO.scoped {
      serve.as(List(webSocketConfigSpec))
    }
  }
    .provideShared(
      DynamicServer.live,
      serverTestLayer,
      clientWithCloseFrames,
    ) @@
    timeout(30 seconds) @@
    diagnose(30.seconds) @@
    withLiveClock @@
    sequential

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
