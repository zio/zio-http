package zio.http.service

import io.netty.channel.embedded.EmbeddedChannel
import zio.http.Channel
import zio.test.TestAspect.timeout
import zio.test.{TestClock, ZIOSpecDefault, assertTrue}
import zio.{UIO, ZIO, durationInt}

import java.util

object ChannelSpec extends ZIOSpecDefault {
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
    val channel: Channel[A]       = Channel.make[A](jChannel)

    def inboundMessages: util.Queue[A]  = jChannel.inboundMessages.asInstanceOf[java.util.Queue[A]]
    def outboundMessages: util.Queue[A] = jChannel.outboundMessages.asInstanceOf[java.util.Queue[A]]
  }

  object EmbeddedTestChannel {
    def make[A]: UIO[EmbeddedTestChannel[A]] = ZIO.succeed(new EmbeddedTestChannel[A])
  }
}
