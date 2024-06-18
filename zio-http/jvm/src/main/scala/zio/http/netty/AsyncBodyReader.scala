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

import java.io.IOException

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, ChunkBuilder}

import zio.http.netty.AsyncBodyReader.State
import zio.http.netty.NettyBody.UnsafeAsync

import io.netty.buffer.ByteBufUtil
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import io.netty.util.ByteProcessor

abstract class AsyncBodyReader extends SimpleChannelInboundHandler[HttpContent](true) {

  private var state: State               = State.Buffering
  private var buffer: ChunkBuilder.Byte  = new ChunkBuilder.Byte
  private var previousAutoRead: Boolean  = false
  private var readingDone: Boolean       = false
  private var ctx: ChannelHandlerContext = _

  private[zio] def connect(callback: UnsafeAsync): Unit = {
    this.synchronized {
      state match {
        case State.Buffering =>
          val chunk = buffer.result()
          if (readingDone) {
            callback(chunk, readingDone)
          } else if (ctx.channel().isOpen) {
            if (chunk.nonEmpty) callback(chunk, readingDone) else ()
            ctx.read()
          } else {
            throw new IllegalStateException("Attempting to read from a closed channel, which will never finish")
          }
        case _: State.Direct =>
          throw new IllegalStateException("Cannot connect twice")
      }
      state = State.Direct(callback)
      buffer = null // GC
    }
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    previousAutoRead = ctx.channel().config().isAutoRead
    ctx.channel().config().setAutoRead(false)
    this.ctx = ctx
  }

  override def handlerRemoved(ctx: ChannelHandlerContext): Unit = {
    val _ = ctx.channel().config().setAutoRead(previousAutoRead)
  }

  override def channelRead0(
    ctx: ChannelHandlerContext,
    msg: HttpContent,
  ): Unit = {
    val isLast  = msg.isInstanceOf[LastHttpContent]
    val content = msg.content()

    this.synchronized {
      state match {
        case State.Buffering        =>
          content.forEachByte(byteAppender)
        case State.Direct(callback) =>
          val chunk =
            if (content.readableBytes() > 0) Chunk.fromArray(ByteBufUtil.getBytes(content))
            else Chunk.empty
          callback(chunk, isLast)
      }
      if (isLast) {
        readingDone = true
        ctx.channel().pipeline().remove(this)
      } else {
        ctx.read()
      }
      ()
    }
  }

  private val byteAppender: ByteProcessor = (b: Byte) => {
    buffer.addOne(b)
    true
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    this.synchronized {
      state match {
        case State.Buffering        =>
        case State.Direct(callback) =>
          callback.fail(cause)
      }
    }
    super.exceptionCaught(ctx, cause)
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    this.synchronized {
      state match {
        case State.Buffering        =>
        case State.Direct(callback) =>
          callback.fail(new IOException("Channel closed unexpectedly"))
      }
    }
    ctx.fireChannelInactive(): Unit
  }
}

object AsyncBodyReader {
  sealed trait State

  object State {
    case object Buffering extends State

    final case class Direct(callback: UnsafeAsync) extends State
  }
}
