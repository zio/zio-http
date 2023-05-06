package zio.http
import zio._

import zio.http.ChannelEvent.{ChannelUnregistered, UserEvent, UserEventTriggered}
import zio.http.socket.{WebSocketChannel, WebSocketChannelEvent}

case class TestChannel(
  in: Queue[WebSocketChannelEvent],
  out: Queue[WebSocketChannelEvent],
  promise: Promise[Nothing, Unit],
) extends WebSocketChannel {
  def awaitShutdown: UIO[Unit]                    =
    promise.await
  def receive: Task[WebSocketChannelEvent]        =
    in.take
  def send(in: WebSocketChannelEvent): Task[Unit] =
    out.offer(in).unit
  def shutdown: UIO[Unit]                         =
    in.offer(ChannelEvent.ChannelUnregistered) *>
      out.offer(ChannelEvent.ChannelUnregistered) *>
      promise.succeed(()).unit
}

object TestChannel {
  def make(
    in: Queue[WebSocketChannelEvent],
    out: Queue[WebSocketChannelEvent],
    promise: Promise[Nothing, Unit],
  ): ZIO[Any, Nothing, TestChannel] =
    for {
      _ <- out.offer(UserEventTriggered(UserEvent.HandshakeComplete))
    } yield TestChannel(in, out, promise)
}
