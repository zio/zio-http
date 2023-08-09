package zio.http

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.ChannelEvent.{Unregistered, UserEvent, UserEventTriggered}

case class TestChannel(
  in: Queue[WebSocketChannelEvent],
  out: Queue[WebSocketChannelEvent],
  promise: Promise[Nothing, Unit],
) extends WebSocketChannel {
  def awaitShutdown(implicit trace: Trace): UIO[Unit]                                 =
    promise.await
  def receive(implicit trace: Trace): Task[WebSocketChannelEvent]                     =
    in.take
  def send(in: WebSocketChannelEvent)(implicit trace: Trace): Task[Unit]              =
    out.offer(in).unit
  def sendAll(in: Iterable[WebSocketChannelEvent])(implicit trace: Trace): Task[Unit] =
    out.offerAll(in).unit
  def shutdown(implicit trace: Trace): UIO[Unit]                                      =
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
