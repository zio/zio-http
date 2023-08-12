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

import java.util

import zio.test.TestAspect.timeout
import zio.test.{TestClock, assertTrue}
import zio.{UIO, ZIO, durationInt}

import zio.http.{Channel, ZIOHttpSpec}

import io.netty.channel.embedded.EmbeddedChannel

object NettyChannelSpec extends ZIOHttpSpec {
  def spec = suite("Channel")(
    suite("writeAndFlush")(
      test("await = false") {
        for {
          tc <- EmbeddedTestChannel.make[String]
          _  <- tc.channel.writeAndFlush("ABC")
          out = tc.outboundMessages.peek()
        } yield assertTrue(out == "ABC")
      },
      test("await = true") {
        for {
          tc <- EmbeddedTestChannel.make[String]
          _  <- tc.channel.writeAndFlush("ABC", true)
          out = tc.outboundMessages.peek()
        } yield assertTrue(out == "ABC")
      },
    ),
    suite("write")(
      test("await = false") {
        for {
          tc <- EmbeddedTestChannel.make[String]
          _  <- tc.channel.write("ABC")
          out = tc.outboundMessages.peek()
        } yield assertTrue(out == null)
      },
      test("await = true") {
        for {
          tc <- EmbeddedTestChannel.make[String]
          f1 <- tc.channel.write("ABC", true).fork
          f2 <- tc.channel.flush.delay(1 second).fork
          _  <- TestClock.adjust(5 second)
          _  <- f2.join
          _  <- f1.join
          out = tc.outboundMessages.peek()
        } yield assertTrue(out == "ABC")
      },
    ),
    suite("contramap")(
      test("converts value") {
        for {
          tc <- EmbeddedTestChannel.make[Int]
          _  <- tc.channel.contramap[String](_.length).writeAndFlush("ABC")
          out = tc.outboundMessages.peek()
        } yield assertTrue(out == 3)
      },
    ),
  ) @@ timeout(5 second)

  final class EmbeddedTestChannel[A] {
    val jChannel: EmbeddedChannel = new EmbeddedChannel()
    val channel: NettyChannel[A]  = NettyChannel.make[A](jChannel)

    def inboundMessages: util.Queue[A]  = jChannel.inboundMessages.asInstanceOf[java.util.Queue[A]]
    def outboundMessages: util.Queue[A] = jChannel.outboundMessages.asInstanceOf[java.util.Queue[A]]
  }

  object EmbeddedTestChannel {
    def make[A]: UIO[EmbeddedTestChannel[A]] = ZIO.succeed(new EmbeddedTestChannel[A])
  }
}
