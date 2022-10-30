package zio.http
import zio._
import zio.http.ChannelEvent.{UserEvent, UserEventTriggered}
import zio.http.socket.{WebSocketChannel, WebSocketFrame}

case class TestChannel(counterpartEvents: Queue[ChannelEvent.Event[WebSocketFrame]]) extends WebSocketChannel {

  // I think this really only matters for Netty. TODO Confirm.
  override def awaitClose(implicit trace: Trace): UIO[Unit] =
    close(true).orDie

  override def close(await: Boolean)(implicit trace: Trace): Task[Unit] =
    counterpartEvents.offer(ChannelEvent.ChannelUnregistered).unit

  override def contramap[A1](f: A1 => WebSocketFrame): ChannelForUserSocketApps[A1] = ???

  override def flush(implicit trace: Trace): Task[Unit] =
    // There's not queuing as would happen in a real Netty server, so this will always be a NoOp
    ZIO.unit


  // TODO Is this ID meaningful in a test?
  //    We can either:
  //    - Give it a random ID in `make`
  //    - Hardcode it to "TestChannel"
  override def id(implicit trace: Trace): String = ???

  def pending(implicit trace: Trace): UIO[ChannelEvent.Event[WebSocketFrame]] =
    counterpartEvents.take

  override def write(msg: WebSocketFrame, await: Boolean)(implicit trace: Trace): Task[Unit] =
    counterpartEvents.offer(ChannelEvent.ChannelRead(msg)).unit

  override def writeAndFlush(msg: WebSocketFrame, await: Boolean)(implicit trace: Trace): Task[Unit] =
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
