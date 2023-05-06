package zio.http.socket

import zio._

import zio.http.ChannelEvent.{ChannelRead, ChannelRegistered, ChannelUnregistered, ExceptionCaught, UserEventTriggered}
import zio.http._
import zio.http.netty.NettyChannel
import zio.http.socket.WebSocketChannelEvent

import io.netty.buffer.{ByteBufUtil, Unpooled}
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, _}

object WebSocketChannel {

  def make(
    nettyChannel: NettyChannel[JWebSocketFrame],
    queue: Queue[WebSocketChannelEvent],
  ): WebSocketChannel =
    new WebSocketChannel {
      def awaitShutdown: UIO[Unit]                   =
        ZIO.debug("await shutdown called") *> nettyChannel.awaitClose
      def receive: UIO[WebSocketChannelEvent]        =
        queue.take
      def send(in: WebSocketChannelEvent): UIO[Unit] =
        in match {
          case ChannelRegistered     => ZIO.unit
          case ChannelUnregistered   => ZIO.unit
          case ExceptionCaught(_)    => ZIO.unit
          case UserEventTriggered(_) => ZIO.unit
          case ChannelRead(message)  =>
            nettyChannel.writeAndFlush(frameToNetty(message)).debug("writeAndFlush").orDie

        }

      def shutdown: UIO[Unit] =
        ZIO.debug("shutdown called") *>
          // nettyChannel.writeAndFlush(frameToNetty(WebSocketFrame.Close(0, None))).orDie *>
          nettyChannel.close(false).orDie
      // *> ZIO.dieMessage("not implemented")
    }

  def frameToNetty(frame: WebSocketFrame): JWebSocketFrame =
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
