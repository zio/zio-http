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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, ChunkBuilder, Trace, Unsafe}

import zio.http.netty.AsyncBodyReader.State
import zio.http.netty.NettyBody.UnsafeAsync

import io.netty.buffer.ByteBufUtil
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
private[netty] abstract class AsyncBodyReader(implicit trace: Trace)
    extends SimpleChannelInboundHandler[HttpContent](true) {

  protected val unsafeClass: Unsafe = Unsafe.unsafe

  private var state: State                                 = State.Buffering
  private val buffer: ChunkBuilder[(Chunk[Byte], Boolean)] = ChunkBuilder.make[(Chunk[Byte], Boolean)]()
  private var previousAutoRead: Boolean                    = false
  private var ctx: ChannelHandlerContext                   = _

  def connect(callback: UnsafeAsync): Unit = {
    this.synchronized {
      state match {
        case State.Buffering =>
          state = State.Direct(callback)
          buffer.result().foreach { case (chunk, isLast) =>
            callback(chunk, isLast)
          }
          ctx.read()
        case State.Direct(_) =>
          throw new IllegalStateException("Cannot connect twice")
      }
    }
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    previousAutoRead = ctx.channel().config().isAutoRead
    ctx.channel().config().setAutoRead(false)
    this.ctx = ctx
  }

  override def handlerRemoved(ctx: ChannelHandlerContext): Unit = {
    ctx.channel().config().setAutoRead(previousAutoRead)
  }

  override def channelRead0(
    ctx: ChannelHandlerContext,
    msg: HttpContent,
  ): Unit = {
    val isLast = msg.isInstanceOf[LastHttpContent]
    val chunk  = Chunk.fromArray(ByteBufUtil.getBytes(msg.content()))

    this.synchronized {
      state match {
        case State.Buffering        =>
          buffer += ((chunk, isLast))
        case State.Direct(callback) =>
          callback(chunk, isLast)
          ctx.read()
      }
    }

    if (isLast) {
      ctx.channel().pipeline().remove(this)
    }: Unit
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
}

private[netty] object AsyncBodyReader {
  sealed trait State

  object State {
    case object Buffering extends State

    final case class Direct(callback: UnsafeAsync) extends State
  }
}
