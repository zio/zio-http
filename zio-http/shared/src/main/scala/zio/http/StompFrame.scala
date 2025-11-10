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

import zio.Chunk

/**
 * Represents a STOMP protocol frame.
 *
 * STOMP (Simple Text Oriented Messaging Protocol) is a frame-based protocol for
 * asynchronous message passing between clients via mediating servers.
 */
sealed trait StompFrame {
  def command: StompCommand
  def headers: Headers
  def body: Chunk[Byte]

  def header(name: CharSequence): Option[String] =
    headers.get(name)

  def withHeader(name: CharSequence, value: CharSequence): StompFrame
  def withBody(body: Chunk[Byte]): StompFrame
}

object StompFrame {

  /** Client frame: Initiates connection */
  final case class Connect(
    headers: Headers = Headers.empty,
    body: Chunk[Byte] = Chunk.empty,
  ) extends StompFrame {
    val command: StompCommand = StompCommand.CONNECT

    def withHeader(name: CharSequence, value: CharSequence): Connect =
      copy(headers = headers ++ Headers(name, value))

    def withBody(body: Chunk[Byte]): Connect =
      copy(body = body)
  }

  /** Client frame: Sends a message to a destination */
  final case class Send(
    destination: String,
    headers: Headers = Headers.empty,
    body: Chunk[Byte] = Chunk.empty,
  ) extends StompFrame {
    val command: StompCommand = StompCommand.SEND

    def withHeader(name: CharSequence, value: CharSequence): Send =
      copy(headers = headers ++ Headers(name, value))

    def withBody(body: Chunk[Byte]): Send =
      copy(body = body)
  }

  /** Client frame: Subscribes to a destination */
  final case class Subscribe(
    destination: String,
    id: String,
    headers: Headers = Headers.empty,
    body: Chunk[Byte] = Chunk.empty,
  ) extends StompFrame {
    val command: StompCommand = StompCommand.SUBSCRIBE

    def withHeader(name: CharSequence, value: CharSequence): Subscribe =
      copy(headers = headers ++ Headers(name, value))

    def withBody(body: Chunk[Byte]): Subscribe =
      copy(body = body)
  }

  /** Client frame: Unsubscribes from a destination */
  final case class Unsubscribe(
    id: String,
    headers: Headers = Headers.empty,
    body: Chunk[Byte] = Chunk.empty,
  ) extends StompFrame {
    val command: StompCommand = StompCommand.UNSUBSCRIBE

    def withHeader(name: CharSequence, value: CharSequence): Unsubscribe =
      copy(headers = headers ++ Headers(name, value))

    def withBody(body: Chunk[Byte]): Unsubscribe =
      copy(body = body)
  }

  /** Client frame: Acknowledges message consumption */
  final case class Ack(
    id: String,
    headers: Headers = Headers.empty,
    body: Chunk[Byte] = Chunk.empty,
  ) extends StompFrame {
    val command: StompCommand = StompCommand.ACK

    def withHeader(name: CharSequence, value: CharSequence): Ack =
      copy(headers = headers ++ Headers(name, value))

    def withBody(body: Chunk[Byte]): Ack =
      copy(body = body)
  }

  /** Client frame: Rejects message consumption */
  final case class Nack(
    id: String,
    headers: Headers = Headers.empty,
    body: Chunk[Byte] = Chunk.empty,
  ) extends StompFrame {
    val command: StompCommand = StompCommand.NACK

    def withHeader(name: CharSequence, value: CharSequence): Nack =
      copy(headers = headers ++ Headers(name, value))

    def withBody(body: Chunk[Byte]): Nack =
      copy(body = body)
  }

  /** Client frame: Gracefully disconnects */
  final case class Disconnect(
    headers: Headers = Headers.empty,
    body: Chunk[Byte] = Chunk.empty,
  ) extends StompFrame {
    val command: StompCommand = StompCommand.DISCONNECT

    def withHeader(name: CharSequence, value: CharSequence): Disconnect =
      copy(headers = headers ++ Headers(name, value))

    def withBody(body: Chunk[Byte]): Disconnect =
      copy(body = body)
  }

  /** Server frame: Confirms successful connection */
  final case class Connected(
    headers: Headers = Headers.empty,
    body: Chunk[Byte] = Chunk.empty,
  ) extends StompFrame {
    val command: StompCommand = StompCommand.CONNECTED

    def withHeader(name: CharSequence, value: CharSequence): Connected =
      copy(headers = headers ++ Headers(name, value))

    def withBody(body: Chunk[Byte]): Connected =
      copy(body = body)
  }

  /** Server frame: Delivers subscribed messages */
  final case class Message(
    destination: String,
    messageId: String,
    subscription: String,
    headers: Headers = Headers.empty,
    body: Chunk[Byte] = Chunk.empty,
  ) extends StompFrame {
    val command: StompCommand = StompCommand.MESSAGE

    def withHeader(name: CharSequence, value: CharSequence): Message =
      copy(headers = headers ++ Headers(name, value))

    def withBody(body: Chunk[Byte]): Message =
      copy(body = body)
  }

  /** Server frame: Acknowledges client frame processing */
  final case class Receipt(
    receiptId: String,
    headers: Headers = Headers.empty,
    body: Chunk[Byte] = Chunk.empty,
  ) extends StompFrame {
    val command: StompCommand = StompCommand.RECEIPT

    def withHeader(name: CharSequence, value: CharSequence): Receipt =
      copy(headers = headers ++ Headers(name, value))

    def withBody(body: Chunk[Byte]): Receipt =
      copy(body = body)
  }

  /** Server frame: Reports errors */
  final case class Error(
    message: String,
    headers: Headers = Headers.empty,
    body: Chunk[Byte] = Chunk.empty,
  ) extends StompFrame {
    val command: StompCommand = StompCommand.ERROR

    def withHeader(name: CharSequence, value: CharSequence): Error =
      copy(headers = headers ++ Headers(name, value))

    def withBody(body: Chunk[Byte]): Error =
      copy(body = body)
  }
}

/**
 * STOMP protocol commands
 */
sealed trait StompCommand {
  def name: String
}

object StompCommand {
  // Client commands
  case object CONNECT     extends StompCommand { val name = "CONNECT"     }
  case object SEND        extends StompCommand { val name = "SEND"        }
  case object SUBSCRIBE   extends StompCommand { val name = "SUBSCRIBE"   }
  case object UNSUBSCRIBE extends StompCommand { val name = "UNSUBSCRIBE" }
  case object ACK         extends StompCommand { val name = "ACK"         }
  case object NACK        extends StompCommand { val name = "NACK"        }
  case object DISCONNECT  extends StompCommand { val name = "DISCONNECT"  }

  // Server commands
  case object CONNECTED extends StompCommand { val name = "CONNECTED" }
  case object MESSAGE   extends StompCommand { val name = "MESSAGE"   }
  case object RECEIPT   extends StompCommand { val name = "RECEIPT"   }
  case object ERROR     extends StompCommand { val name = "ERROR"     }

  def fromString(str: String): Option[StompCommand] = str match {
    case "CONNECT"     => Some(CONNECT)
    case "SEND"        => Some(SEND)
    case "SUBSCRIBE"   => Some(SUBSCRIBE)
    case "UNSUBSCRIBE" => Some(UNSUBSCRIBE)
    case "ACK"         => Some(ACK)
    case "NACK"        => Some(NACK)
    case "DISCONNECT"  => Some(DISCONNECT)
    case "CONNECTED"   => Some(CONNECTED)
    case "MESSAGE"     => Some(MESSAGE)
    case "RECEIPT"     => Some(RECEIPT)
    case "ERROR"       => Some(ERROR)
    case _             => None
  }
}
