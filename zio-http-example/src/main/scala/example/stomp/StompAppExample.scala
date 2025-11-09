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

package example.stomp

import zio._
import zio.http._
import zio.http.ChannelEvent.Read

/**
 * Example demonstrating StompApp usage pattern.
 *
 * This shows the conceptual API for handling STOMP frames in a type-safe way.
 * In a complete implementation, this would connect to a real STOMP broker.
 */
object StompAppExample extends ZIOAppDefault {

  /**
   * Example STOMP application that handles different frame types
   */
  val stompApp: StompApp[Any] = StompApp {
    Handler.fromFunctionZIO[StompChannel] { channel =>
      Console.printLine("STOMP connection established") *>
        // Send CONNECT frame
        channel.send(
          StompFrame
            .Connect()
            .withHeader("accept-version", "1.2")
            .withHeader("host", "localhost"),
        ) *>
        Console.printLine("Sent CONNECT frame") *>
        // Handle incoming frames (never returns)
        channel.receiveAll {
          case Read(StompFrame.Connected(headers, _)) =>
            Console.printLine(s"Connected! Server version: ${headers.get("version").getOrElse("unknown")}") *>
              // Subscribe to a topic after connection
              channel.send(
                StompFrame.Subscribe(
                  destination = "/topic/updates",
                  id = "sub-1",
                ),
              )

          case Read(StompFrame.Message(destination, messageId, subscription, _, body)) =>
            val content = new String(body.toArray, "UTF-8")
            Console.printLine(s"Received message on $destination: $content") *>
              // Acknowledge the message
              channel.send(StompFrame.Ack(id = messageId))

          case Read(StompFrame.Error(message, _, body)) =>
            val details = new String(body.toArray, "UTF-8")
            Console.printLine(s"Error from server: $message - $details") *>
              ZIO.fail(new RuntimeException(message))

          case Read(StompFrame.Receipt(receiptId, _, _)) =>
            Console.printLine(s"Received receipt: $receiptId")

          case ChannelEvent.ExceptionCaught(err) =>
            Console.printLine(s"Channel error: ${err.getMessage}") *>
              ZIO.fail(err)

          case ChannelEvent.Unregistered =>
            Console.printLine("Channel closed") *>
              ZIO.fail(new RuntimeException("Connection closed"))

          case _ =>
            ZIO.unit
        }
    }
  }

  /**
   * Example showing how to compose STOMP apps with ZIO environment
   */
  trait MessageLogger {
    def log(message: String): UIO[Unit]
  }

  object MessageLogger {
    val live: ZLayer[Any, Nothing, MessageLogger] = ZLayer.succeed(
      new MessageLogger {
        def log(message: String): UIO[Unit] =
          Console.printLine(s"[LOG] $message").orDie
      },
    )
  }

  val stompAppWithLogging: StompApp[MessageLogger] = StompApp {
    Handler.fromFunctionZIO[StompChannel] { channel =>
      ZIO.service[MessageLogger].flatMap { logger =>
        logger.log("Starting STOMP session") *>
          channel.receiveAll {
            case Read(frame) =>
              logger.log(s"Received frame: ${frame.command.name}")

            case _ =>
              ZIO.unit
          }
      }
    }
  }

  override def run: ZIO[Any, Any, Unit] = {
    for {
      _ <- Console.printLine("=== STOMP App Example ===\n")
      _ <- Console.printLine("This example demonstrates the StompApp API pattern.")
      _ <- Console.printLine("\nThe StompApp provides:")
      _ <- Console.printLine("  - Type-safe frame handling")
      _ <- Console.printLine("  - Pattern matching on frame types")
      _ <- Console.printLine("  - ZIO environment composition")
      _ <- Console.printLine("  - Automatic error handling")
      _ <- Console.printLine("\nTo connect to a real broker:")
      _ <- Console.printLine("  1. Start a STOMP broker (e.g., docker run -p 61613:61613 rmohr/activemq)")
      _ <- Console.printLine("  2. Implement connection setup with Netty pipeline")
      _ <- Console.printLine("  3. Use StompChannel for bidirectional communication")
      _ <- Console.printLine("\nThe typed API ensures compile-time safety:")

      // Demonstrate type safety
      connectFrame = StompFrame.Connect()
      _ <- Console.printLine(s"\n  - Connect frame type: ${connectFrame.getClass.getSimpleName}")
      _ <- Console.printLine(s"  - Command: ${connectFrame.command.name}")
      _ <- Console.printLine(s"  - Headers are type-safe and validated")

    } yield ()
  }
}
