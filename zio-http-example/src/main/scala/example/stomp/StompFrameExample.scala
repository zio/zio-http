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

package example.stomp

import zio._

import zio.http._

/**
 * Example demonstrating STOMP frame creation and manipulation.
 *
 * This example shows the typed STOMP API without requiring a running broker.
 */
object StompFrameExample extends ZIOAppDefault {

  override def run: ZIO[Any, Any, Unit] = {
    for {
      _ <- Console.printLine("=== STOMP Frame Example ===\n")

      // Create a CONNECT frame
      connectFrame = StompFrame
        .Connect()
        .withHeader("accept-version", "1.2")
        .withHeader("host", "localhost")
        .withHeader("login", "guest")
        .withHeader("passcode", "guest")

      _ <- Console.printLine("1. CONNECT Frame:")
      _ <- Console.printLine(s"   Command: ${connectFrame.command.name}")
      _ <- Console.printLine(s"   Host: ${connectFrame.header("host").getOrElse("not set")}")
      _ <- Console.printLine(s"   Version: ${connectFrame.header("accept-version").getOrElse("not set")}\n")

      // Create a SEND frame with a message body
      messageBody = Chunk.fromArray("Hello, STOMP!".getBytes("UTF-8"))
      sendFrame   = StompFrame
        .Send(
          destination = "/queue/test",
        )
        .withHeader("content-type", "text/plain")
        .withBody(messageBody)

      _ <- Console.printLine("2. SEND Frame:")
      _ <- Console.printLine(s"   Command: ${sendFrame.command.name}")
      _ <- Console.printLine(s"   Destination: ${sendFrame.destination}")
      _ <- Console.printLine(s"   Content-Type: ${sendFrame.header("content-type").getOrElse("not set")}")
      _ <- Console.printLine(s"   Body: ${new String(sendFrame.body.toArray, "UTF-8")}\n")

      // Create a SUBSCRIBE frame
      subscribeFrame = StompFrame
        .Subscribe(
          destination = "/topic/events",
          id = "sub-0",
        )
        .withHeader("ack", "client")

      _ <- Console.printLine("3. SUBSCRIBE Frame:")
      _ <- Console.printLine(s"   Command: ${subscribeFrame.command.name}")
      _ <- Console.printLine(s"   Destination: ${subscribeFrame.destination}")
      _ <- Console.printLine(s"   Subscription ID: ${subscribeFrame.id}")
      _ <- Console.printLine(s"   Ack mode: ${subscribeFrame.header("ack").getOrElse("auto")}\n")

      // Create a MESSAGE frame (server response)
      responseBody = Chunk.fromArray("Event data".getBytes("UTF-8"))
      messageFrame = StompFrame
        .Message(
          destination = "/topic/events",
          messageId = "msg-001",
          subscription = "sub-0",
        )
        .withHeader("content-type", "text/plain")
        .withBody(responseBody)

      _ <- Console.printLine("4. MESSAGE Frame (from server):")
      _ <- Console.printLine(s"   Command: ${messageFrame.command.name}")
      _ <- Console.printLine(s"   Message ID: ${messageFrame.messageId}")
      _ <- Console.printLine(s"   Subscription: ${messageFrame.subscription}")
      _ <- Console.printLine(s"   Body: ${new String(messageFrame.body.toArray, "UTF-8")}\n")

      // Create an ACK frame
      ackFrame = StompFrame.Ack(id = "msg-001")

      _ <- Console.printLine("5. ACK Frame:")
      _ <- Console.printLine(s"   Command: ${ackFrame.command.name}")
      _ <- Console.printLine(s"   Message ID: ${ackFrame.id}\n")

      // Create a DISCONNECT frame
      disconnectFrame = StompFrame
        .Disconnect()
        .withHeader("receipt", "disconnect-01")

      _ <- Console.printLine("6. DISCONNECT Frame:")
      _ <- Console.printLine(s"   Command: ${disconnectFrame.command.name}")
      _ <- Console.printLine(s"   Receipt: ${disconnectFrame.header("receipt").getOrElse("none")}\n")

      // Demonstrate error frame
      errorFrame = StompFrame
        .Error(
          message = "Destination not found",
        )
        .withBody(Chunk.fromArray("The destination '/queue/invalid' does not exist".getBytes("UTF-8")))

      _ <- Console.printLine("7. ERROR Frame (from server):")
      _ <- Console.printLine(s"   Command: ${errorFrame.command.name}")
      _ <- Console.printLine(s"   Message: ${errorFrame.message}")
      _ <- Console.printLine(s"   Details: ${new String(errorFrame.body.toArray, "UTF-8")}\n")

      _ <- Console.printLine("=== Frame API Demonstration Complete ===")
      _ <- Console.printLine("\nNote: This example demonstrates the typed STOMP frame API.")
      _ <- Console.printLine("To use STOMP with a real broker, you would:")
      _ <- Console.printLine("  1. Create a StompApp with your frame handling logic")
      _ <- Console.printLine("  2. Connect to a STOMP broker (ActiveMQ, RabbitMQ, etc.)")
      _ <- Console.printLine("  3. Use StompChannel to send/receive frames")

    } yield ()
  }
}
