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

import zio.stream._

import zio.http._
import zio.http.stomp._ // Import syntax extensions

object StompIntegrationSpec extends ZIOSpecDefault {

  override def spec = suite("STOMP WebSocket Integration")(
    suite("WebSocketFrame conversion")(
      test("StompFrame to WebSocketFrame") {
        val stompFrame = StompFrame.connect(
          host = "localhost",
          login = Some("user"),
          passcode = Some("pass"),
        )

        val wsFrame = stompFrame.toWebSocketFrame

        wsFrame match {
          case WebSocketFrame.Binary(bytes) =>
            StompFrame.decode(bytes) match {
              case Right(decoded) =>
                assertTrue(
                  decoded.command == StompCommand.Connect,
                  decoded.header("host").contains("localhost"),
                  decoded.header("login").contains("user"),
                )
              case Left(_)        =>
                assertTrue(false)
            }
          case _                            =>
            assertTrue(false)
        }
      },
      test("WebSocketFrame to StompFrame") {
        val stompFrame = StompFrame.send("/queue/test", "Hello", None, Map.empty[String, String])
        val wsFrame    = WebSocketFrame.Binary(stompFrame.encode)

        for {
          decoded <- wsFrame.asStompFrame
        } yield assertTrue(
          decoded.command == StompCommand.Send,
          decoded.header("destination").contains("/queue/test"),
        )
      },
      test("non-binary WebSocketFrame fails to decode") {
        val wsFrame = WebSocketFrame.Text("not a stomp frame")

        for {
          result <- wsFrame.asStompFrame.either
        } yield assertTrue(
          result.isLeft,
        )
      },
    ),
    suite("Codec")(
      test("stream encoder produces valid frames") {
        val frames = ZStream.fromIterable(
          Seq(
            StompFrame.send("/queue/1", "msg1", None, Map.empty[String, String]),
            StompFrame.send("/queue/2", "msg2", None, Map.empty[String, String]),
          ),
        )

        for {
          encoded <- (frames >>> StompCodec.binaryCodec.streamEncoder).runCollect
          decoded <- (ZStream.fromChunk(encoded) >>> StompCodec.binaryCodec.streamDecoder).runCollect
        } yield assertTrue(
          decoded.size == 2,
          decoded(0).header("destination").contains("/queue/1"),
          decoded(1).header("destination").contains("/queue/2"),
        )
      },
      test("stream decoder handles partial frames") {
        val frame1 = StompFrame.send("/queue/test", "Hello", None, Map.empty[String, String])
        val frame2 = StompFrame.connect("localhost")

        val bytes = frame1.encode ++ frame2.encode

        // Split bytes in the middle to simulate partial frame
        val part1 = bytes.take(bytes.size / 2)
        val part2 = bytes.drop(bytes.size / 2)

        val stream = ZStream.fromChunk(part1) ++ ZStream.fromChunk(part2)

        for {
          decoded <- (stream >>> StompCodec.binaryCodec.streamDecoder).runCollect
        } yield assertTrue(
          decoded.size == 2,
          decoded(0).command == StompCommand.Send,
          decoded(1).command == StompCommand.Connect,
        )
      },
    ),
    suite("STOMP Protocol Scenarios")(
      test("client connection handshake") {
        val connectFrame = StompFrame.connect(
          host = "stomp.example.com",
          login = Some("user"),
          passcode = Some("pass"),
          acceptVersion = "1.2",
          heartBeat = Some((1000, 1000)),
        )

        val connectedFrame = StompFrame.connected(
          version = "1.2",
          session = Some("session-abc123"),
          heartBeat = Some((1000, 1000)),
        )

        assertTrue(
          connectFrame.command == StompCommand.Connect,
          connectFrame.header("accept-version").contains("1.2"),
          connectedFrame.command == StompCommand.Connected,
          connectedFrame.header("version").contains("1.2"),
        )
      },
      test("subscribe and receive message") {
        val subscribeFrame = StompFrame.subscribe(
          destination = "/topic/news",
          id = "sub-0",
          ack = "client",
        )

        val messageFrame = StompFrame.message(
          destination = "/topic/news",
          messageId = "msg-1",
          subscription = "sub-0",
          body = Chunk.fromArray("Breaking news!".getBytes(Charsets.Utf8)),
          contentType = Some("text/plain"),
        )

        val ackFrame = StompFrame.ack(id = "msg-1")

        assertTrue(
          subscribeFrame.header("destination").contains("/topic/news"),
          subscribeFrame.header("ack").contains("client"),
          messageFrame.header("message-id").contains("msg-1"),
          messageFrame.header("subscription").contains("sub-0"),
          ackFrame.header("id").contains("msg-1"),
        )
      },
      test("transaction workflow") {
        val beginFrame  = StompFrame.begin("tx-1")
        val sendFrame1  = StompFrame.send(
          "/queue/a",
          "msg1",
          None,
          Map("transaction" -> "tx-1"),
        )
        val sendFrame2  = StompFrame.send(
          "/queue/b",
          "msg2",
          None,
          Map("transaction" -> "tx-1"),
        )
        val commitFrame = StompFrame.commit("tx-1")

        assertTrue(
          beginFrame.command == StompCommand.Begin,
          sendFrame1.header("transaction").contains("tx-1"),
          sendFrame2.header("transaction").contains("tx-1"),
          commitFrame.command == StompCommand.Commit,
        )
      },
      test("error handling") {
        val errorFrame = StompFrame.error(
          message = Some("Invalid destination"),
          body = Some("The destination '/invalid' does not exist"),
          receiptId = Some("receipt-123"),
        )

        assertTrue(
          errorFrame.command == StompCommand.Error,
          errorFrame.header("message").contains("Invalid destination"),
          errorFrame.header("receipt-id").contains("receipt-123"),
          errorFrame.body.map(_.asString(Charsets.Utf8)).contains("The destination '/invalid' does not exist"),
        )
      },
      test("graceful disconnect with receipt") {
        val disconnectFrame = StompFrame.disconnect(receipt = Some("77"))
        val receiptFrame    = StompFrame.receipt("77")

        assertTrue(
          disconnectFrame.command == StompCommand.Disconnect,
          disconnectFrame.header("receipt").contains("77"),
          receiptFrame.command == StompCommand.Receipt,
          receiptFrame.header("receipt-id").contains("77"),
        )
      },
    ),
  )
}
