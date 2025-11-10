/*
 * Copyright 2021 - 2025 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
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
import zio.test._

object StompFrameSpec extends ZIOSpecDefault {

  def spec = suite("StompFrame")(
    suite("Connect")(
      test("creates frame with correct command") {
        val frame = StompFrame.Connect()
        assertTrue(frame.command == StompCommand.CONNECT)
      },
      test("allows adding headers") {
        val frame = StompFrame
          .Connect()
          .withHeader("accept-version", "1.2")
          .withHeader("host", "localhost")

        assertTrue(
          frame.header("accept-version").contains("1.2"),
          frame.header("host").contains("localhost"),
        )
      },
      test("allows setting body") {
        val body  = Chunk.fromArray("test".getBytes("UTF-8"))
        val frame = StompFrame.Connect().withBody(body)

        assertTrue(frame.body == body)
      },
    ),
    suite("Send")(
      test("requires destination") {
        val frame = StompFrame.Send(destination = "/queue/test")
        assertTrue(
          frame.command == StompCommand.SEND,
          frame.destination == "/queue/test",
        )
      },
      test("supports message body") {
        val body  = Chunk.fromArray("Hello, STOMP!".getBytes("UTF-8"))
        val frame = StompFrame.Send("/queue/test").withBody(body)

        assertTrue(
          frame.body == body,
          new String(frame.body.toArray, "UTF-8") == "Hello, STOMP!",
        )
      },
      test("supports custom headers") {
        val frame = StompFrame
          .Send("/queue/test")
          .withHeader("content-type", "application/json")
          .withHeader("priority", "high")

        assertTrue(
          frame.header("content-type").contains("application/json"),
          frame.header("priority").contains("high"),
        )
      },
    ),
    suite("Subscribe")(
      test("requires destination and id") {
        val frame = StompFrame.Subscribe(
          destination = "/topic/events",
          id = "sub-1",
        )

        assertTrue(
          frame.command == StompCommand.SUBSCRIBE,
          frame.destination == "/topic/events",
          frame.id == "sub-1",
        )
      },
      test("supports ack mode header") {
        val frame = StompFrame
          .Subscribe("/topic/events", "sub-1")
          .withHeader("ack", "client")

        assertTrue(frame.header("ack").contains("client"))
      },
    ),
    suite("Unsubscribe")(
      test("requires subscription id") {
        val frame = StompFrame.Unsubscribe(id = "sub-1")

        assertTrue(
          frame.command == StompCommand.UNSUBSCRIBE,
          frame.id == "sub-1",
        )
      },
    ),
    suite("Ack")(
      test("requires message id") {
        val frame = StompFrame.Ack(id = "msg-123")

        assertTrue(
          frame.command == StompCommand.ACK,
          frame.id == "msg-123",
        )
      },
    ),
    suite("Nack")(
      test("requires message id") {
        val frame = StompFrame.Nack(id = "msg-456")

        assertTrue(
          frame.command == StompCommand.NACK,
          frame.id == "msg-456",
        )
      },
    ),
    suite("Disconnect")(
      test("creates frame with correct command") {
        val frame = StompFrame.Disconnect()
        assertTrue(frame.command == StompCommand.DISCONNECT)
      },
      test("supports receipt header") {
        val frame = StompFrame
          .Disconnect()
          .withHeader("receipt", "disconnect-1")

        assertTrue(frame.header("receipt").contains("disconnect-1"))
      },
    ),
    suite("Connected")(
      test("creates server frame with correct command") {
        val frame = StompFrame.Connected()
        assertTrue(frame.command == StompCommand.CONNECTED)
      },
      test("supports version header") {
        val frame = StompFrame
          .Connected()
          .withHeader("version", "1.2")
          .withHeader("server", "ActiveMQ/5.15")

        assertTrue(
          frame.header("version").contains("1.2"),
          frame.header("server").contains("ActiveMQ/5.15"),
        )
      },
    ),
    suite("Message")(
      test("requires destination, message-id, and subscription") {
        val frame = StompFrame.Message(
          destination = "/queue/test",
          messageId = "msg-001",
          subscription = "sub-1",
        )

        assertTrue(
          frame.command == StompCommand.MESSAGE,
          frame.destination == "/queue/test",
          frame.messageId == "msg-001",
          frame.subscription == "sub-1",
        )
      },
      test("includes message body") {
        val body  = Chunk.fromArray("message content".getBytes("UTF-8"))
        val frame = StompFrame
          .Message("/queue/test", "msg-001", "sub-1")
          .withBody(body)

        assertTrue(
          frame.body == body,
          new String(frame.body.toArray, "UTF-8") == "message content",
        )
      },
    ),
    suite("Receipt")(
      test("requires receipt-id") {
        val frame = StompFrame.Receipt(receiptId = "receipt-123")

        assertTrue(
          frame.command == StompCommand.RECEIPT,
          frame.receiptId == "receipt-123",
        )
      },
    ),
    suite("Error")(
      test("requires error message") {
        val frame = StompFrame.Error(message = "Destination not found")

        assertTrue(
          frame.command == StompCommand.ERROR,
          frame.message == "Destination not found",
        )
      },
      test("includes error details in body") {
        val details = Chunk.fromArray("The destination '/queue/invalid' does not exist".getBytes("UTF-8"))
        val frame   = StompFrame.Error("Destination not found").withBody(details)

        assertTrue(
          frame.body == details,
          new String(frame.body.toArray, "UTF-8").contains("/queue/invalid"),
        )
      },
    ),
    suite("Begin")(
      test("requires transaction id") {
        val frame = StompFrame.Begin(transaction = "tx-1")

        assertTrue(
          frame.command == StompCommand.BEGIN,
          frame.transaction == "tx-1",
        )
      },
    ),
    suite("Commit")(
      test("requires transaction id") {
        val frame = StompFrame.Commit(transaction = "tx-1")

        assertTrue(
          frame.command == StompCommand.COMMIT,
          frame.transaction == "tx-1",
        )
      },
    ),
    suite("Abort")(
      test("requires transaction id") {
        val frame = StompFrame.Abort(transaction = "tx-1")

        assertTrue(
          frame.command == StompCommand.ABORT,
          frame.transaction == "tx-1",
        )
      },
    ),
    suite("StompCommand")(
      test("fromString parses valid commands") {
        assertTrue(
          StompCommand.fromString("CONNECT") == Some(StompCommand.CONNECT),
          StompCommand.fromString("STOMP") == Some(StompCommand.STOMP),
          StompCommand.fromString("SEND") == Some(StompCommand.SEND),
          StompCommand.fromString("SUBSCRIBE") == Some(StompCommand.SUBSCRIBE),
          StompCommand.fromString("BEGIN") == Some(StompCommand.BEGIN),
          StompCommand.fromString("COMMIT") == Some(StompCommand.COMMIT),
          StompCommand.fromString("ABORT") == Some(StompCommand.ABORT),
          StompCommand.fromString("MESSAGE") == Some(StompCommand.MESSAGE),
          StompCommand.fromString("ERROR") == Some(StompCommand.ERROR),
        )
      },
      test("fromString returns None for invalid commands") {
        assertTrue(
          StompCommand.fromString("INVALID") == None,
          StompCommand.fromString("") == None,
        )
      },
    ),
  )
}
