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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, Task, Trace, Unsafe, ZIO}

import zio.stream.ZStream

import zio.http.Body.{UnsafeBytes, UnsafeWriteable}
import zio.http.internal.BodyEncoding
import zio.http.{Body, Boundary, Header, Headers, MediaType}

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.channel.{Channel => JChannel}
import io.netty.util.AsciiString
object NettyBody extends BodyEncoding {

  /**
   * Helper to create Body from AsciiString
   */
  def fromAsciiString(asciiString: AsciiString): Body = AsciiStringBody(asciiString)

  private[zio] def fromAsync(
    unsafeAsync: UnsafeAsync => Unit,
    contentTypeHeader: Option[Header.ContentType] = None,
  ): Body = AsyncBody(unsafeAsync, contentTypeHeader.map(_.mediaType), contentTypeHeader.flatMap(_.boundary))

  /**
   * Helper to create Body from ByteBuf
   */
  def fromByteBuf(byteBuf: ByteBuf, contentTypeHeader: Option[Header.ContentType] = None): Body =
    ByteBufBody(byteBuf, contentTypeHeader.map(_.mediaType), contentTypeHeader.flatMap(_.boundary))

  override def fromCharSequence(charSequence: CharSequence, charset: Charset): Body =
    fromAsciiString(new AsciiString(charSequence, charset))

  private[zio] final case class AsciiStringBody(
    asciiString: AsciiString,
    override val mediaType: Option[MediaType] = None,
    override val boundary: Option[Boundary] = None,
  ) extends Body
      with UnsafeWriteable
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

    override def mediaType(newMediaType: MediaType): Body = contentType(newMediaType)

    override def contentType(newMediaType: MediaType, newBoundary: Option[Boundary] = None): Body =
      copy(mediaType = Some(newMediaType), boundary = boundary.orElse(newBoundary))
  }

  private[zio] final case class ByteBufBody(
    val byteBuf: ByteBuf,
    override val mediaType: Option[MediaType] = None,
    override val boundary: Option[Boundary] = None,
  ) extends Body
      with UnsafeWriteable
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

    override def mediaType(newMediaType: MediaType): Body = contentType(newMediaType)

    override def contentType(newMediaType: MediaType, newBoundary: Option[Boundary] = None): Body =
      copy(mediaType = Some(newMediaType), boundary = boundary.orElse(newBoundary))
  }

  private[zio] final case class AsyncBody(
    unsafeAsync: UnsafeAsync => Unit,
    override val mediaType: Option[MediaType] = None,
    override val boundary: Option[Boundary] = None,
  ) extends Body
      with UnsafeWriteable {
    override def asArray(implicit trace: Trace): Task[Array[Byte]] = asChunk.map(_.toArray)

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = asStream.runCollect

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream
        .async[Any, Throwable, Byte](emit =>
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
        )

    override def isComplete: Boolean = false

    override def isEmpty: Boolean = false

    override def toString(): String = s"AsyncBody($unsafeAsync)"

    override def mediaType(newMediaType: MediaType): Body = contentType(newMediaType)

    override def contentType(newMediaType: MediaType, newBoundary: Option[Boundary] = None): Body =
      copy(mediaType = Some(newMediaType), boundary = boundary.orElse(newBoundary))
  }

  private[zio] trait UnsafeAsync {
    def apply(message: Chunk[Byte], isLast: Boolean): Unit
    def fail(cause: Throwable): Unit
  }
}
