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
    val (mediaType, boundary) = mediaTypeAndBoundary(contentTypeHeader)
    ByteBufBody(byteBuf, mediaType, boundary)
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

  private[zio] final case class ByteBufBody(
    byteBuf: ByteBuf,
    override val mediaType: Option[MediaType] = None,
    override val boundary: Option[Boundary] = None,
  ) extends Body
      with UnsafeBytes {

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = ZIO.succeed(ByteBufUtil.getBytes(byteBuf))

    override def isComplete: Boolean = true

    override def isEmpty: Boolean = false

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = asArray.map(Chunk.fromArray)

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream.unwrap(asChunk.map(ZStream.fromChunk(_)))

    override def toString(): String = s"Body.fromByteBuf($byteBuf)"

    override private[zio] def unsafeAsArray(implicit unsafe: Unsafe): Array[Byte] =
      ByteBufUtil.getBytes(byteBuf)

    override def contentType(newMediaType: MediaType): Body = copy(mediaType = Some(newMediaType))

    override def contentType(newMediaType: MediaType, newBoundary: Boundary): Body =
      copy(mediaType = Some(newMediaType), boundary = Some(newBoundary))

    override def knownContentLength: Option[Long] = Some(byteBuf.readableBytes().toLong)
  }

  private[zio] final case class AsyncBody(
    unsafeAsync: UnsafeAsync => Unit,
    knownContentLength: Option[Long],
    override val mediaType: Option[MediaType] = None,
    override val boundary: Option[Boundary] = None,
  ) extends Body {

    override def asArray(implicit trace: Trace): Task[Array[Byte]] =
      asChunk.map(_.toArray)

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] =
      Promise.make[Throwable, Chunk[Byte]].flatMap { promise =>
        ZIO.attempt(unsafeAsync(new AsyncChunkBuilder(promise, bufferSize)(Unsafe.unsafe))) *>
          promise.await
      }

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream
        .async[Any, Throwable, Byte](
          emit =>
            try {
              unsafeAsync(new UnsafeAsync {
                override def apply(message: Chunk[Byte], isLast: Boolean): Unit = {
                  emit(ZIO.succeed(message))
                  if (isLast) {
                    emit(ZIO.fail(None))
                  }
                }
                override def fail(cause: Throwable): Unit                       =
                  emit(ZIO.fail(Some(cause)))
              })
            } catch {
              case e: Throwable => emit(ZIO.fail(Option(e)))
            },
          bufferSize,
        )

    // No need to create a large buffer when we know the response is small
    private[this] def bufferSize: Int = {
      val cl = knownContentLength.getOrElse(4096L)
      if (cl <= 16L) 16
      else if (cl >= 4096) 4096
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

  private final class AsyncChunkBuilder(
    promise: Promise[Throwable, Chunk[Byte]],
    sizeHint: Int,
  )(implicit unsafe: Unsafe)
      extends UnsafeAsync {

    private var isFirst = true

    private val builder: ChunkBuilder.Byte = {
      val b = new ChunkBuilder.Byte()
      b.sizeHint(sizeHint)
      b
    }

    override def apply(bytes: Chunk[Byte], isLast: Boolean): Unit = {
      if (isLast && isFirst) {
        promise.unsafe.done(Exit.succeed(bytes))
      } else {
        if (isFirst) isFirst = false
        bytes match {
          case bytes: Chunk.ByteArray =>
            // Prevent boxing by accessing the byte array directly
            var i    = 0
            val arr  = bytes.array
            val size = arr.length
            while (i < size) {
              builder.addOne(arr(i))
              i += 1
            }
          case _                      =>
            builder.addAll(bytes)
        }
        if (isLast) {
          promise.unsafe.done(Exit.succeed(builder.result()))
        }
      }
    }

    override def fail(cause: Throwable): Unit =
      promise.unsafe.done(Exit.fail(cause))
  }

}
