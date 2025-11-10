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

import zio._
import zio.test._

import zio.http.{StompCommand => ZStompCommand, StompFrame => ZStompFrame, _}

import io.netty.handler.codec.stomp.{
  DefaultStompFrame,
  DefaultStompHeaders,
  StompCommand,
  StompFrame => JStompFrame,
  StompHeaders,
}

object NettyStompFrameCodecSpec extends ZIOSpecDefault {

  def spec = suite("NettyStompFrameCodec")(
    suite("toNettyFrame")(
      test("converts Connect frame") {
        val zioFrame   = ZStompFrame.Connect().withHeader("host", "localhost")
        val nettyFrame = NettyStompFrameCodec.toNettyFrame(zioFrame)
        assertTrue(
          nettyFrame.command() == StompCommand.CONNECT,
          nettyFrame.headers().getAsString("host") == "localhost",
        )
      },
      test("converts STOMP command frame (STOMP 1.1+)") {
        val zioFrame   = ZStompFrame.Connect.stomp().withHeader("accept-version", "1.1,1.2")
        val nettyFrame = NettyStompFrameCodec.toNettyFrame(zioFrame)
        assertTrue(
          nettyFrame.command() == StompCommand.STOMP,
          nettyFrame.headers().getAsString("accept-version") == "1.1,1.2",
        )
      },
      test("converts Send frame with destination") {
        val body       = Chunk.fromArray("test message".getBytes("UTF-8"))
        val zioFrame   = ZStompFrame.Send("/queue/test").withBody(body)
        val nettyFrame = NettyStompFrameCodec.toNettyFrame(zioFrame)
        assertTrue(
          nettyFrame.command() == StompCommand.SEND,
          nettyFrame.headers().getAsString(StompHeaders.DESTINATION) == "/queue/test",
          nettyFrame.content().readableBytes() == body.length,
        )
      },
      test("converts Subscribe frame with id") {
        val zioFrame   = ZStompFrame.Subscribe("/topic/events", "sub-1")
        val nettyFrame = NettyStompFrameCodec.toNettyFrame(zioFrame)
        assertTrue(
          nettyFrame.command() == StompCommand.SUBSCRIBE,
          nettyFrame.headers().getAsString(StompHeaders.DESTINATION) == "/topic/events",
          nettyFrame.headers().getAsString(StompHeaders.ID) == "sub-1",
        )
      },
      test("converts Message frame") {
        val body       = Chunk.fromArray("message body".getBytes("UTF-8"))
        val zioFrame   = ZStompFrame.Message("/queue/test", "msg-1", "sub-1").withBody(body)
        val nettyFrame = NettyStompFrameCodec.toNettyFrame(zioFrame)
        assertTrue(
          nettyFrame.command() == StompCommand.MESSAGE,
          nettyFrame.headers().getAsString(StompHeaders.DESTINATION) == "/queue/test",
          nettyFrame.headers().getAsString(StompHeaders.MESSAGE_ID) == "msg-1",
          nettyFrame.headers().getAsString(StompHeaders.SUBSCRIPTION) == "sub-1",
        )
      },
      test("converts Error frame") {
        val zioFrame   = ZStompFrame.Error("Test error")
        val nettyFrame = NettyStompFrameCodec.toNettyFrame(zioFrame)
        assertTrue(
          nettyFrame.command() == StompCommand.ERROR,
          nettyFrame.headers().getAsString(StompHeaders.MESSAGE) == "Test error",
        )
      },
      test("filters duplicate well-known headers") {
        // User tries to override destination via withHeader (should be ignored)
        val zioFrame   = ZStompFrame
          .Send("/queue/test")
          .withHeader("destination", "/queue/wrong")
          .withHeader("custom-header", "custom-value")
        val nettyFrame = NettyStompFrameCodec.toNettyFrame(zioFrame)
        assertTrue(
          // Constructor value takes precedence
          nettyFrame.headers().getAsString(StompHeaders.DESTINATION) == "/queue/test",
          // Custom header is preserved
          nettyFrame.headers().getAsString("custom-header") == "custom-value",
          // Only one destination header exists
          nettyFrame.headers().getAll(StompHeaders.DESTINATION).size() == 1,
        )
      },
      test("adds content-length header for frames with bodies") {
        val body       = Chunk.fromArray("test message".getBytes("UTF-8"))
        val zioFrame   = ZStompFrame.Send("/queue/test").withBody(body)
        val nettyFrame = NettyStompFrameCodec.toNettyFrame(zioFrame)
        assertTrue(
          nettyFrame.headers().getAsString(StompHeaders.CONTENT_LENGTH) == body.length.toString,
        )
      },
      test("omits content-length header for frames without bodies") {
        val zioFrame   = ZStompFrame.Connect()
        val nettyFrame = NettyStompFrameCodec.toNettyFrame(zioFrame)
        assertTrue(
          nettyFrame.headers().getAsString(StompHeaders.CONTENT_LENGTH) == null,
        )
      },
    ),
    suite("fromNettyFrame")(
      test("converts Netty Connect frame") {
        val headers    = new DefaultStompHeaders()
        headers.set("accept-version", "1.2")
        headers.set("host", "localhost")
        val nettyFrame = new DefaultStompFrame(StompCommand.CONNECT)
        nettyFrame.headers().set(headers)

        val zioFrame = NettyStompFrameCodec.fromNettyFrame(nettyFrame)
        assertTrue(
          zioFrame.command == ZStompCommand.CONNECT,
          zioFrame.header("accept-version").contains("1.2"),
          zioFrame.header("host").contains("localhost"),
        )
      },
      test("converts Netty STOMP frame (STOMP 1.1+)") {
        val headers    = new DefaultStompHeaders()
        headers.set("accept-version", "1.1,1.2")
        headers.set("host", "localhost")
        val nettyFrame = new DefaultStompFrame(StompCommand.STOMP)
        nettyFrame.headers().set(headers)

        val zioFrame = NettyStompFrameCodec.fromNettyFrame(nettyFrame)
        assertTrue(
          zioFrame.command == ZStompCommand.STOMP,
          zioFrame.header("accept-version").contains("1.1,1.2"),
        )
      },
      test("converts Netty Send frame") {
        val body       = "test".getBytes("UTF-8")
        val headers    = new DefaultStompHeaders()
        headers.set(StompHeaders.DESTINATION, "/queue/test")
        val content    = io.netty.buffer.Unpooled.wrappedBuffer(body)
        val nettyFrame = new DefaultStompFrame(StompCommand.SEND, content)
        nettyFrame.headers().set(headers)

        val zioFrame = NettyStompFrameCodec.fromNettyFrame(nettyFrame)
        assertTrue(
          zioFrame.command == ZStompCommand.SEND,
          zioFrame.isInstanceOf[ZStompFrame.Send],
          zioFrame.asInstanceOf[ZStompFrame.Send].destination == "/queue/test",
          zioFrame.body.toArray.sameElements(body),
        )
      },
      test("converts Netty Message frame") {
        val headers    = new DefaultStompHeaders()
        headers.set(StompHeaders.DESTINATION, "/topic/test")
        headers.set(StompHeaders.MESSAGE_ID, "msg-123")
        headers.set(StompHeaders.SUBSCRIPTION, "sub-1")
        val nettyFrame = new DefaultStompFrame(StompCommand.MESSAGE)
        nettyFrame.headers().set(headers)

        val zioFrame = NettyStompFrameCodec.fromNettyFrame(nettyFrame)
        assertTrue(
          zioFrame.command == ZStompCommand.MESSAGE,
          zioFrame.isInstanceOf[ZStompFrame.Message],
          zioFrame.asInstanceOf[ZStompFrame.Message].destination == "/topic/test",
          zioFrame.asInstanceOf[ZStompFrame.Message].messageId == "msg-123",
          zioFrame.asInstanceOf[ZStompFrame.Message].subscription == "sub-1",
        )
      },
    ),
    suite("roundtrip")(
      test("Connect frame survives roundtrip") {
        val original   = ZStompFrame
          .Connect()
          .withHeader("accept-version", "1.2")
          .withHeader("host", "localhost")
        val nettyFrame = NettyStompFrameCodec.toNettyFrame(original)
        val converted  = NettyStompFrameCodec.fromNettyFrame(nettyFrame)
        assertTrue(
          converted.command == original.command,
          converted.header("accept-version") == original.header("accept-version"),
          converted.header("host") == original.header("host"),
        )
      },
      test("Send frame with body survives roundtrip") {
        val body       = Chunk.fromArray("Hello, STOMP!".getBytes("UTF-8"))
        val original   = ZStompFrame
          .Send("/queue/test")
          .withHeader("content-type", "text/plain")
          .withBody(body)
        val nettyFrame = NettyStompFrameCodec.toNettyFrame(original)
        val converted  = NettyStompFrameCodec.fromNettyFrame(nettyFrame)
        assertTrue(
          converted.command == original.command,
          converted.asInstanceOf[ZStompFrame.Send].destination == original.destination,
          converted.header("content-type") == original.header("content-type"),
          converted.body == original.body,
        )
      },
      test("Subscribe frame survives roundtrip") {
        val original   = ZStompFrame
          .Subscribe("/topic/events", "sub-1")
          .withHeader("ack", "client")
        val nettyFrame = NettyStompFrameCodec.toNettyFrame(original)
        val converted  = NettyStompFrameCodec.fromNettyFrame(nettyFrame)
        assertTrue(
          converted.command == original.command,
          converted.asInstanceOf[ZStompFrame.Subscribe].destination == original.destination,
          converted.asInstanceOf[ZStompFrame.Subscribe].id == original.id,
          converted.header("ack") == original.header("ack"),
        )
      },
    ),
  )
}
