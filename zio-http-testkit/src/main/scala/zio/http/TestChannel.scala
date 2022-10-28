package zio.http
import zio.http.ChannelEvent.{UserEvent, UserEventTriggered}
import zio.http.socket.{WebSocketChannel, WebSocketFrame}
import zio.{Queue, Ref, Task, Trace, UIO, ZIO}

case class TestChannel(counterpartEvents: Queue[ChannelEvent.Event[WebSocketFrame]], isOpen: Ref[Boolean]) extends WebSocketChannel {
  override def autoRead(flag: Boolean)(implicit trace: Trace): UIO[Unit] = ???

  override def awaitClose(implicit trace: Trace): UIO[Unit] = ???

  override def close(await: Boolean)(implicit trace: Trace): Task[Unit] =
    counterpartEvents.offer(ChannelEvent.ChannelUnregistered).unit

  override def contramap[A1](f: A1 => WebSocketFrame): Channel[A1] = ???

  override def flush(implicit trace: Trace): Task[Unit] = ???

  override def id(implicit trace: Trace): String = ???

  override def isAutoRead(implicit trace: Trace): UIO[Boolean] = ???

  override def read(implicit trace: Trace): UIO[Unit] = ???

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
      _ <- queue.offer(UserEventTriggered(UserEvent.HandshakeComplete))
      isOpen <- Ref.make(true)
    } yield TestChannel(queue, isOpen)
}