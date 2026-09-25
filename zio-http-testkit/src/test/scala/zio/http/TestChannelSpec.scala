package zio.http

import java.nio.channels.ClosedChannelException

import zio._
import zio.test._

import zio.http.ChannelEvent.Read

object TestChannelSpec extends ZIOHttpSpec {

  def spec =
    suite("TestChannel")(
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
