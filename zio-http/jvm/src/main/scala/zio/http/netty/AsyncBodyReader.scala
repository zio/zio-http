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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable

import zio.Chunk
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.netty.NettyBody.UnsafeAsync

import io.netty.buffer.ByteBufUtil
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import io.netty.util.concurrent.ScheduledFuture

private[netty] abstract class AsyncBodyReader(
  timeoutMillis: Option[Long],
  maxPreConnectBufferSize: Int = AsyncBodyReader.DefaultMaxPreConnectBufferSize,
) extends SimpleChannelInboundHandler[HttpContent](true) { self =>
  import zio.http.netty.AsyncBodyReader._

  require(maxPreConnectBufferSize >= 0, "maxPreConnectBufferSize must be >= 0")

  private var state: State                    = State.Buffering
  private val buffer                          = new mutable.ArrayBuilder.ofByte()
  // Tracks `buffer` size while in `State.Buffering` so we can apply back-pressure
  // before the consumer connects. `ArrayBuilder#knownSize` is unreliable on
  // Scala 2.12, so we count bytes ourselves.
  private var bufferedBytes: Int              = 0
  // Budget left for throwing away an unconsumed request body; see discardRemainingBody.
  private var discardableBytes: Long          = 0L
  private var previousAutoRead: Boolean       = false
  private var readingDone: Boolean            = false
  private var ctx: ChannelHandlerContext      = _
  private var timeoutTask: ScheduledFuture[_] = _

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

            // Schedule timeout task if configured
            createTimeOutTask()

            ctx.read(): Unit
          } else {
            // Channel is already closed - fail immediately with appropriate error
            callback.fail(
              new IOException(
                "Server closed connection before sending complete response body",
              ),
            )
          }
        case _               =>
          callback.fail(new IllegalStateException("Cannot connect twice"))
      }
    }
  }

  /**
   * Reads and throws away whatever is left of the request body, so that the
   * connection can be used for the next request.
   *
   * Called when a response has been written while this reader is still in the
   * pipeline, which means the body was never consumed or was only consumed in
   * part. Reading stops in that situation - `handlerAdded` disables `autoRead`
   * and the only calls to `ctx.read()` come from a consumer asking for the next
   * chunk - so without this the connection stays open but is never read from
   * again, and the next request the client sends over it is silently dropped.
   *
   * HTTP/1.1 gives a server no way to ask a client to stop sending, so the only
   * two correct options once a response has been sent early are to read the
   * rest and discard it, or to close the connection. This discards up to
   * `maxDiscardedBytes` and closes the connection if the body turns out to be
   * larger than that.
   *
   * Any body read timeout is deliberately left running: it is the only thing
   * that would stop a client that goes quiet half way from holding on to the
   * connection while we are draining it.
   */
  private[zio] def discardRemainingBody(maxDiscardedBytes: Long): Unit =
    self.synchronized {
      if (!readingDone && ctx.channel().isOpen) {
        discardableBytes = maxDiscardedBytes
        state = State.Discarding
        ctx.read(): Unit
      }
    }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    previousAutoRead = ctx.channel().config().isAutoRead
    ctx.channel().config().setAutoRead(false)
    this.ctx = ctx
  }

  override def handlerRemoved(ctx: ChannelHandlerContext): Unit = {
    val _ = ctx.channel().config().setAutoRead(previousAutoRead)
    cancelTimeoutTask()
  }

  protected def onLastMessage(): Unit = ()

  override def channelRead0(
    ctx: ChannelHandlerContext,
    msg: HttpContent,
  ): Unit = {
    val buffer0 = buffer // Avoid reading it from the heap in the synchronized block

    self.synchronized {
      val isLast  = msg.isInstanceOf[LastHttpContent]
      // A discarded chunk is only ever counted, so its bytes are copied out of the ByteBuf lazily.
      def content = ByteBufUtil.getBytes(msg.content())

      // Cancel timeout task since we received data
      if (isLast) {
        readingDone = true
        ctx.channel().pipeline().remove(this)
        onLastMessage()
      }

      val readMore =
        state match {
          case State.Discarding                                           =>
            // The consumer is gone; read on until the body ends so that the connection stays usable,
            // and give up on the connection if the client turns out to be sending more than we are
            // willing to throw away. Flushed first, so that a response still sitting in the outbound
            // buffer is not dropped along with the connection.
            discardableBytes -= msg.content().readableBytes()
            if (discardableBytes < 0) {
              ctx.flush()
              ctx.close(): Unit
              false
            } else !isLast
          case State.Buffering                                            =>
            // `connect` method hasn't been called yet, add all incoming content to the buffer.
            // Cap the pre-connect buffer to avoid unbounded heap growth when a fast producer
            // outpaces a slow-to-start consumer (see issue #3173).
            val bytes = content
            buffer0.addAll(bytes)
            bufferedBytes += bytes.length
            bufferedBytes < maxPreConnectBufferSize
          case State.Direct(callback) if isLast && buffer0.knownSize == 0 =>
            // Buffer is empty, we can just use the array directly
            callback(Chunk.fromArray(content), isLast = true)
            false
          case State.Direct(callback: UnsafeAsync.Aggregating)            =>
            // We're aggregating the full response, only call the callback on the last message
            buffer0.addAll(content)
            if (isLast) callback(result(buffer0), isLast = true)
            !isLast
          case State.Direct(callback: UnsafeAsync.Streaming)              =>
            // We're streaming, emit chunks as they come
            callback(Chunk.fromArray(content), isLast)
            // ctx.read will be called when the chunk is consumed
            false
          case State.Direct(callback)                                     =>
            // We're streaming, emit chunks as they come
            callback(Chunk.fromArray(content), isLast)
            !isLast
        }

      // Reschedule timeout for next chunk if not the last message
      if (readMore && !isLast) createTimeOutTask()

      if (readMore) ctx.read(): Unit
    }
  }

  private def createTimeOutTask(): Unit = {
    timeoutMillis.foreach { timeoutMillis =>
      timeoutTask = ctx
        .channel()
        .eventLoop()
        .schedule(
          new Runnable {
            override def run(): Unit = {
              self.synchronized {
                state match {
                  case State.Direct(cb) if !readingDone =>
                    cb.fail(
                      new IOException(
                        s"Body read timeout: server stopped sending data after ${timeoutMillis}ms",
                      ),
                    )
                    readingDone = true
                    if (ctx.channel().isOpen) {
                      ctx.channel().close(): Unit
                    }
                  case _                                => // Already completed or not connected
                }
              }
            }
          },
          timeoutMillis,
          TimeUnit.MILLISECONDS,
        )
    }
  }

  private def cancelTimeoutTask(): Unit =
    if (timeoutTask != null) {
      timeoutTask.cancel(false)
      timeoutTask = null
    }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    self.synchronized {
      cancelTimeoutTask()
      state match {
        case State.Buffering        =>
        case State.Discarding       =>
        case State.Direct(callback) =>
          callback.fail(cause)
      }
    }
    super.exceptionCaught(ctx, cause)
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    this.synchronized {
      cancelTimeoutTask()

      state match {
        case State.Buffering                        =>
        case State.Discarding                       =>
        case State.Direct(callback) if !readingDone =>
          // Step 4: Premature channel closure detection
          // This is the core issue from #2383 - server sent headers but closed before body completed
          // Provide a clear, actionable error message
          callback.fail(
            new IOException(
              "Server closed connection before sending complete response body. " +
                "This may indicate a broken server, network issue, or server-side timeout.",
            ),
          )
        case _                                      =>
        // Reading already done - this is a normal close after completion
      }
    }
    ctx.fireChannelInactive(): Unit
  }
}

private[netty] object AsyncBodyReader {

  /**
   * Default cap on bytes that may be buffered before the consumer calls
   * `connect`. Once the buffer reaches this size we stop calling `ctx.read()`,
   * relying on Netty's TCP back-pressure until the consumer drains the buffer.
   */
  final val DefaultMaxPreConnectBufferSize: Int = 64 * 1024

  sealed trait State

  object State {
    case object Buffering extends State

    final case class Direct(callback: UnsafeAsync) extends State

    /**
     * The consumer is gone and whatever is left of the body is read and thrown
     * away, so that the connection is left in a state where the next request on
     * it can be read.
     */
    case object Discarding extends State
  }

  // For Scala 2.12. In Scala 2.13+, the methods directly implemented on ArrayBuilder[Byte] are selected over syntax.
  private implicit class ByteArrayBuilderOps[A](private val self: mutable.ArrayBuilder[Byte]) extends AnyVal {
    def addAll(as: Array[Byte]): Unit = self ++= as
    def knownSize: Int                = -1
  }
}
