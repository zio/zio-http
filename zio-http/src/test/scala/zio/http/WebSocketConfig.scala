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
// import zio.test.Assertion.equalTo
import zio.test.TestAspect._
import zio.test.{TestClock, assertCompletes, assertTrue, assertZIO, testClock}

// import zio.http.ChannelEvent.UserEvent.HandshakeComplete
import zio.http.ChannelEvent.{Read, Unregistered, UserEvent, UserEventTriggered}
import zio.http.Client
import zio.http.DnsResolver
import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}
import zio.http.Client
import zio.http.netty.NettyConfig
import zio.http.Client
import zio.http.Client
// import zio.http.netty.NettyConfig

object WebSocketConfigSpec extends HttpRunnableSpec {

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
          }.toHttpAppWS
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
  )

  def clientWithCloseFrames =
    ZLayer.succeed(
      ZClient.Config.default.webSocketConfig(
        WebSocketConfig.default
          .forwardCloseFrames(true),
      ),
    ) ++ 
    ZLayer.succeed(NettyConfig.default) ++ 
    DnsResolver.default >>> 
    Client.live

  override def spec = suite("Server") { 
    ZIO.scoped { 
      serve.as(List(webSocketConfigSpec)) 
    } 
  }
  .provideShared(
    DynamicServer.live,
    severTestLayer,
    clientWithCloseFrames,
    Scope.default,
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
