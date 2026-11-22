/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.netty

import java.util.concurrent.TimeUnit

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.channel.socket.DuplexChannel
import io.netty.handler.ssl.SslHandler
import io.netty.util.ReferenceCountUtil

/**
 * Closes a server connection on which the client is still sending, without
 * losing the response that has been written on it.
 *
 * Closing a socket that has unread input on it makes the kernel reset the
 * connection, and a reset throws away whatever of the response has not yet
 * reached the client. Not reading at all is no better: a client that sends its
 * request in full before it reads the response then blocks on a full window
 * while the server blocks on writing the response it does not read.
 *
 * So from here on all input is read and thrown away, and no request on the
 * connection is dispatched any more - not even one the decoder had already
 * buffered - since its response could never be sent. Once everything written so
 * far has reached the socket, the output is shut down, which tells the client
 * the response is complete. Over TLS this is a `close_notify` rather than a
 * bare TCP half-close, so that the client does not see the session cut short.
 * The connection is closed when the client closes its end in turn,
 * [[LingerAfterResponseMillis]] after the response is out, or
 * [[MaxLingerMillis]] after this began, whichever comes first.
 */
private[netty] object LingeringClose {

  /**
   * How long a client that has been sent the whole response may go on sending
   * before the connection is closed on it regardless.
   */
  final val LingerAfterResponseMillis: Long = 5000L

  /**
   * How long the whole of it may take, including waiting for a client to take
   * in the response; one that never reads would otherwise be read from forever.
   */
  final val MaxLingerMillis: Long = 30000L

  private val DiscardInput   = "LINGERING_CLOSE_DISCARD_INPUT"
  private val DiscardDecoded = "LINGERING_CLOSE_DISCARD_DECODED"

  def apply(channel: Channel): Unit = {
    val pipeline = channel.pipeline()
    if (pipeline.get(DiscardInput) eq null) {
      val decoder = pipeline.get(Names.HttpRequestDecoder)
      if (decoder ne null) {
        // Input, so that it is not decoded at all. After the TLS handler, so that
        // its records are decoded and a close_notify from the client is seen, and
        // after the read timeout, so that input that keeps arriving keeps the
        // connection from being closed as idle ...
        pipeline.addBefore(Names.HttpRequestDecoder, DiscardInput, DiscardInbound)
        // ... and after the decoder, whatever it had buffered already, so that
        // nothing past it sees another request.
        pipeline.addAfter(Names.HttpRequestDecoder, DiscardDecoded, DiscardInbound)
      } else {
        pipeline.addFirst(DiscardInput, DiscardInbound)
      }
      channel.config().setAutoRead(true)
      channel.read()
      closeAfter(channel, MaxLingerMillis)
      // Writes complete in order, so this completes only once the response before it has been written in full.
      channel
        .writeAndFlush(Unpooled.EMPTY_BUFFER)
        .addListener(new ChannelFutureListener {
          override def operationComplete(written: ChannelFuture): Unit =
            if (written.isSuccess && channel.isActive) {
              val sslHandler = pipeline.get(classOf[SslHandler])
              if (sslHandler ne null) {
                // Tell the client the response is complete in TLS terms, without
                // closing the channel: its inbound side is still needed to read
                // the rest of the request, until the linger time is up.
                sslHandler.closeOutbound(): Unit
                closeAfter(channel, LingerAfterResponseMillis)
              } else
                channel match {
                  case duplex: DuplexChannel =>
                    duplex.shutdownOutput()
                    closeAfter(channel, LingerAfterResponseMillis)
                  case _                     =>
                    channel.close(): Unit
                }
            } else {
              channel.close(): Unit
            }
        }): Unit
    }
  }

  private def closeAfter(channel: Channel, millis: Long): Unit = {
    val forcedClose = channel
      .eventLoop()
      .schedule(
        new Runnable { override def run(): Unit = channel.close(): Unit },
        millis,
        TimeUnit.MILLISECONDS,
      )
    channel
      .closeFuture()
      .addListener(new ChannelFutureListener {
        override def operationComplete(closed: ChannelFuture): Unit = forcedClose.cancel(false): Unit
      }): Unit
  }

  @Sharable
  private object DiscardInbound extends ChannelInboundHandlerAdapter {
    override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit =
      ReferenceCountUtil.release(msg): Unit
  }
}
