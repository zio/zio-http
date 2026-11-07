package zio.http

import java.nio.channels.ClosedChannelException

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.ChannelEvent._

case class TestChannel(
  in: Queue[WebSocketChannelEvent],
  out: Queue[WebSocketChannelEvent],
  promise: Promise[Nothing, Unit],
) extends WebSocketChannel {
  def awaitShutdown(implicit trace: Trace): UIO[Unit]                                 =
    promise.await
  def receive(implicit trace: Trace): Task[WebSocketChannelEvent]                     =
    in.take
  def receiveAll[Env, Err](f: WebSocketChannelEvent => ZIO[Env, Err, Any])(implicit
    trace: Trace,
  ): ZIO[Env, Err, Unit] = {
    lazy val loop: ZIO[Env, Err, Unit] =
      in.take.flatMap {
        case event: ChannelEvent.ExceptionCaught   => f(event).unit
        case event: ChannelEvent.Unregistered.type => f(event).unit
        case event                                 => f(event) *> ZIO.yieldNow *> loop
      }

    loop
  }
  def send(in: WebSocketChannelEvent)(implicit trace: Trace): Task[Unit]              =
    whenOpen(out.offer(in).unit)
  def sendAll(in: Iterable[WebSocketChannelEvent])(implicit trace: Trace): Task[Unit] =
    whenOpen(out.offerAll(in).unit)

  private def whenOpen[A](zio: => Task[A])(implicit trace: Trace): Task[A] =
    promise.isDone.flatMap {
      case true  => ZIO.fail(new ClosedChannelException)
      case false => zio
    }
  def shutdown(implicit trace: Trace): UIO[Unit]                           =
    in.offer(ChannelEvent.Unregistered) *>
      out.offer(ChannelEvent.Unregistered) *>
      promise.succeed(()).unit
}

object TestChannel {
  def make(
    in: Queue[WebSocketChannelEvent],
    out: Queue[WebSocketChannelEvent],
    promise: Promise[Nothing, Unit],
  )(implicit trace: Trace): ZIO[Any, Nothing, TestChannel] =
    for {
      _ <- out.offer(UserEventTriggered(UserEvent.HandshakeComplete))
    } yield TestChannel(in, out, promise)
}
