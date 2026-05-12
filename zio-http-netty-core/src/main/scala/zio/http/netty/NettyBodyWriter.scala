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

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.Body
import zio.http.netty.NettyBody.UnsafeAsync

import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.handler.codec.http.{DefaultHttpContent, LastHttpContent}

private[netty] object NettyBodyWriter {

  def writeAndFlush(
    body: Body,
    contentLength: Option[Long],
    ctx: ChannelHandlerContext,
  )(implicit
    trace: Trace,
  ): Option[Task[Unit]] = {

    def writeArray(bytes: Array[Byte], isLast: Boolean) = {
      val content = new DefaultHttpContent(Unpooled.wrappedBuffer(bytes))
      if (isLast) {
        // Flushes the last body content and LastHttpContent together to avoid race conditions.
        ctx.write(content)
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
      } else {
        ctx.writeAndFlush(content)
      }
    }

    val asyncState = NettyBody.asyncCallbacks.remove(body)
    if (asyncState != null) {
      asyncState.unsafeAsync(
        new UnsafeAsync {
          override def apply(message: Chunk[Byte], isLast: Boolean): Unit = {
            val arr = message match {
              case b: Chunk.ByteArray => b.array
              case other              => other.toArray
            }
            writeArray(arr, isLast): Unit
          }

          override def fail(cause: Throwable): Unit =
            ctx.fireExceptionCaught(cause): Unit
        },
      )
      None
    } else if (body.isEmpty) {
      ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
      None
    } else {
      val bytes = body.toArray
      writeArray(bytes, isLast = true)
      None
    }
  }
}
