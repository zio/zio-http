package zio.http
import zio.http.ChannelEvent.{UserEvent, UserEventTriggered}
import zio.http.socket.{WebSocketChannel, WebSocketChannelEvent, WebSocketFrame}
import zio.{Queue, Ref, Task, Trace, UIO}

case class TestChannel(queue: Queue[WebSocketChannelEvent], responseChannel: UIO[WebSocketChannel], isOpen: Ref[Boolean]) extends WebSocketChannel {
  override def autoRead(flag: Boolean)(implicit trace: Trace): UIO[Unit] = ???

  override def awaitClose(implicit trace: Trace): UIO[Unit] = ???

  override def close(await: Boolean)(implicit trace: Trace): Task[Unit] = ???

  override def contramap[A1](f: A1 => WebSocketFrame): Channel[A1] = ???

  override def flush(implicit trace: Trace): Task[Unit] = ???

  override def id(implicit trace: Trace): String = ???

  override def isAutoRead(implicit trace: Trace): UIO[Boolean] = ???

  override def read(implicit trace: Trace): UIO[Unit] = {
    for {
      element <- queue.take
    } yield ()
  }

  def pending(implicit trace: Trace): UIO[WebSocketChannelEvent] =
    for {
      element <- queue.take
    } yield element

  override def write(msg: WebSocketFrame, await: Boolean)(implicit trace: Trace): Task[Unit] =
    for {
      responseC <- responseChannel
    _ <- queue.offer(ChannelEvent( responseC, ChannelEvent.ChannelRead(msg))).unit
  } yield  ()

  override def writeAndFlush(msg: WebSocketFrame, await: Boolean)(implicit trace: Trace): Task[Unit] =
    for {
      responseC <- responseChannel
      _ <- queue.offer(ChannelEvent( responseC, ChannelEvent.ChannelRead(msg))).unit
    } yield  ()

  val initialize = {
    val handshakeComplete = UserEventTriggered(UserEvent.HandshakeComplete)
    for {
      responseC <- responseChannel
      _ <- queue.offer(ChannelEvent(responseC, handshakeComplete))
    } yield ()
  }
}

object TestChannel {
  // TODO parameterize
  def make(replyChannel: UIO[WebSocketChannel]) =
    for {
      queue <- Queue.unbounded[WebSocketChannelEvent]
      isOpen <- Ref.make(true)
    } yield TestChannel(queue, replyChannel, isOpen)
}