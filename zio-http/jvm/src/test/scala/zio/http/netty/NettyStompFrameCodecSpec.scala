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

package zio.http.netty

import zio._
import zio.test._
import zio.http.{StompCommand => ZStompCommand, StompFrame => ZStompFrame, _}

import io.netty.handler.codec.stomp.{
  DefaultStompFrame,
  DefaultStompHeaders,
  StompCommand,
  StompHeaders,
  StompFrame => JStompFrame,
}

object NettyStompFrameCodecSpec extends ZIOSpecDefault {

  def spec = suite("NettyStompFrameCodec")(
    suite("toNettyFrame")(
      test("converts Connect frame") {
        for {
          zioFrame   <- ZIO.succeed(ZStompFrame.Connect().withHeader("host", "localhost"))
          nettyFrame <- NettyStompFrameCodec.toNettyFrame(zioFrame)
        } yield assertTrue(
          nettyFrame.command() == StompCommand.CONNECT,
          nettyFrame.headers().getAsString("host") == "localhost",
        )
      },
      test("converts Send frame with destination") {
        for {
          body       <- ZIO.succeed(Chunk.fromArray("test message".getBytes("UTF-8")))
          zioFrame   <- ZIO.succeed(ZStompFrame.Send("/queue/test").withBody(body))
          nettyFrame <- NettyStompFrameCodec.toNettyFrame(zioFrame)
        } yield assertTrue(
          nettyFrame.command() == StompCommand.SEND,
          nettyFrame.headers().getAsString(StompHeaders.DESTINATION) == "/queue/test",
          nettyFrame.content().readableBytes() == body.length,
        )
      },
      test("converts Subscribe frame with id") {
        for {
          zioFrame   <- ZIO.succeed(ZStompFrame.Subscribe("/topic/events", "sub-1"))
          nettyFrame <- NettyStompFrameCodec.toNettyFrame(zioFrame)
        } yield assertTrue(
          nettyFrame.command() == StompCommand.SUBSCRIBE,
          nettyFrame.headers().getAsString(StompHeaders.DESTINATION) == "/topic/events",
          nettyFrame.headers().getAsString(StompHeaders.ID) == "sub-1",
        )
      },
      test("converts Message frame") {
        for {
          body       <- ZIO.succeed(Chunk.fromArray("message body".getBytes("UTF-8")))
          zioFrame   <- ZIO.succeed(
            ZStompFrame.Message("/queue/test", "msg-1", "sub-1").withBody(body),
          )
          nettyFrame <- NettyStompFrameCodec.toNettyFrame(zioFrame)
        } yield assertTrue(
          nettyFrame.command() == StompCommand.MESSAGE,
          nettyFrame.headers().getAsString(StompHeaders.DESTINATION) == "/queue/test",
          nettyFrame.headers().getAsString(StompHeaders.MESSAGE_ID) == "msg-1",
          nettyFrame.headers().getAsString(StompHeaders.SUBSCRIPTION) == "sub-1",
        )
      },
      test("converts Error frame") {
        for {
          zioFrame   <- ZIO.succeed(ZStompFrame.Error("Test error"))
          nettyFrame <- NettyStompFrameCodec.toNettyFrame(zioFrame)
        } yield assertTrue(
          nettyFrame.command() == StompCommand.ERROR,
          nettyFrame.headers().getAsString(StompHeaders.MESSAGE) == "Test error",
        )
      },
    ),
    suite("fromNettyFrame")(
      test("converts Netty Connect frame") {
        for {
          nettyFrame <- ZIO.attempt {
            val headers = new DefaultStompHeaders()
            headers.set("accept-version", "1.2")
            headers.set("host", "localhost")
            val frame   = new DefaultStompFrame(StompCommand.CONNECT)
            frame.headers().set(headers)
            frame
          }
          zioFrame   <- NettyStompFrameCodec.fromNettyFrame(nettyFrame)
        } yield assertTrue(
          zioFrame.command == ZStompCommand.CONNECT,
          zioFrame.header("accept-version").contains("1.2"),
          zioFrame.header("host").contains("localhost"),
        )
      },
      test("converts Netty Send frame") {
        for {
          body       <- ZIO.succeed("test".getBytes("UTF-8"))
          nettyFrame <- ZIO.attempt {
            val headers = new DefaultStompHeaders()
            headers.set(StompHeaders.DESTINATION, "/queue/test")
            val content = io.netty.buffer.Unpooled.wrappedBuffer(body)
            val frame   = new DefaultStompFrame(StompCommand.SEND, content)
            frame.headers().set(headers)
            frame
          }
          zioFrame   <- NettyStompFrameCodec.fromNettyFrame(nettyFrame)
        } yield assertTrue(
          zioFrame.command == ZStompCommand.SEND,
          zioFrame.isInstanceOf[ZStompFrame.Send],
          zioFrame.asInstanceOf[ZStompFrame.Send].destination == "/queue/test",
          zioFrame.body.toArray.sameElements(body),
        )
      },
      test("converts Netty Message frame") {
        for {
          nettyFrame <- ZIO.attempt {
            val headers = new DefaultStompHeaders()
            headers.set(StompHeaders.DESTINATION, "/topic/test")
            headers.set(StompHeaders.MESSAGE_ID, "msg-123")
            headers.set(StompHeaders.SUBSCRIPTION, "sub-1")
            val frame   = new DefaultStompFrame(StompCommand.MESSAGE)
            frame.headers().set(headers)
            frame
          }
          zioFrame   <- NettyStompFrameCodec.fromNettyFrame(nettyFrame)
        } yield assertTrue(
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
        for {
          original   <- ZIO.succeed(
            ZStompFrame
              .Connect()
              .withHeader("accept-version", "1.2")
              .withHeader("host", "localhost"),
          )
          nettyFrame <- NettyStompFrameCodec.toNettyFrame(original)
          converted  <- NettyStompFrameCodec.fromNettyFrame(nettyFrame)
        } yield assertTrue(
          converted.command == original.command,
          converted.header("accept-version") == original.header("accept-version"),
          converted.header("host") == original.header("host"),
        )
      },
      test("Send frame with body survives roundtrip") {
        for {
          body       <- ZIO.succeed(Chunk.fromArray("Hello, STOMP!".getBytes("UTF-8")))
          original   <- ZIO.succeed(
            ZStompFrame
              .Send("/queue/test")
              .withHeader("content-type", "text/plain")
              .withBody(body),
          )
          nettyFrame <- NettyStompFrameCodec.toNettyFrame(original)
          converted  <- NettyStompFrameCodec.fromNettyFrame(nettyFrame)
        } yield assertTrue(
          converted.command == original.command,
          converted.asInstanceOf[ZStompFrame.Send].destination == original.destination,
          converted.header("content-type") == original.header("content-type"),
          converted.body == original.body,
        )
      },
      test("Subscribe frame survives roundtrip") {
        for {
          original   <- ZIO.succeed(
            ZStompFrame
              .Subscribe("/topic/events", "sub-1")
              .withHeader("ack", "client"),
          )
          nettyFrame <- NettyStompFrameCodec.toNettyFrame(original)
          converted  <- NettyStompFrameCodec.fromNettyFrame(nettyFrame)
        } yield assertTrue(
          converted.command == original.command,
          converted.asInstanceOf[ZStompFrame.Subscribe].destination == original.destination,
          converted.asInstanceOf[ZStompFrame.Subscribe].id == original.id,
          converted.header("ack") == original.header("ack"),
        )
      },
    ),
  )
}
