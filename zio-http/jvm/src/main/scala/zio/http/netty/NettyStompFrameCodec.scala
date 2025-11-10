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

package zio.http.netty

import scala.jdk.CollectionConverters._

import zio._

import zio.http.{Header, Headers, StompCommand => ZStompCommand, StompFrame => ZStompFrame}

import io.netty.handler.codec.stomp.{
  DefaultStompFrame,
  DefaultStompHeaders,
  StompCommand,
  StompFrame => JStompFrame,
  StompHeaders,
}

/**
 * Converts between Netty STOMP frames and zio-http StompFrame ADT
 */
private[netty] object NettyStompFrameCodec {

  /**
   * Converts a Netty StompFrame to a zio-http StompFrame.
   *
   * This is a pure function (not wrapped in ZIO) to avoid blocking the Netty
   * event loop. Errors are thrown and will be caught by the caller.
   */
  def fromNettyFrame(nettyFrame: JStompFrame): ZStompFrame = {
    val command = nettyFrame.command()
    val headers = convertHeaders(nettyFrame.headers())
    val body    = {
      val content = nettyFrame.content()
      // Read bytes from ByteBuf. Memory management: The frame's ByteBuf will be
      // automatically released by StompAppHandler which uses autoRelease = true
      val bytes   = new Array[Byte](content.readableBytes())
      content.getBytes(content.readerIndex(), bytes)
      Chunk.fromArray(bytes)
    }

    command match {
      case StompCommand.CONNECT =>
        ZStompFrame.Connect(ZStompCommand.CONNECT, headers, body)

      case StompCommand.STOMP =>
        // STOMP is the STOMP 1.1+ command (preferred over CONNECT)
        ZStompFrame.Connect(ZStompCommand.STOMP, headers, body)

      case StompCommand.SEND =>
        val destination = Option(nettyFrame.headers().getAsString(StompHeaders.DESTINATION))
          .getOrElse(throw new IllegalArgumentException("SEND frame missing destination header"))
        ZStompFrame.Send(destination, headers, body)

      case StompCommand.SUBSCRIBE =>
        val destination = Option(nettyFrame.headers().getAsString(StompHeaders.DESTINATION))
          .getOrElse(throw new IllegalArgumentException("SUBSCRIBE frame missing destination header"))
        val id          = Option(nettyFrame.headers().getAsString(StompHeaders.ID))
          .getOrElse(throw new IllegalArgumentException("SUBSCRIBE frame missing id header"))
        ZStompFrame.Subscribe(destination, id, headers, body)

      case StompCommand.UNSUBSCRIBE =>
        val id = Option(nettyFrame.headers().getAsString(StompHeaders.ID))
          .getOrElse(throw new IllegalArgumentException("UNSUBSCRIBE frame missing id header"))
        ZStompFrame.Unsubscribe(id, headers, body)

      case StompCommand.ACK =>
        val id = Option(nettyFrame.headers().getAsString(StompHeaders.ID))
          .getOrElse(throw new IllegalArgumentException("ACK frame missing id header"))
        ZStompFrame.Ack(id, headers, body)

      case StompCommand.NACK =>
        val id = Option(nettyFrame.headers().getAsString(StompHeaders.ID))
          .getOrElse(throw new IllegalArgumentException("NACK frame missing id header"))
        ZStompFrame.Nack(id, headers, body)

      case StompCommand.DISCONNECT =>
        ZStompFrame.Disconnect(headers, body)

      case StompCommand.BEGIN =>
        val transaction = Option(nettyFrame.headers().getAsString(StompHeaders.TRANSACTION))
          .getOrElse(throw new IllegalArgumentException("BEGIN frame missing transaction header"))
        ZStompFrame.Begin(transaction, headers, body)

      case StompCommand.COMMIT =>
        val transaction = Option(nettyFrame.headers().getAsString(StompHeaders.TRANSACTION))
          .getOrElse(throw new IllegalArgumentException("COMMIT frame missing transaction header"))
        ZStompFrame.Commit(transaction, headers, body)

      case StompCommand.ABORT =>
        val transaction = Option(nettyFrame.headers().getAsString(StompHeaders.TRANSACTION))
          .getOrElse(throw new IllegalArgumentException("ABORT frame missing transaction header"))
        ZStompFrame.Abort(transaction, headers, body)

      case StompCommand.CONNECTED =>
        ZStompFrame.Connected(headers, body)

      case StompCommand.MESSAGE =>
        val destination  = Option(nettyFrame.headers().getAsString(StompHeaders.DESTINATION))
          .getOrElse(throw new IllegalArgumentException("MESSAGE frame missing destination header"))
        val messageId    = Option(nettyFrame.headers().getAsString(StompHeaders.MESSAGE_ID))
          .getOrElse(throw new IllegalArgumentException("MESSAGE frame missing message-id header"))
        val subscription = Option(nettyFrame.headers().getAsString(StompHeaders.SUBSCRIPTION))
          .getOrElse(throw new IllegalArgumentException("MESSAGE frame missing subscription header"))
        ZStompFrame.Message(destination, messageId, subscription, headers, body)

      case StompCommand.RECEIPT =>
        val receiptId = Option(nettyFrame.headers().getAsString(StompHeaders.RECEIPT_ID))
          .getOrElse(throw new IllegalArgumentException("RECEIPT frame missing receipt-id header"))
        ZStompFrame.Receipt(receiptId, headers, body)

      case StompCommand.ERROR =>
        val message = Option(nettyFrame.headers().getAsString(StompHeaders.MESSAGE))
          .getOrElse("Unknown error")
        ZStompFrame.Error(message, headers, body)

      case _ =>
        throw new IllegalArgumentException(s"Unknown STOMP command: $command")
    }
  }

  /**
   * Converts a zio-http StompFrame to a Netty StompFrame.
   *
   * This is a pure function to avoid blocking the Netty event loop.
   */
  def toNettyFrame(frame: ZStompFrame): DefaultStompFrame = {
    val nettyCommand = frame.command match {
      case ZStompCommand.CONNECT     => StompCommand.CONNECT
      case ZStompCommand.STOMP       => StompCommand.STOMP
      case ZStompCommand.SEND        => StompCommand.SEND
      case ZStompCommand.SUBSCRIBE   => StompCommand.SUBSCRIBE
      case ZStompCommand.UNSUBSCRIBE => StompCommand.UNSUBSCRIBE
      case ZStompCommand.ACK         => StompCommand.ACK
      case ZStompCommand.NACK        => StompCommand.NACK
      case ZStompCommand.BEGIN       => StompCommand.BEGIN
      case ZStompCommand.COMMIT      => StompCommand.COMMIT
      case ZStompCommand.ABORT       => StompCommand.ABORT
      case ZStompCommand.DISCONNECT  => StompCommand.DISCONNECT
      case ZStompCommand.CONNECTED   => StompCommand.CONNECTED
      case ZStompCommand.MESSAGE     => StompCommand.MESSAGE
      case ZStompCommand.RECEIPT     => StompCommand.RECEIPT
      case ZStompCommand.ERROR       => StompCommand.ERROR
    }

    val nettyHeaders = new DefaultStompHeaders()

    // Add required headers for specific frame types
    frame match {
      case f: ZStompFrame.Send        =>
        nettyHeaders.set(StompHeaders.DESTINATION, f.destination)
      case f: ZStompFrame.Subscribe   =>
        nettyHeaders.set(StompHeaders.DESTINATION, f.destination)
        nettyHeaders.set(StompHeaders.ID, f.id)
      case f: ZStompFrame.Unsubscribe =>
        nettyHeaders.set(StompHeaders.ID, f.id)
      case f: ZStompFrame.Ack         =>
        nettyHeaders.set(StompHeaders.ID, f.id)
      case f: ZStompFrame.Nack        =>
        nettyHeaders.set(StompHeaders.ID, f.id)
      case f: ZStompFrame.Message     =>
        nettyHeaders.set(StompHeaders.DESTINATION, f.destination)
        nettyHeaders.set(StompHeaders.MESSAGE_ID, f.messageId)
        nettyHeaders.set(StompHeaders.SUBSCRIPTION, f.subscription)
      case f: ZStompFrame.Receipt     =>
        nettyHeaders.set(StompHeaders.RECEIPT_ID, f.receiptId)
      case f: ZStompFrame.Begin       =>
        nettyHeaders.set(StompHeaders.TRANSACTION, f.transaction)
      case f: ZStompFrame.Commit      =>
        nettyHeaders.set(StompHeaders.TRANSACTION, f.transaction)
      case f: ZStompFrame.Abort       =>
        nettyHeaders.set(StompHeaders.TRANSACTION, f.transaction)
      case f: ZStompFrame.Error       =>
        nettyHeaders.set(StompHeaders.MESSAGE, f.message)
      case _                          => // No required headers
    }

    // Add custom headers, excluding well-known headers that are set via frame-specific fields
    // to prevent duplicate headers (e.g., if user calls .withHeader("destination", "..."))
    frame.headers.foreach { header =>
      val headerName  = header.headerName.toString.toLowerCase
      val isWellKnown = headerName match {
        case "destination" | "id" | "message-id" | "subscription" | "receipt-id" | "message" | "transaction" |
            "content-length" =>
          true
        case _ => false
      }
      if (!isWellKnown) {
        nettyHeaders.add(header.headerName, header.renderedValue)
      }
    }

    // Add content-length header for frames with bodies (required by STOMP spec)
    if (frame.body.nonEmpty) {
      nettyHeaders.set(StompHeaders.CONTENT_LENGTH, frame.body.length.toString)
    }

    // Create frame with body
    val content    = io.netty.buffer.Unpooled.wrappedBuffer(frame.body.toArray)
    val stompFrame = new DefaultStompFrame(nettyCommand, content)
    stompFrame.headers().set(nettyHeaders)
    stompFrame
  }

  /**
   * Converts Netty STOMP headers to zio-http Headers
   */
  private def convertHeaders(nettyHeaders: StompHeaders): Headers = {
    val headerSeq = nettyHeaders.asScala.map { entry =>
      Header.Custom(entry.getKey.toString, entry.getValue.toString)
    }.toSeq
    Headers(headerSeq: _*)
  }
}
