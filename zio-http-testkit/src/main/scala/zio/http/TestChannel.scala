package zio.http
import zio._

import zio.http.ChannelEvent.{UserEvent, UserEventTriggered}
import zio.http.socket.{WebSocketChannel, WebSocketChannelEvent}

case class TestChannel(in: Queue[WebSocketChannelEvent], out: Queue[WebSocketChannelEvent]) extends WebSocketChannel {
  def awaitShutdown: UIO[Unit]                    = ???
  def receive: Task[WebSocketChannelEvent]        =
    in.take
  def send(in: WebSocketChannelEvent): Task[Unit] =
    out.offer(in).unit
  def shutdown: UIO[Unit]                         = ???
}

object TestChannel {
  def make: ZIO[Any, Nothing, TestChannel] =
    for {
      in  <- Queue.unbounded[WebSocketChannelEvent]
      out <- Queue.unbounded[WebSocketChannelEvent]
      _   <- out.offer(UserEventTriggered(UserEvent.HandshakeComplete))
    } yield TestChannel(in, out)
}
