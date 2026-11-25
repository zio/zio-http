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

import zio.schema.{DeriveSchema, Schema}

/**
 * STOMP Commands as defined by STOMP Protocol Specification 1.2
 * https://stomp.github.io/stomp-specification-1.2.html
 */
sealed trait StompCommand {
  def name: String
  def isClientFrame: Boolean
  def isServerFrame: Boolean
}

object StompCommand {

  implicit val schema: Schema[StompCommand] = DeriveSchema.gen[StompCommand]

  // Client frames (sent from client to server)
  case object Connect extends StompCommand {
    val name          = "CONNECT"
    val isClientFrame = true
    val isServerFrame = false
  }

  case object Stomp extends StompCommand {
    val name          = "STOMP"
    val isClientFrame = true
    val isServerFrame = false
  }

  case object Send extends StompCommand {
    val name          = "SEND"
    val isClientFrame = true
    val isServerFrame = false
  }

  case object Subscribe extends StompCommand {
    val name          = "SUBSCRIBE"
    val isClientFrame = true
    val isServerFrame = false
  }

  case object Unsubscribe extends StompCommand {
    val name          = "UNSUBSCRIBE"
    val isClientFrame = true
    val isServerFrame = false
  }

  case object Ack extends StompCommand {
    val name          = "ACK"
    val isClientFrame = true
    val isServerFrame = false
  }

  case object Nack extends StompCommand {
    val name          = "NACK"
    val isClientFrame = true
    val isServerFrame = false
  }

  case object Begin extends StompCommand {
    val name          = "BEGIN"
    val isClientFrame = true
    val isServerFrame = false
  }

  case object Commit extends StompCommand {
    val name          = "COMMIT"
    val isClientFrame = true
    val isServerFrame = false
  }

  case object Abort extends StompCommand {
    val name          = "ABORT"
    val isClientFrame = true
    val isServerFrame = false
  }

  case object Disconnect extends StompCommand {
    val name          = "DISCONNECT"
    val isClientFrame = true
    val isServerFrame = false
  }

  // Server frames (sent from server to client)
  case object Connected extends StompCommand {
    val name          = "CONNECTED"
    val isClientFrame = false
    val isServerFrame = true
  }

  case object Message extends StompCommand {
    val name          = "MESSAGE"
    val isClientFrame = false
    val isServerFrame = true
  }

  case object Receipt extends StompCommand {
    val name          = "RECEIPT"
    val isClientFrame = false
    val isServerFrame = true
  }

  case object Error extends StompCommand {
    val name          = "ERROR"
    val isClientFrame = false
    val isServerFrame = true
  }

  // Special frame for heartbeats (can be sent by both client and server)
  case object Heartbeat extends StompCommand {
    val name          = "\n"
    val isClientFrame = true
    val isServerFrame = true
  }

  val all: List[StompCommand] = List(
    Connect,
    Stomp,
    Send,
    Subscribe,
    Unsubscribe,
    Ack,
    Nack,
    Begin,
    Commit,
    Abort,
    Disconnect,
    Connected,
    Message,
    Receipt,
    Error,
    Heartbeat,
  )

  def fromString(s: String): Option[StompCommand] = {
    s.toUpperCase match {
      case "CONNECT"     => Some(Connect)
      case "STOMP"       => Some(Stomp)
      case "SEND"        => Some(Send)
      case "SUBSCRIBE"   => Some(Subscribe)
      case "UNSUBSCRIBE" => Some(Unsubscribe)
      case "ACK"         => Some(Ack)
      case "NACK"        => Some(Nack)
      case "BEGIN"       => Some(Begin)
      case "COMMIT"      => Some(Commit)
      case "ABORT"       => Some(Abort)
      case "DISCONNECT"  => Some(Disconnect)
      case "CONNECTED"   => Some(Connected)
      case "MESSAGE"     => Some(Message)
      case "RECEIPT"     => Some(Receipt)
      case "ERROR"       => Some(Error)
      case "\n"          => Some(Heartbeat)
      case _             => None
    }
  }
}
