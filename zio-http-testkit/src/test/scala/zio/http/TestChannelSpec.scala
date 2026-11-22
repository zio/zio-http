package zio.http

import java.nio.channels.ClosedChannelException

import zio._
import zio.test._

import zio.http.ChannelEvent.{Read, UserEvent, UserEventTriggered}

object TestChannelSpec extends ZIOHttpSpec {

  def spec =
    suite("TestChannel")(
      test("send delivers frame while channel is open") {
        for {
          in        <- Queue.unbounded[WebSocketChannelEvent]
          out       <- Queue.unbounded[WebSocketChannelEvent]
          promise   <- Promise.make[Nothing, Unit]
          channel   <- TestChannel.make(in, out, promise)
          handshake <- out.take
          frame = Read(WebSocketFrame.text("hello"))
          _    <- channel.send(frame)
          sent <- out.take
        } yield assertTrue(
          handshake == UserEventTriggered(UserEvent.HandshakeComplete),
          sent == frame,
        )
      },
      test("sendAll delivers frames in order while channel is open") {
        for {
          in        <- Queue.unbounded[WebSocketChannelEvent]
          out       <- Queue.unbounded[WebSocketChannelEvent]
          promise   <- Promise.make[Nothing, Unit]
          channel   <- TestChannel.make(in, out, promise)
          handshake <- out.take
          first  = Read(WebSocketFrame.text("first"))
          second = Read(WebSocketFrame.text("second"))
          _         <- channel.sendAll(List(first, second))
          sentFirst <- out.take
          sentNext  <- out.take
        } yield assertTrue(
          handshake == UserEventTriggered(UserEvent.HandshakeComplete),
          sentFirst == first,
          sentNext == second,
        )
      },
      test("send fails after shutdown") {
        for {
          in      <- Queue.unbounded[WebSocketChannelEvent]
          out     <- Queue.unbounded[WebSocketChannelEvent]
          promise <- Promise.make[Nothing, Unit]
          channel <- TestChannel.make(in, out, promise)
          _       <- channel.shutdown
          result  <- channel.send(Read(WebSocketFrame.text("after shutdown"))).either
        } yield assertTrue(
          result.isLeft,
          result.left.exists(_.isInstanceOf[ClosedChannelException]),
        )
      },
      test("sendAll fails after shutdown") {
        for {
          in      <- Queue.unbounded[WebSocketChannelEvent]
          out     <- Queue.unbounded[WebSocketChannelEvent]
          promise <- Promise.make[Nothing, Unit]
          channel <- TestChannel.make(in, out, promise)
          _       <- channel.shutdown
          result  <- channel.sendAll(Iterable(Read(WebSocketFrame.text("after shutdown")))).either
        } yield assertTrue(
          result.isLeft,
          result.left.exists(_.isInstanceOf[ClosedChannelException]),
        )
      },
    )

}
