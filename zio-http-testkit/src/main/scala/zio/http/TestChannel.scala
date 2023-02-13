package zio.http
import zio._
import zio.http.ChannelEvent.{UserEvent, UserEventTriggered}
import zio.http.socket.{WebSocketChannel, WebSocketFrame}

case class TestChannel(counterpartEvents: Queue[ChannelEvent.Event[WebSocketFrame]]) extends WebSocketChannel {
  override def autoRead(flag: Boolean): UIO[Unit] = ???

  override def awaitClose: UIO[Unit] =
    close(true).orDie

  override def close(await: Boolean): Task[Unit] =
    counterpartEvents.offer(ChannelEvent.ChannelUnregistered).unit

  override def contramap[A1](f: A1 => WebSocketFrame): Channel[A1] = ???

  override def flush: Task[Unit] =
    // There's not queuing as would happen in a real Netty server, so this will always be a NoOp
    ZIO.unit

  // TODO Is this ID meaningful in a test?
  //    We can either:
  //    - Give it a random ID in `make`
  //    - Hardcode it to "TestChannel"
  override def id: String = ???

  override def isAutoRead: UIO[Boolean] = ???

  override def read: UIO[Unit] = ???

  def pending: UIO[ChannelEvent.Event[WebSocketFrame]] =
    counterpartEvents.take

  override def write(msg: WebSocketFrame, await: Boolean): Task[Unit] =
    counterpartEvents.offer(ChannelEvent.ChannelRead(msg)).unit

  override def writeAndFlush(msg: WebSocketFrame, await: Boolean): Task[Unit] =
    counterpartEvents.offer(ChannelEvent.ChannelRead(msg)).unit

  val close: UIO[Boolean] =
    counterpartEvents.offer(ChannelEvent.ChannelUnregistered)
}

object TestChannel {
  def make: ZIO[Any, Nothing, TestChannel] =
    for {
      queue <- Queue.unbounded[ChannelEvent.Event[WebSocketFrame]]
      _     <- queue.offer(UserEventTriggered(UserEvent.HandshakeComplete))
    } yield TestChannel(queue)
}
