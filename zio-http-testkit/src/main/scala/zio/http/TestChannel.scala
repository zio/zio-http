package zio.http
import zio.{Task, Trace, UIO}

case class TestChannel[-A]() extends Channel[A] {
  override def autoRead(flag: Boolean)(implicit trace: Trace): UIO[Unit] = ???

  override def awaitClose(implicit trace: Trace): UIO[Unit] = ???

  override def close(await: Boolean)(implicit trace: Trace): Task[Unit] = ???

  override def contramap[A1](f: A1 => A): Channel[A1] = ???

  override def flush(implicit trace: Trace): Task[Unit] = ???

  override def id(implicit trace: Trace): String = ???

  override def isAutoRead(implicit trace: Trace): UIO[Boolean] = ???

  override def read(implicit trace: Trace): UIO[Unit] = ???

  override def write(msg: A, await: Boolean)(implicit trace: Trace): Task[Unit] = ???

  override def writeAndFlush(msg: A, await: Boolean)(implicit trace: Trace): Task[Unit] = ???
}
