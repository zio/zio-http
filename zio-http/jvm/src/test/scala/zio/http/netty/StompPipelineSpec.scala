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

import zio.http.{StompCommand => ZStompCommand, StompFrame}

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.stomp._

/**
 * Integration tests for STOMP codec working through Netty's pipeline.
 *
 * These tests verify that frames can be encoded, sent through Netty handlers,
 * and decoded correctly - simulating the full codec lifecycle.
 */
object StompPipelineSpec extends ZIOSpecDefault {

  def spec = suite("STOMP Netty Pipeline Integration")(
    test("CONNECT frame roundtrip through pipeline") {
      val channel = new EmbeddedChannel(
        new StompSubframeDecoder(),
        new StompSubframeAggregator(65536),
        new StompSubframeEncoder(),
      )

      try {
        // Create and encode a CONNECT frame
        val originalFrame = StompFrame
          .Connect()
          .withHeader("accept-version", "1.2")
          .withHeader("host", "localhost")

        val nettyFrame = NettyStompFrameCodec.toNettyFrame(originalFrame)

        // Write to outbound (encoding direction)
        channel.writeOutbound(nettyFrame)

        // Read the encoded bytes
        val encoded = channel.readOutbound[io.netty.buffer.ByteBuf]()

        // Write back as inbound (decoding direction)
        channel.writeInbound(encoded)

        // Read the decoded frame
        val decoded = channel.readInbound[io.netty.handler.codec.stomp.StompFrame]()

        // Convert back to zio-http frame
        val convertedFrame = NettyStompFrameCodec.fromNettyFrame(decoded)

        assertTrue(
          convertedFrame.command == ZStompCommand.CONNECT,
          convertedFrame.header("accept-version").contains("1.2"),
          convertedFrame.header("host").contains("localhost"),
        )
      } finally {
        val _ = channel.finishAndReleaseAll()
        ()
      }
    },
    test("SEND frame with body roundtrip through pipeline") {
      val channel = new EmbeddedChannel(
        new StompSubframeDecoder(),
        new StompSubframeAggregator(65536),
        new StompSubframeEncoder(),
      )

      try {
        // Create SEND frame with message body
        val messageBody   = Chunk.fromArray("Hello, STOMP!".getBytes("UTF-8"))
        val originalFrame = StompFrame
          .Send("/queue/test")
          .withHeader("content-type", "text/plain")
          .withBody(messageBody)

        val nettyFrame = NettyStompFrameCodec.toNettyFrame(originalFrame)

        // Encode
        channel.writeOutbound(nettyFrame)
        val encoded = channel.readOutbound[io.netty.buffer.ByteBuf]()

        // Decode
        channel.writeInbound(encoded)
        val decoded = channel.readInbound[io.netty.handler.codec.stomp.StompFrame]()

        val convertedFrame = NettyStompFrameCodec.fromNettyFrame(decoded)

        assertTrue(
          convertedFrame.command == ZStompCommand.SEND,
          convertedFrame.asInstanceOf[StompFrame.Send].destination == "/queue/test",
          convertedFrame.header("content-type").contains("text/plain"),
          convertedFrame.body == messageBody,
          convertedFrame.header("content-length").contains(messageBody.length.toString),
        )
      } finally {
        val _ = channel.finishAndReleaseAll()
        ()
      }
    },
    test("SUBSCRIBE frame roundtrip through pipeline") {
      val channel = new EmbeddedChannel(
        new StompSubframeDecoder(),
        new StompSubframeAggregator(65536),
        new StompSubframeEncoder(),
      )

      try {
        val originalFrame = StompFrame
          .Subscribe("/topic/events", "sub-1")
          .withHeader("ack", "client")

        val nettyFrame = NettyStompFrameCodec.toNettyFrame(originalFrame)

        // Roundtrip through pipeline
        channel.writeOutbound(nettyFrame)
        val encoded = channel.readOutbound[io.netty.buffer.ByteBuf]()
        channel.writeInbound(encoded)
        val decoded = channel.readInbound[io.netty.handler.codec.stomp.StompFrame]()

        val convertedFrame = NettyStompFrameCodec.fromNettyFrame(decoded)

        assertTrue(
          convertedFrame.command == ZStompCommand.SUBSCRIBE,
          convertedFrame.asInstanceOf[StompFrame.Subscribe].destination == "/topic/events",
          convertedFrame.asInstanceOf[StompFrame.Subscribe].id == "sub-1",
          convertedFrame.header("ack").contains("client"),
        )
      } finally {
        val _ = channel.finishAndReleaseAll()
        ()
      }
    },
    test("MESSAGE frame from server roundtrip through pipeline") {
      val channel = new EmbeddedChannel(
        new StompSubframeDecoder(),
        new StompSubframeAggregator(65536),
        new StompSubframeEncoder(),
      )

      try {
        val messageBody   = Chunk.fromArray("Event data".getBytes("UTF-8"))
        val originalFrame = StompFrame
          .Message("/topic/events", "msg-001", "sub-1")
          .withBody(messageBody)

        val nettyFrame = NettyStompFrameCodec.toNettyFrame(originalFrame)

        // Roundtrip
        channel.writeOutbound(nettyFrame)
        val encoded = channel.readOutbound[io.netty.buffer.ByteBuf]()
        channel.writeInbound(encoded)
        val decoded = channel.readInbound[io.netty.handler.codec.stomp.StompFrame]()

        val convertedFrame = NettyStompFrameCodec.fromNettyFrame(decoded)

        assertTrue(
          convertedFrame.command == ZStompCommand.MESSAGE,
          convertedFrame.asInstanceOf[StompFrame.Message].destination == "/topic/events",
          convertedFrame.asInstanceOf[StompFrame.Message].messageId == "msg-001",
          convertedFrame.asInstanceOf[StompFrame.Message].subscription == "sub-1",
          convertedFrame.body == messageBody,
        )
      } finally {
        val _ = channel.finishAndReleaseAll()
        ()
      }
    },
    test("ERROR frame roundtrip through pipeline") {
      val channel = new EmbeddedChannel(
        new StompSubframeDecoder(),
        new StompSubframeAggregator(65536),
        new StompSubframeEncoder(),
      )

      try {
        val errorBody     = Chunk.fromArray("Destination not found".getBytes("UTF-8"))
        val originalFrame = StompFrame
          .Error("Invalid destination")
          .withBody(errorBody)

        val nettyFrame = NettyStompFrameCodec.toNettyFrame(originalFrame)

        // Roundtrip
        channel.writeOutbound(nettyFrame)
        val encoded = channel.readOutbound[io.netty.buffer.ByteBuf]()
        channel.writeInbound(encoded)
        val decoded = channel.readInbound[io.netty.handler.codec.stomp.StompFrame]()

        val convertedFrame = NettyStompFrameCodec.fromNettyFrame(decoded)

        assertTrue(
          convertedFrame.command == ZStompCommand.ERROR,
          convertedFrame.asInstanceOf[StompFrame.Error].message == "Invalid destination",
          convertedFrame.body == errorBody,
        )
      } finally {
        val _ = channel.finishAndReleaseAll()
        ()
      }
    },
    test("multiple frames through pipeline maintain order") {
      val channel = new EmbeddedChannel(
        new StompSubframeDecoder(),
        new StompSubframeAggregator(65536),
        new StompSubframeEncoder(),
      )

      try {
        // Create multiple frames
        val frame1 = StompFrame.Connect().withHeader("host", "localhost")
        val frame2 = StompFrame.Subscribe("/topic/test", "sub-1")
        val frame3 = StompFrame.Send("/queue/test").withBody(Chunk.fromArray("msg1".getBytes("UTF-8")))

        // Encode all frames
        val netty1 = NettyStompFrameCodec.toNettyFrame(frame1)
        val netty2 = NettyStompFrameCodec.toNettyFrame(frame2)
        val netty3 = NettyStompFrameCodec.toNettyFrame(frame3)

        // Write all to pipeline
        channel.writeOutbound(netty1)
        channel.writeOutbound(netty2)
        channel.writeOutbound(netty3)

        // Read in order
        val encoded1 = channel.readOutbound[io.netty.buffer.ByteBuf]()
        val encoded2 = channel.readOutbound[io.netty.buffer.ByteBuf]()
        val encoded3 = channel.readOutbound[io.netty.buffer.ByteBuf]()

        // Decode
        channel.writeInbound(encoded1)
        channel.writeInbound(encoded2)
        channel.writeInbound(encoded3)

        val decoded1 = channel.readInbound[io.netty.handler.codec.stomp.StompFrame]()
        val decoded2 = channel.readInbound[io.netty.handler.codec.stomp.StompFrame]()
        val decoded3 = channel.readInbound[io.netty.handler.codec.stomp.StompFrame]()

        // Verify order maintained
        val converted1 = NettyStompFrameCodec.fromNettyFrame(decoded1)
        val converted2 = NettyStompFrameCodec.fromNettyFrame(decoded2)
        val converted3 = NettyStompFrameCodec.fromNettyFrame(decoded3)

        assertTrue(
          converted1.command == ZStompCommand.CONNECT,
          converted2.command == ZStompCommand.SUBSCRIBE,
          converted3.command == ZStompCommand.SEND,
          converted3.body.length == 4,
        )
      } finally {
        val _ = channel.finishAndReleaseAll()
        ()
      }
    },
    test("empty body frames do not include content-length") {
      val channel = new EmbeddedChannel(
        new StompSubframeDecoder(),
        new StompSubframeAggregator(65536),
        new StompSubframeEncoder(),
      )

      try {
        val originalFrame = StompFrame.Disconnect()
        val nettyFrame    = NettyStompFrameCodec.toNettyFrame(originalFrame)

        channel.writeOutbound(nettyFrame)
        val encoded = channel.readOutbound[io.netty.buffer.ByteBuf]()
        channel.writeInbound(encoded)
        val decoded = channel.readInbound[io.netty.handler.codec.stomp.StompFrame]()

        // Verify content-length is not set for empty body
        assertTrue(
          decoded.headers().getAsString(StompHeaders.CONTENT_LENGTH) == null,
        )
      } finally {
        val _ = channel.finishAndReleaseAll()
        ()
      }
    },
    test("large message body survives pipeline roundtrip") {
      val channel = new EmbeddedChannel(
        new StompSubframeDecoder(),
        new StompSubframeAggregator(65536),
        new StompSubframeEncoder(),
      )

      try {
        // Create a large message (10KB)
        val largeBody     = Chunk.fromArray(Array.fill(10000)('X').mkString.getBytes("UTF-8"))
        val originalFrame = StompFrame
          .Send("/queue/large")
          .withBody(largeBody)

        val nettyFrame = NettyStompFrameCodec.toNettyFrame(originalFrame)

        channel.writeOutbound(nettyFrame)
        val encoded = channel.readOutbound[io.netty.buffer.ByteBuf]()
        channel.writeInbound(encoded)
        val decoded = channel.readInbound[io.netty.handler.codec.stomp.StompFrame]()

        val convertedFrame = NettyStompFrameCodec.fromNettyFrame(decoded)

        assertTrue(
          convertedFrame.body.length == 10000,
          convertedFrame.body == largeBody,
          convertedFrame.header("content-length").contains("10000"),
        )
      } finally {
        val _ = channel.finishAndReleaseAll()
        ()
      }
    },
  )
}
