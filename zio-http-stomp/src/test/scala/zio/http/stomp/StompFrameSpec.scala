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
import zio.test.Assertion._
import zio.test._

import zio.http._

object StompFrameSpec extends ZIOSpecDefault {

  override def spec = suite("StompFrame")(
    suite("encode/decode")(
      test("encode and decode CONNECT frame") {
        val frame = StompFrame.connect(
          host = "example.com",
          login = Some("user"),
          passcode = Some("pass"),
          acceptVersion = "1.2",
        )

        val encoded = frame.encode
        val decoded = StompFrame.decode(encoded)

        assertTrue(
          decoded.isRight,
          decoded.map(_.command) == Right(StompCommand.Connect),
          decoded.map(_.header("host")) == Right(Some("example.com")),
          decoded.map(_.header("login")) == Right(Some("user")),
          decoded.map(_.header("passcode")) == Right(Some("pass")),
        )
      },
      test("encode and decode SEND frame with body") {
        val body  = "Hello, STOMP!"
        val frame = StompFrame.send(
          destination = "/queue/test",
          body = body,
          contentType = Some("text/plain"),
          additionalHeaders = Map.empty[String, String],
        )

        val encoded = frame.encode
        val decoded = StompFrame.decode(encoded)

        assertTrue(
          decoded.isRight,
          decoded.map(_.command) == Right(StompCommand.Send),
          decoded.map(_.header("destination")) == Right(Some("/queue/test")),
          decoded.map(_.header("content-type")) == Right(Some("text/plain")),
          decoded.map(f => f.body.map(b => new String(b.toArray, Charsets.Utf8))) == Right(Some(body)),
        )
      },
      test("encode and decode SUBSCRIBE frame") {
        val frame = StompFrame.subscribe(
          destination = "/topic/news",
          id = "sub-1",
          ack = "client",
        )

        val encoded = frame.encode
        val decoded = StompFrame.decode(encoded)

        assertTrue(
          decoded.isRight,
          decoded.map(_.command) == Right(StompCommand.Subscribe),
          decoded.map(_.header("destination")) == Right(Some("/topic/news")),
          decoded.map(_.header("id")) == Right(Some("sub-1")),
          decoded.map(_.header("ack")) == Right(Some("client")),
        )
      },
      test("encode and decode MESSAGE frame") {
        val body  = "News update"
        val frame = StompFrame.message(
          destination = "/topic/news",
          messageId = "msg-123",
          subscription = "sub-1",
          body = Chunk.fromArray(body.getBytes(Charsets.Utf8)),
          contentType = Some("text/plain"),
        )

        val encoded = frame.encode
        val decoded = StompFrame.decode(encoded)

        assertTrue(
          decoded.isRight,
          decoded.map(_.command) == Right(StompCommand.Message),
          decoded.map(_.header("destination")) == Right(Some("/topic/news")),
          decoded.map(_.header("message-id")) == Right(Some("msg-123")),
          decoded.map(_.header("subscription")) == Right(Some("sub-1")),
          decoded.map(f => f.body.map(b => new String(b.toArray, Charsets.Utf8))) == Right(Some(body)),
        )
      },
      test("encode and decode ERROR frame") {
        val errorMsg = "Something went wrong"
        val frame    = StompFrame.error(
          message = Some("Error occurred"),
          body = Some(errorMsg),
        )

        val encoded = frame.encode
        val decoded = StompFrame.decode(encoded)

        assertTrue(
          decoded.isRight,
          decoded.map(_.command) == Right(StompCommand.Error),
          decoded.map(_.header("message")) == Right(Some("Error occurred")),
          decoded.map(f => f.body.map(b => new String(b.toArray, Charsets.Utf8))) == Right(Some(errorMsg)),
        )
      },
      test("handle header escaping according to STOMP 1.2") {
        val frame = StompFrame(
          command = StompCommand.Send,
          headers = Map(
            "special-chars" -> "line1\nline2\rcolons:here\\backslash",
          ),
        )

        val encoded = frame.encode
        val decoded = StompFrame.decode(encoded)

        assertTrue(
          decoded.isRight,
          decoded.map(_.header("special-chars")) ==
            Right(Some("line1\nline2\rcolons:here\\backslash")),
        )
      },
      test("handle multiple line body") {
        val body  = "line1\nline2\nline3"
        val frame = StompFrame.send(
          destination = "/queue/test",
          body = body,
          contentType = None,
          additionalHeaders = Map.empty[String, String],
        )

        val encoded = frame.encode
        val decoded = StompFrame.decode(encoded)

        assertTrue(
          decoded.isRight,
          decoded.map(f => f.body.map(b => new String(b.toArray, Charsets.Utf8))) == Right(Some(body)),
        )
      },
      test("handle empty body") {
        val frame = StompFrame(
          command = StompCommand.Disconnect,
          headers = Map("receipt" -> "77"),
        )

        val encoded = frame.encode
        val decoded = StompFrame.decode(encoded)

        assertTrue(
          decoded.isRight,
          decoded.map(_.command) == Right(StompCommand.Disconnect),
          decoded.map(_.body) == Right(None),
        )
      },
      test("first header wins on duplicate keys") {
        val content = "SEND\ndestination:/queue/test\ndestination:/queue/other\n\nHello\u0000"
        val bytes   = Chunk.fromArray(content.getBytes(Charsets.Utf8))
        val decoded = StompFrame.decode(bytes)

        assertTrue(
          decoded.isRight,
          decoded.map(_.header("destination")) == Right(Some("/queue/test")),
        )
      },
      test("handle ACK frame") {
        val frame   = StompFrame.ack(id = "msg-123")
        val encoded = frame.encode
        val decoded = StompFrame.decode(encoded)

        assertTrue(
          decoded.isRight,
          decoded.map(_.command) == Right(StompCommand.Ack),
          decoded.map(_.header("id")) == Right(Some("msg-123")),
        )
      },
      test("handle NACK frame") {
        val frame   = StompFrame.nack(id = "msg-456", transaction = Some("tx-1"))
        val encoded = frame.encode
        val decoded = StompFrame.decode(encoded)

        assertTrue(
          decoded.isRight,
          decoded.map(_.command) == Right(StompCommand.Nack),
          decoded.map(_.header("id")) == Right(Some("msg-456")),
          decoded.map(_.header("transaction")) == Right(Some("tx-1")),
        )
      },
      test("handle transaction frames") {
        val beginFrame  = StompFrame.begin("tx-1")
        val commitFrame = StompFrame.commit("tx-1")
        val abortFrame  = StompFrame.abort("tx-1")

        assertTrue(
          beginFrame.command == StompCommand.Begin,
          commitFrame.command == StompCommand.Commit,
          abortFrame.command == StompCommand.Abort,
          beginFrame.header("transaction").contains("tx-1"),
        )
      },
    ),
    suite("factory methods")(
      test("CONNECT with all options") {
        val frame = StompFrame.connect(
          host = "stomp.example.com",
          login = Some("user123"),
          passcode = Some("secret"),
          acceptVersion = "1.2",
          heartBeat = Some((1000, 2000)),
        )

        assertTrue(
          frame.command == StompCommand.Connect,
          frame.header("host").contains("stomp.example.com"),
          frame.header("login").contains("user123"),
          frame.header("passcode").contains("secret"),
          frame.header("accept-version").contains("1.2"),
          frame.header("heart-beat").contains("1000,2000"),
        )
      },
      test("CONNECTED with session") {
        val frame = StompFrame.connected(
          version = "1.2",
          session = Some("session-123"),
          server = Some("ZIO-STOMP/1.0"),
          heartBeat = Some((0, 1000)),
        )

        assertTrue(
          frame.command == StompCommand.Connected,
          frame.header("version").contains("1.2"),
          frame.header("session").contains("session-123"),
          frame.header("server").contains("ZIO-STOMP/1.0"),
          frame.header("heart-beat").contains("0,1000"),
        )
      },
      test("SEND with content-length") {
        val body  = "Test message"
        val frame = StompFrame.send(
          destination = "/queue/test",
          body = Chunk.fromArray(body.getBytes(Charsets.Utf8)),
          contentType = Some("text/plain"),
          additionalHeaders = Map.empty[String, String],
        )

        assertTrue(
          frame.header("content-length").contains(body.length.toString),
        )
      },
      test("RECEIPT frame") {
        val frame = StompFrame.receipt("receipt-123")

        assertTrue(
          frame.command == StompCommand.Receipt,
          frame.header("receipt-id").contains("receipt-123"),
        )
      },
      test("UNSUBSCRIBE frame") {
        val frame = StompFrame.unsubscribe("sub-1")

        assertTrue(
          frame.command == StompCommand.Unsubscribe,
          frame.header("id").contains("sub-1"),
        )
      },
      test("DISCONNECT with receipt") {
        val frame = StompFrame.disconnect(receipt = Some("77"))

        assertTrue(
          frame.command == StompCommand.Disconnect,
          frame.header("receipt").contains("77"),
        )
      },
    ),
    suite("withHeader and withBody")(
      test("add headers to frame") {
        val frame = StompFrame(StompCommand.Send, Map("destination" -> "/queue/test"))
          .withHeader("custom", "value1")
          .withHeader("another", "value2")

        assertTrue(
          frame.header("destination").contains("/queue/test"),
          frame.header("custom").contains("value1"),
          frame.header("another").contains("value2"),
        )
      },
      test("withBody adds content-length header") {
        val body  = "Test body"
        val frame = StompFrame(StompCommand.Send, Map("destination" -> "/queue/test"))
          .withBody(body)

        assertTrue(
          frame.body.isDefined,
          frame.header("content-length").contains(body.length.toString),
        )
      },
    ),
  )
}
