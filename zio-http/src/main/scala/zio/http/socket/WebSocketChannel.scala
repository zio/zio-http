package zio.http.socket

import zio._

import zio.http._
import zio.http.socket.WebSocketChannelEvent

object WebSocketChannel {

  def make(queue: Queue[WebSocketChannelEvent]): WebSocketChannel =
    new WebSocketChannel {
      def awaitShutdown: UIO[Unit]                   =
        ???
      def receive: UIO[WebSocketChannelEvent]        =
        queue.take
      def send(in: WebSocketChannelEvent): UIO[Unit] =
        ???
      def shutdown: UIO[Unit]                        =
        ???
    }
}
