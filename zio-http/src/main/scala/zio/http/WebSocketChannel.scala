package zio.http

import zio._

import zio.http.ChannelEvent.{ExceptionCaught, Read, Registered, Unregistered, UserEventTriggered}
import zio.http.netty.NettyChannel

import io.netty.buffer.{ByteBufUtil, Unpooled}
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, _}

object WebSocketChannel {

  private[http] def make(
    nettyChannel: NettyChannel[JWebSocketFrame],
    queue: Queue[WebSocketChannelEvent],
  ): WebSocketChannel =
    new WebSocketChannel {
      def awaitShutdown: UIO[Unit]                    =
        nettyChannel.awaitClose
      def receive: Task[WebSocketChannelEvent]        =
        queue.take
      def send(in: WebSocketChannelEvent): Task[Unit] =
        in match {
          case Read(message) => nettyChannel.writeAndFlush(frameToNetty(message))
          case _             => ZIO.unit
        }
      def shutdown: UIO[Unit]                         =
        nettyChannel.close(false).orDie
    }

  private def frameToNetty(frame: WebSocketFrame): JWebSocketFrame =
    frame match {
      case b: WebSocketFrame.Binary                 =>
        new BinaryWebSocketFrame(b.isFinal, 0, Unpooled.wrappedBuffer(b.bytes.toArray))
      case t: WebSocketFrame.Text                   =>
        new TextWebSocketFrame(t.isFinal, 0, t.text)
      case WebSocketFrame.Close(status, Some(text)) =>
        new CloseWebSocketFrame(status, text)
      case WebSocketFrame.Close(status, None)       =>
        new CloseWebSocketFrame(status, null)
      case WebSocketFrame.Ping                      =>
        new PingWebSocketFrame()
      case WebSocketFrame.Pong                      =>
        new PongWebSocketFrame()
      case c: WebSocketFrame.Continuation           =>
        new ContinuationWebSocketFrame(c.isFinal, 0, Unpooled.wrappedBuffer(c.buffer.toArray))
    }
}
