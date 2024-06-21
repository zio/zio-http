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

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.stream.ZStream

import zio.http.Body.UnsafeBytes
import zio.http.internal.BodyEncoding
import zio.http.{Body, Boundary, MediaType}

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.util.AsciiString

object NettyBody extends BodyEncoding {

  /**
   * Helper to create Body from AsciiString
   */
  def fromAsciiString(asciiString: AsciiString): Body = AsciiStringBody(asciiString)

  private[zio] def fromAsync(
    unsafeAsync: UnsafeAsync => Unit,
    knownContentLength: Option[Long],
    contentTypeHeader: Option[String] = None,
  ): Body = {
    val (mediaType, boundary) = mediaTypeAndBoundary(contentTypeHeader)
    AsyncBody(
      unsafeAsync,
      knownContentLength,
      mediaType,
      boundary,
    )
  }

  /**
   * Helper to create Body from ByteBuf
   */
  private[zio] def fromByteBuf(byteBuf: ByteBuf, contentTypeHeader: Option[String]): Body = {
    if (byteBuf.readableBytes() == 0) Body.EmptyBody
    else {
      val (mediaType, boundary) = mediaTypeAndBoundary(contentTypeHeader)
      Body.ArrayBody(ByteBufUtil.getBytes(byteBuf), mediaType, boundary)
    }
  }

  private def mediaTypeAndBoundary(contentTypeHeader: Option[String]) = {
    val mediaType = contentTypeHeader.flatMap(MediaType.forContentType)
    val boundary  = mediaType.flatMap(_.parameters.get("boundary")).map(Boundary(_))
    (mediaType, boundary)
  }

  override def fromCharSequence(charSequence: CharSequence, charset: Charset): Body =
    fromAsciiString(new AsciiString(charSequence, charset))

  private[zio] final case class AsciiStringBody(
    asciiString: AsciiString,
    override val mediaType: Option[MediaType] = None,
    override val boundary: Option[Boundary] = None,
  ) extends Body
      with UnsafeBytes {

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = ZIO.succeed(asciiString.array())

    override def isComplete: Boolean = true

    override def isEmpty: Boolean = asciiString.isEmpty()

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] =
      ZIO.succeed(Chunk.fromArray(asciiString.array()))

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream.unwrap(asChunk.map(ZStream.fromChunk(_)))

    override def toString(): String = s"Body.fromAsciiString($asciiString)"

    private[zio] override def unsafeAsArray(implicit unsafe: Unsafe): Array[Byte] = asciiString.array()

    override def contentType(newMediaType: MediaType): Body = copy(mediaType = Some(newMediaType))

    override def contentType(newMediaType: MediaType, newBoundary: Boundary): Body =
      copy(mediaType = Some(newMediaType), boundary = Some(newBoundary))

    override def knownContentLength: Option[Long] = Some(asciiString.length().toLong)
  }

  private[zio] final case class AsyncBody(
    unsafeAsync: UnsafeAsync => Unit,
    knownContentLength: Option[Long],
    override val mediaType: Option[MediaType] = None,
    override val boundary: Option[Boundary] = None,
  ) extends Body {

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = asChunk.map(_.toArray)

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] =
      ZIO.async { cb =>
        try {
          // Cap at 100kB as a precaution in case the server sends an invalid content length
          unsafeAsync(UnsafeAsync.Aggregating(bufferSize(1024 * 100))(cb))
        } catch {
          case e: Throwable => cb(ZIO.fail(e))
        }
      }

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream
        .async[Any, Throwable, Byte](
          emit =>
            try {
              unsafeAsync(new UnsafeAsync.Streaming(emit))
            } catch {
              case e: Throwable => emit(ZIO.fail(Option(e)))
            },
          bufferSize(4096),
        )

    // No need to create a large buffer when we know the response is small
    private[this] def bufferSize(maxSize: Int): Int = {
      val cl = knownContentLength.getOrElse(4096L)
      if (cl <= 16L) 16
      else if (cl >= maxSize) maxSize
      else Integer.highestOneBit(cl.toInt - 1) << 1 // Round to next power of 2
    }

    override def isComplete: Boolean = false

    override def isEmpty: Boolean = false

    override def toString(): String = s"AsyncBody($unsafeAsync)"

    override def contentType(newMediaType: MediaType): Body = copy(mediaType = Some(newMediaType))

    override def contentType(newMediaType: MediaType, newBoundary: Boundary): Body =
      copy(mediaType = Some(newMediaType), boundary = Some(newBoundary))
  }

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
        callback(ZIO.succeed(message))
      }

      def fail(cause: Throwable): Unit =
        callback(ZIO.fail(cause))
    }

    final class Streaming(emit: ZStream.Emit[Any, Throwable, Byte, Unit])(implicit trace: Trace) extends UnsafeAsync {
      def apply(message: Chunk[Byte], isLast: Boolean): Unit = {
        if (message.nonEmpty) emit(ZIO.succeed(message))
        if (isLast) emit(FailNone)
      }

      def fail(cause: Throwable): Unit =
        emit(ZIO.fail(Some(cause)))
    }
  }
}
