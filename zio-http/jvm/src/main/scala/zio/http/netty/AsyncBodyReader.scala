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

import scala.collection.mutable

import zio.Chunk
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.netty.NettyBody.UnsafeAsync

import io.netty.buffer.ByteBufUtil
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}

private[netty] abstract class AsyncBodyReader extends SimpleChannelInboundHandler[HttpContent](true) {
  import zio.http.netty.AsyncBodyReader._

  private var state: State               = State.Buffering
  private val buffer                     = new mutable.ArrayBuilder.ofByte()
  private var previousAutoRead: Boolean  = false
  private var readingDone: Boolean       = false
  private var ctx: ChannelHandlerContext = _

  private def result(buffer: mutable.ArrayBuilder.ofByte): Chunk[Byte] = {
    val arr = buffer.result()
    Chunk.ByteArray(arr, 0, arr.length)
  }

  private[zio] def connect(callback: UnsafeAsync): Unit = {
    val buffer0 = buffer // Avoid reading it from the heap in the synchronized block
    this.synchronized {
      state match {
        case State.Buffering =>
          state = State.Direct(callback)

          if (readingDone) {
            callback(result(buffer0), isLast = true)
          } else if (ctx.channel().isOpen) {
            callback match {
              case UnsafeAsync.Aggregating(bufSize) => buffer.sizeHint(bufSize)
              case cb                               => cb(result(buffer0), isLast = false)
            }
            ctx.read(): Unit
          } else {
            throw new IllegalStateException("Attempting to read from a closed channel, which will never finish")
          }
        case _               =>
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
    val _ = ctx.channel().config().setAutoRead(previousAutoRead)
  }

  protected def onLastMessage(): Unit = ()

  override def channelRead0(
    ctx: ChannelHandlerContext,
    msg: HttpContent,
  ): Unit = {
    val buffer0 = buffer // Avoid reading it from the heap in the synchronized block

    this.synchronized {
      val isLast  = msg.isInstanceOf[LastHttpContent]
      val content = ByteBufUtil.getBytes(msg.content())

      if (isLast) {
        readingDone = true
        ctx.channel().pipeline().remove(this)
        onLastMessage()
      }

      state match {
        case State.Buffering                                            =>
          // `connect` method hasn't been called yet, add all incoming content to the buffer
          buffer0.addAll(content)
        case State.Direct(callback) if isLast && buffer0.knownSize == 0 =>
          // Buffer is empty, we can just use the array directly
          callback(Chunk.fromArray(content), isLast = true)
        case State.Direct(callback: UnsafeAsync.Aggregating)            =>
          // We're aggregating the full response, only call the callback on the last message
          buffer0.addAll(content)
          if (isLast) callback(result(buffer0), isLast = true)
        case State.Direct(callback)                                     =>
          // We're streaming, emit chunks as they come
          callback(Chunk.fromArray(content), isLast)
      }

      if (!isLast) ctx.read(): Unit
    }
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

private[netty] object AsyncBodyReader {

  sealed trait State

  object State {
    case object Buffering extends State

    final case class Direct(callback: UnsafeAsync) extends State
  }

  // For Scala 2.12. In Scala 2.13+, the methods directly implemented on ArrayBuilder[Byte] are selected over syntax.
  private implicit class ByteArrayBuilderOps[A](private val self: mutable.ArrayBuilder[Byte]) extends AnyVal {
    def addAll(as: Array[Byte]): Unit = self ++= as
    def knownSize: Int                = -1
  }
}
