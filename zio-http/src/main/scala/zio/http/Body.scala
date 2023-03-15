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

package zio.http

import java.io.FileInputStream
import java.nio.charset._
import java.nio.file._

import zio._

import zio.stream.ZStream

import zio.http.forms._
import zio.http.internal.BodyEncoding
import zio.http.model.{HTTP_CHARSET, Headers, MediaType}

/**
 * Holds Body that needs to be written on the HttpChannel
 */
trait Body { self =>

  def asArray(implicit trace: Trace): Task[Array[Byte]]

  def asChunk(implicit trace: Trace): Task[Chunk[Byte]]

  def asURLEncodedForm(implicit trace: Trace): Task[Form] =
    asString.flatMap(Form.fromURLEncoded(_, HTTP_CHARSET).mapError(_.asException))

  def asMultipartForm(implicit trace: Trace): Task[Form] =
    for {
      bytes <- asChunk
      form  <- Form.fromMultipartBytes(bytes, HTTP_CHARSET).mapError(_.asException)
    } yield form

  def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte]

  /**
   * Decodes the content of request as string with the provided charset.
   */
  final def asString(charset: Charset)(implicit trace: Trace): Task[String] =
    asArray.map(new String(_, charset))

  /**
   * Decodes the content of request as string with the default charset.
   */
  final def asString(implicit trace: Trace): Task[String] =
    asArray.map(new String(_, HTTP_CHARSET))

  def isComplete: Boolean

  private[zio] def mediaType: Option[MediaType]
  private[zio] def boundary: Option[CharSequence]

  private[zio] def withContentType(newMediaType: MediaType, newBoundary: Option[CharSequence] = None): Body
}

object Body {

  val empty: Body = EmptyBody

  /**
   * Helper to create Body from CharSequence
   */
  def fromCharSequence(charSequence: CharSequence, charset: Charset = HTTP_CHARSET): Body =
    BodyEncoding.default.fromCharSequence(charSequence, charset)

  /**
   * Helper to create Body from chunk of bytes
   */
  def fromChunk(data: Chunk[Byte]): Body = new ChunkBody(data)

  /**
   * Helper to create Body from contents of a file
   */
  def fromFile(file: java.io.File, chunkSize: Int = 1024 * 4): Body = new FileBody(file, chunkSize)

  def fromURLEncodedForm(form: Form, charset: Charset = StandardCharsets.UTF_8): Body = {
    fromString(form.encodeAsURLEncoded(charset), charset).withContentType(MediaType.application.`x-www-form-urlencoded`)
  }

  def fromMultipartForm(
    form: Form,
    charset: Charset = StandardCharsets.UTF_8,
    specificBoundary: Option[Boundary] = None,
  ): Body = {
    val (boundary, bytes) =
      specificBoundary match {
        case Some(value) => form.encodeAsMultipartBytes(charset, value)
        case None        => form.encodeAsMultipartBytes(charset)
      }
    ChunkBody(bytes).withContentType(MediaType.multipart.`form-data`, Some(boundary))
  }

  /**
   * Helper to create Body from Stream of string
   */
  def fromStream(stream: ZStream[Any, Throwable, CharSequence], charset: Charset = HTTP_CHARSET)(implicit
    trace: Trace,
  ): Body =
    fromStream(stream.map(seq => Chunk.fromArray(seq.toString.getBytes(charset))).flattenChunks)

  /**
   * Helper to create Body from Stream of bytes
   */
  def fromStream(stream: ZStream[Any, Throwable, Byte]): Body = new StreamBody(stream)

  /**
   * Helper to create Body from String
   */
  def fromString(text: String, charset: Charset = HTTP_CHARSET): Body = fromCharSequence(text, charset)

  private[zio] trait UnsafeWriteable extends Body

  private[zio] trait UnsafeBytes extends Body {
    private[zio] def unsafeAsArray(implicit unsafe: Unsafe): Array[Byte]
  }

  /**
   * Helper to create empty Body
   */

  private[zio] object EmptyBody extends Body with UnsafeWriteable with UnsafeBytes {

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = zioEmptyArray

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = zioEmptyChunk

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] = ZStream.empty
    override def isComplete: Boolean                                            = true

    override def toString(): String = "Body.empty"

    override private[zio] def unsafeAsArray(implicit unsafe: Unsafe): Array[Byte] = Array.empty[Byte]

    override private[zio] def mediaType: Option[MediaType] = None

    override private[zio] def boundary: Option[CharSequence] = None

    override def withContentType(newMediaType: MediaType, newBoundary: Option[CharSequence] = None): Body = EmptyBody
  }

  private[zio] final case class ChunkBody(
    data: Chunk[Byte],
    override val mediaType: Option[MediaType] = None,
    override val boundary: Option[CharSequence] = None,
  ) extends Body
      with UnsafeWriteable
      with UnsafeBytes {

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = ZIO.succeed(data.toArray)

    override def isComplete: Boolean = true

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = ZIO.succeed(data)

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream.unwrap(asChunk.map(ZStream.fromChunk(_)))

    override def toString(): String = s"Body.fromChunk($data)"

    override private[zio] def unsafeAsArray(implicit unsafe: Unsafe): Array[Byte] = data.toArray

    override def withContentType(newMediaType: MediaType, newBoundary: Option[CharSequence] = None): Body =
      copy(mediaType = Some(newMediaType), boundary = boundary.orElse(newBoundary))
  }

  private[zio] final case class FileBody(
    val file: java.io.File,
    chunkSize: Int = 1024 * 4,
    override val mediaType: Option[MediaType] = None,
    override val boundary: Option[CharSequence] = None,
  ) extends Body
      with UnsafeWriteable
      with UnsafeBytes {

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = ZIO.attempt {
      Files.readAllBytes(file.toPath)
    }

    override def isComplete: Boolean = false

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] =
      asArray.map(Chunk.fromArray)

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream.unwrap {
        for {
          file <- ZIO.attempt(file)
          fs   <- ZIO.attempt(new FileInputStream(file))
          size = Math.min(chunkSize.toLong, file.length()).toInt
        } yield ZStream
          .repeatZIOOption[Any, Throwable, Chunk[Byte]] {
            for {
              buffer <- ZIO.succeed(new Array[Byte](size))
              len    <- ZIO.attemptBlocking(fs.read(buffer)).mapError(Some(_))
              bytes  <-
                if (len > 0) ZIO.succeed(Chunk.fromArray(buffer.slice(0, len)))
                else ZIO.fail(None)
            } yield bytes
          }
          .ensuring(ZIO.succeed(fs.close()))
      }.flattenChunks

    override private[zio] def unsafeAsArray(implicit unsafe: Unsafe): Array[Byte] =
      Files.readAllBytes(file.toPath)

    override def withContentType(newMediaType: MediaType, newBoundary: Option[CharSequence] = None): Body =
      copy(mediaType = Some(newMediaType), boundary = boundary.orElse(newBoundary))
  }

  private[zio] final case class StreamBody(
    stream: ZStream[Any, Throwable, Byte],
    override val mediaType: Option[MediaType] = None,
    override val boundary: Option[CharSequence] = None,
  ) extends Body {

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = asChunk.map(_.toArray)

    override def isComplete: Boolean = false

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = stream.runCollect

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] = stream

    override def withContentType(newMediaType: MediaType, newBoundary: Option[CharSequence] = None): Body =
      copy(mediaType = Some(newMediaType), boundary = boundary.orElse(newBoundary))
  }

  private val zioEmptyArray = ZIO.succeed(Array.empty[Byte])

  private val zioEmptyChunk = ZIO.succeed(Chunk.empty[Byte])

}
