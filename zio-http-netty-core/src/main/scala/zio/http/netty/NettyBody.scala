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

import java.nio.charset.Charset
import java.util.{Collections, WeakHashMap}

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.stream.ZStream

import zio.http.{Body, ContentType, Header}

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.util.AsciiString

object NettyBody {

  /**
   * Helper to create Body from AsciiString
   */
  def fromAsciiString(asciiString: AsciiString): Body =
    Body.fromArray(asciiString.array(), ContentType.`application/octet-stream`)

  private[zio] def fromAsync(
    unsafeAsync: UnsafeAsync => Unit,
    knownContentLength: Option[Long],
    contentTypeHeader: Option[Header.ContentType] = None,
    readMore: () => Unit = () => (),
  ): Body = {
    val ct   =
      contentTypeHeader.map(_.value).getOrElse(ContentType.`application/octet-stream`)
    val body = Body.fromStream(
      zio.blocks.streams.Stream.fromChunk(zio.blocks.chunk.Chunk.empty[Byte]),
      ct,
    )
    asyncCallbacks.put(body, AsyncBodyState(unsafeAsync, readMore))
    body
  }

  /**
   * Helper to create Body from ByteBuf
   */
  private[zio] def fromByteBuf(byteBuf: ByteBuf, contentTypeHeader: Option[Header.ContentType]): Body = {
    if (byteBuf.readableBytes() == 0) Body.empty
    else {
      val ct = contentTypeHeader.map(_.value).getOrElse(ContentType.`application/octet-stream`)
      Body.fromArray(ByteBufUtil.getBytes(byteBuf), ct)
    }
  }

  def fromCharSequence(charSequence: CharSequence, charset: Charset): Body =
    fromAsciiString(new AsciiString(charSequence, charset))

  /**
   * Stores async body callbacks for Body instances that represent async
   * (streaming) bodies. Uses a WeakHashMap so that entries are cleaned up when
   * the Body is garbage collected.
   */
  private[zio] val asyncCallbacks: java.util.Map[Body, AsyncBodyState] =
    Collections.synchronizedMap(new WeakHashMap[Body, AsyncBodyState]())

  /**
   * Holds the async callback and read-more function for an async body.
   */
  private[zio] final case class AsyncBodyState(
    unsafeAsync: UnsafeAsync => Unit,
    readMore: () => Unit,
  )

  private[zio] trait UnsafeAsync {
    def apply(message: Chunk[Byte], isLast: Boolean): Unit
    def fail(cause: Throwable): Unit
  }

  private[zio] object UnsafeAsync {
    private val FailNone = Exit.fail(None)

    final case class Aggregating(bufferInitialSize: Int)(callback: Task[Chunk[Byte]] => Unit)(implicit trace: Trace)
        extends UnsafeAsync {

      def apply(message: Chunk[Byte], isLast: Boolean): Unit = {
        assert(isLast)
        callback(Exit.succeed(message))
      }

      def fail(cause: Throwable): Unit =
        callback(ZIO.fail(cause))
    }

    final class Streaming(emit: ZStream.Emit[Any, Throwable, Byte, Unit])(implicit trace: Trace) extends UnsafeAsync {
      def apply(message: Chunk[Byte], isLast: Boolean): Unit = {
        if (message.nonEmpty) emit(Exit.succeed(message))
        if (isLast) emit(FailNone)
      }

      def fail(cause: Throwable): Unit =
        emit(ZIO.fail(Some(cause)))
    }
  }
}
