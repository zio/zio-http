package zio.http
import zio.http.ChannelEvent.{UserEvent, UserEventTriggered}
import zio.http.socket.{WebSocketChannel, WebSocketChannelEvent, WebSocketFrame}
import zio.{Queue, Ref, Task, Trace, UIO}

case class TestChannel(counterpartEvents: Queue[ChannelEvent.Event[WebSocketFrame]], isOpen: Ref[Boolean]) extends WebSocketChannel {
  override def autoRead(flag: Boolean)(implicit trace: Trace): UIO[Unit] = ???

  override def awaitClose(implicit trace: Trace): UIO[Unit] = ???

  override def close(await: Boolean)(implicit trace: Trace): Task[Unit] =
    for {
      _ <- counterpartEvents.offer(ChannelEvent.ChannelUnregistered).unit
    } yield  ()



  override def contramap[A1](f: A1 => WebSocketFrame): Channel[A1] = ???

  override def flush(implicit trace: Trace): Task[Unit] = ???

  override def id(implicit trace: Trace): String = ???

  override def isAutoRead(implicit trace: Trace): UIO[Boolean] = ???

  override def read(implicit trace: Trace): UIO[Unit] = {
    for {
      element <- counterpartEvents.take
    } yield ()
  }

  def pending(implicit trace: Trace): UIO[ChannelEvent.Event[WebSocketFrame]] =
    for {
      element <- counterpartEvents.take
    } yield element

  override def write(msg: WebSocketFrame, await: Boolean)(implicit trace: Trace): Task[Unit] =
    for {
//      responseC <- responseChannel
    _ <- counterpartEvents.offer(ChannelEvent.ChannelRead(msg)).unit
  } yield  ()

  override def writeAndFlush(msg: WebSocketFrame, await: Boolean)(implicit trace: Trace): Task[Unit] =
    for {
      _ <- counterpartEvents.offer(ChannelEvent.ChannelRead(msg)).unit
    } yield  ()


  val close = {
    for {
      _ <- counterpartEvents.offer(ChannelEvent.ChannelUnregistered)
    } yield ()
  }
}

object TestChannel {
  // TODO parameterize
  def make =
    for {
      queue <- Queue.unbounded[ChannelEvent.Event[WebSocketFrame]]
      _ <- queue.offer(UserEventTriggered(UserEvent.HandshakeComplete))
      isOpen <- Ref.make(true)
    } yield TestChannel(queue, isOpen)
}