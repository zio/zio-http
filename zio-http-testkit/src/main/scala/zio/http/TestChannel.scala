package zio.http
import zio.http.socket.{WebSocketChannel, WebSocketChannelEvent, WebSocketFrame}
import zio.{Queue, Task, Trace, UIO}

case class TestChannel(queue: Queue[WebSocketChannelEvent]) extends WebSocketChannel {
  override def autoRead(flag: Boolean)(implicit trace: Trace): UIO[Unit] = ???

  override def awaitClose(implicit trace: Trace): UIO[Unit] = ???

  override def close(await: Boolean)(implicit trace: Trace): Task[Unit] = ???

  override def contramap[A1](f: A1 => WebSocketFrame): Channel[A1] = ???

  override def flush(implicit trace: Trace): Task[Unit] = ???

  override def id(implicit trace: Trace): String = ???

  override def isAutoRead(implicit trace: Trace): UIO[Boolean] = ???

  override def read(implicit trace: Trace): UIO[Unit] = ???

  override def write(msg: WebSocketFrame, await: Boolean)(implicit trace: Trace): Task[Unit] = ???

  override def writeAndFlush(msg: WebSocketFrame, await: Boolean)(implicit trace: Trace): Task[Unit] = ???
}

object TestChannel {
  // TODO parameterize
  def make =
    for {
      queue <- Queue.unbounded[ChannelEvent[WebSocketChannelEvent, WebSocketChannelEvent]]
    } yield TestChannel(queue)
}