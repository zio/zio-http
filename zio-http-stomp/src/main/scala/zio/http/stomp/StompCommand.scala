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
 * The command of a STOMP frame, as defined by STOMP Protocol Specification 1.2
 * https://stomp.github.io/stomp-specification-1.2.html
 *
 * The cases are the client commands, the server commands and `Heartbeat`, a
 * marker case that the specification does not list as a command. `all` lists
 * every case.
 */
sealed trait StompCommand {
  /**
   * The command text of a frame, for example `SEND`. It is a single newline for `Heartbeat`.
   */
  def name: String
  /**
   * `true` when a client can send a frame with this command to the server.
   */
  def isClientFrame: Boolean
  /**
   * `true` when a server can send a frame with this command to the client.
   */
  def isServerFrame: Boolean
}

/**
 * Defines the `StompCommand` cases, their `Schema`, the list of all cases and the lookup by command text.
 */
object StompCommand {

  /**
   * The `Schema` of `StompCommand`, derived with `DeriveSchema.gen`.
   */
  implicit val schema: Schema[StompCommand] = DeriveSchema.gen[StompCommand]

  // Client frames (sent from client to server)
  /**
   * The `CONNECT` command, which a client sends. `StompFrame.connect` builds a frame with this command.
   */
  case object Connect extends StompCommand {
    /**
     * The command text, `"CONNECT"`.
     */
    val name          = "CONNECT"
    /**
     * `true`: a client can send this command.
     */
    val isClientFrame = true
    /**
     * `false`: a server does not send this command.
     */
    val isServerFrame = false
  }

  /**
   * The `STOMP` command, which a client sends. The `StompFrame` factory methods do not build a frame with this command.
   */
  case object Stomp extends StompCommand {
    /**
     * The command text, `"STOMP"`.
     */
    val name          = "STOMP"
    /**
     * `true`: a client can send this command.
     */
    val isClientFrame = true
    /**
     * `false`: a server does not send this command.
     */
    val isServerFrame = false
  }

  /**
   * The `SEND` command, which a client sends. `StompFrame.send` builds a frame with this command.
   */
  case object Send extends StompCommand {
    /**
     * The command text, `"SEND"`.
     */
    val name          = "SEND"
    /**
     * `true`: a client can send this command.
     */
    val isClientFrame = true
    /**
     * `false`: a server does not send this command.
     */
    val isServerFrame = false
  }

  /**
   * The `SUBSCRIBE` command, which a client sends. `StompFrame.subscribe` builds a frame with this command.
   */
  case object Subscribe extends StompCommand {
    /**
     * The command text, `"SUBSCRIBE"`.
     */
    val name          = "SUBSCRIBE"
    /**
     * `true`: a client can send this command.
     */
    val isClientFrame = true
    /**
     * `false`: a server does not send this command.
     */
    val isServerFrame = false
  }

  /**
   * The `UNSUBSCRIBE` command, which a client sends. `StompFrame.unsubscribe` builds a frame with this command.
   */
  case object Unsubscribe extends StompCommand {
    /**
     * The command text, `"UNSUBSCRIBE"`.
     */
    val name          = "UNSUBSCRIBE"
    /**
     * `true`: a client can send this command.
     */
    val isClientFrame = true
    /**
     * `false`: a server does not send this command.
     */
    val isServerFrame = false
  }

  /**
   * The `ACK` command, which a client sends. `StompFrame.ack` builds a frame with this command.
   */
  case object Ack extends StompCommand {
    /**
     * The command text, `"ACK"`.
     */
    val name          = "ACK"
    /**
     * `true`: a client can send this command.
     */
    val isClientFrame = true
    /**
     * `false`: a server does not send this command.
     */
    val isServerFrame = false
  }

  /**
   * The `NACK` command, which a client sends. `StompFrame.nack` builds a frame with this command.
   */
  case object Nack extends StompCommand {
    /**
     * The command text, `"NACK"`.
     */
    val name          = "NACK"
    /**
     * `true`: a client can send this command.
     */
    val isClientFrame = true
    /**
     * `false`: a server does not send this command.
     */
    val isServerFrame = false
  }

  /**
   * The `BEGIN` command, which a client sends. `StompFrame.begin` builds a frame with this command.
   */
  case object Begin extends StompCommand {
    /**
     * The command text, `"BEGIN"`.
     */
    val name          = "BEGIN"
    /**
     * `true`: a client can send this command.
     */
    val isClientFrame = true
    /**
     * `false`: a server does not send this command.
     */
    val isServerFrame = false
  }

  /**
   * The `COMMIT` command, which a client sends. `StompFrame.commit` builds a frame with this command.
   */
  case object Commit extends StompCommand {
    /**
     * The command text, `"COMMIT"`.
     */
    val name          = "COMMIT"
    /**
     * `true`: a client can send this command.
     */
    val isClientFrame = true
    /**
     * `false`: a server does not send this command.
     */
    val isServerFrame = false
  }

  /**
   * The `ABORT` command, which a client sends. `StompFrame.abort` builds a frame with this command.
   */
  case object Abort extends StompCommand {
    /**
     * The command text, `"ABORT"`.
     */
    val name          = "ABORT"
    /**
     * `true`: a client can send this command.
     */
    val isClientFrame = true
    /**
     * `false`: a server does not send this command.
     */
    val isServerFrame = false
  }

  /**
   * The `DISCONNECT` command, which a client sends. `StompFrame.disconnect` builds a frame with this command.
   */
  case object Disconnect extends StompCommand {
    /**
     * The command text, `"DISCONNECT"`.
     */
    val name          = "DISCONNECT"
    /**
     * `true`: a client can send this command.
     */
    val isClientFrame = true
    /**
     * `false`: a server does not send this command.
     */
    val isServerFrame = false
  }

  // Server frames (sent from server to client)
  /**
   * The `CONNECTED` command, which a server sends. `StompFrame.connected` builds a frame with this command.
   */
  case object Connected extends StompCommand {
    /**
     * The command text, `"CONNECTED"`.
     */
    val name          = "CONNECTED"
    /**
     * `false`: a client does not send this command.
     */
    val isClientFrame = false
    /**
     * `true`: a server can send this command.
     */
    val isServerFrame = true
  }

  /**
   * The `MESSAGE` command, which a server sends. `StompFrame.message` builds a frame with this command.
   */
  case object Message extends StompCommand {
    /**
     * The command text, `"MESSAGE"`.
     */
    val name          = "MESSAGE"
    /**
     * `false`: a client does not send this command.
     */
    val isClientFrame = false
    /**
     * `true`: a server can send this command.
     */
    val isServerFrame = true
  }

  /**
   * The `RECEIPT` command, which a server sends. `StompFrame.receipt` builds a frame with this command.
   */
  case object Receipt extends StompCommand {
    /**
     * The command text, `"RECEIPT"`.
     */
    val name          = "RECEIPT"
    /**
     * `false`: a client does not send this command.
     */
    val isClientFrame = false
    /**
     * `true`: a server can send this command.
     */
    val isServerFrame = true
  }

  /**
   * The `ERROR` command, which a server sends. `StompFrame.error` builds a frame with this command.
   */
  case object Error extends StompCommand {
    /**
     * The command text, `"ERROR"`.
     */
    val name          = "ERROR"
    /**
     * `false`: a client does not send this command.
     */
    val isClientFrame = false
    /**
     * `true`: a server can send this command.
     */
    val isServerFrame = true
  }

  // Special frame for heartbeats (can be sent by both client and server)
  /**
   * The heartbeat marker, which both a client and a server can send. `StompFrame.heartbeat` builds a frame with this command.
   */
  case object Heartbeat extends StompCommand {
    /**
     * The command text, `"\n"`.
     */
    val name          = "\n"
    /**
     * `true`: a client can send this command.
     */
    val isClientFrame = true
    /**
     * `true`: a server can send this command.
     */
    val isServerFrame = true
  }

  /**
   * Every `StompCommand` case, in the order the client commands, the server commands and `Heartbeat` are declared.
   */
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

  /**
   * Returns the command whose text equals `s` ignoring case, or `None` when there is no such command. For example `"send"` gives `Some(Send)`, and a single newline gives `Some(Heartbeat)`.
   */
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
