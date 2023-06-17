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

import zio.http.internal.BodyEncoding

/**
 * Represents the body of a request or response. The body can be a fixed chunk
 * of bytes, a stream of bytes, or form data, or any type that can be encoded
 * into such representations (such as textual data using some character
 * encoding, the contents of files, JSON, etc.).
 */
trait Body { self =>

  /**
   * A right-biased way of combining two bodies. If either body is empty, the
   * other will be returned. Otherwise, the right body will be returned.
   */
  def ++(that: Body): Body = if (that.isEmpty) self else that

  /**
   * Returns an effect that decodes the content of the body as array of bytes.
   * Note that attempting to decode a large stream of bytes into an array could
   * result in an out of memory error.
   */
  def asArray(implicit trace: Trace): Task[Array[Byte]]

  /**
   * Returns an effect that decodes the content of the body as a chunk of bytes.
   * Note that attempting to decode a large stream of bytes into a chunk could
   * result in an out of memory error.
   */
  def asChunk(implicit trace: Trace): Task[Chunk[Byte]]

  /**
   * Returns an effect that decodes the content of the body as a multipart form.
   * Note that attempting to decode a large stream of bytes into a form could
   * result in an out of memory error.
   */
  def asMultipartForm(implicit trace: Trace): Task[Form] =
    for {
      bytes <- asChunk
      form  <- Form.fromMultipartBytes(bytes, Charsets.Http)
    } yield form

  /**
   * Returns an effect that decodes the streaming body as a multipart form.
   *
   * The result is a stream of FormField objects, where each FormField may be a
   * StreamingBinary or a Text object. The StreamingBinary object contains a
   * stream of bytes, which has to be consumed asynchronously by the user to get
   * the next FormField from the stream.
   */
  def asMultipartFormStream(implicit trace: Trace): Task[StreamingForm] =
    boundary match {
      case Some(boundary) =>
        ZIO.succeed(
          StreamingForm(asStream, boundary),
        )
      case None           =>
        ZIO.fail(
          new IllegalStateException("Cannot decode body as streaming multipart/form-data without a known boundary"),
        )
    }

  /**
   * Returns a stream that contains the bytes of the body. This method is safe
   * to use with large bodies, because the elements of the returned stream are
   * lazily produced from the body.
   */
  def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte]

  /**
   * Decodes the content of the body as a string with the default charset. Note
   * that attempting to decode a large stream of bytes into a string could
   * result in an out of memory error.
   */
  final def asString(implicit trace: Trace): Task[String] =
    asArray.map(new String(_, Charsets.Http))

  /**
   * Decodes the content of the body as a string with the provided charset. Note
   * that attempting to decode a large stream of bytes into a string could
   * result in an out of memory error.
   */
  final def asString(charset: Charset)(implicit trace: Trace): Task[String] =
    asArray.map(new String(_, charset))

  /**
   * Returns an effect that decodes the content of the body as form data.
   */
  def asURLEncodedForm(implicit trace: Trace): Task[Form] =
    asString.flatMap(string => ZIO.fromEither(Form.fromURLEncoded(string, Charsets.Http)))

  /**
   * Returns whether or not the bytes of the body have been fully read.
   */
  def isComplete: Boolean

  /**
   * Returns whether or not the body is known to be empty. Note that some bodies
   * may not be known to be empty until an attempt is made to consume them.
   */
  def isEmpty: Boolean

  private[zio] def mediaType: Option[MediaType]
  private[zio] def boundary: Option[Boundary]

  private[zio] def contentType(newMediaType: MediaType, newBoundary: Option[Boundary] = None): Body
}

object Body {

  /**
   * A body that contains no data.
   */
  val empty: Body = EmptyBody

  /**
   * Constructs a [[zio.http.Body]] from the contents of a file.
   */
  def fromCharSequence(charSequence: CharSequence, charset: Charset = Charsets.Http): Body =
    BodyEncoding.default.fromCharSequence(charSequence, charset)

  /**
   * Constructs a [[zio.http.Body]] from a chunk of bytes.
   */
  def fromChunk(data: Chunk[Byte]): Body = ChunkBody(data)

  /**
   * Constructs a [[zio.http.Body]] from the contents of a file.
   */
  def fromFile(file: java.io.File, chunkSize: Int = 1024 * 4): Body = FileBody(file, chunkSize)

  /**
   * Constructs a [[zio.http.Body]] from from form data, using multipart
   * encoding and the specified character set, which defaults to UTF-8.
   */
  def fromMultipartForm(
    form: Form,
    specificBoundary: Boundary,
  ): Body = {
    val bytes = form.multipartBytes(specificBoundary)

    StreamBody(bytes, Some(MediaType.multipart.`form-data`), Some(specificBoundary))
  }

  /**
   * Constructs a [[zio.http.Body]] from from form data, using multipart
   * encoding and the specified character set, which defaults to UTF-8. Utilizes
   * a random boundary based on a UUID.
   */
  def fromMultipartFormUUID(
    form: Form,
  ): UIO[Body] =
    form.multipartBytesUUID.map { case (boundary, bytes) =>
      StreamBody(bytes, Some(MediaType.multipart.`form-data`), Some(boundary))
    }

  /**
   * Constructs a [[zio.http.Body]] from a stream of bytes.
   */
  def fromStream(stream: ZStream[Any, Throwable, Byte]): Body = StreamBody(stream)

  /**
   * Constructs a [[zio.http.Body]] from a stream of text, using the specified
   * character set, which defaults to the HTTP character set.
   */
  def fromStream(stream: ZStream[Any, Throwable, CharSequence], charset: Charset = Charsets.Http)(implicit
    trace: Trace,
  ): Body =
    fromStream(stream.map(seq => Chunk.fromArray(seq.toString.getBytes(charset))).flattenChunks)

  /**
   * Helper to create Body from String
   */
  def fromString(text: String, charset: Charset = Charsets.Http): Body = fromCharSequence(text, charset)

  /**
   * Constructs a [[zio.http.Body]] from form data using URL encoding and the
   * default character set.
   */
  def fromURLEncodedForm(form: Form, charset: Charset = StandardCharsets.UTF_8): Body = {
    fromString(form.urlEncoded(charset), charset).contentType(MediaType.application.`x-www-form-urlencoded`)
  }

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

    override def isEmpty: Boolean = true

    override def toString(): String = "Body.empty"

    override private[zio] def unsafeAsArray(implicit unsafe: Unsafe): Array[Byte] = Array.empty[Byte]

    override private[zio] def mediaType: Option[MediaType] = None

    override private[zio] def boundary: Option[Boundary] = None

    override def contentType(newMediaType: MediaType, newBoundary: Option[Boundary] = None): Body = EmptyBody
  }

  private[zio] final case class ChunkBody(
    data: Chunk[Byte],
    override val mediaType: Option[MediaType] = None,
    override val boundary: Option[Boundary] = None,
  ) extends Body
      with UnsafeWriteable
      with UnsafeBytes { self =>

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = ZIO.succeed(data.toArray)

    override def isComplete: Boolean = true

    override def isEmpty: Boolean = data.isEmpty

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = ZIO.succeed(data)

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream.unwrap(asChunk.map(ZStream.fromChunk(_)))

    override def toString(): String = s"Body.fromChunk($data)"

    override private[zio] def unsafeAsArray(implicit unsafe: Unsafe): Array[Byte] = data.toArray

    override def contentType(newMediaType: MediaType, newBoundary: Option[Boundary] = None): Body =
      copy(mediaType = Some(newMediaType), boundary = boundary.orElse(newBoundary))
  }

  private[zio] final case class FileBody(
    val file: java.io.File,
    chunkSize: Int = 1024 * 4,
    override val mediaType: Option[MediaType] = None,
    override val boundary: Option[Boundary] = None,
  ) extends Body
      with UnsafeWriteable
      with UnsafeBytes {

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = ZIO.attempt {
      Files.readAllBytes(file.toPath)
    }

    override def isComplete: Boolean = false

    override def isEmpty: Boolean = false

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

    override def contentType(newMediaType: MediaType, newBoundary: Option[Boundary] = None): Body =
      copy(mediaType = Some(newMediaType), boundary = boundary.orElse(newBoundary))
  }

  private[zio] final case class StreamBody(
    stream: ZStream[Any, Throwable, Byte],
    override val mediaType: Option[MediaType] = None,
    override val boundary: Option[Boundary] = None,
  ) extends Body {

    override def asArray(implicit trace: Trace): Task[Array[Byte]] = asChunk.map(_.toArray)

    override def isComplete: Boolean = false

    override def isEmpty: Boolean = false

    override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = stream.runCollect

    override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] = stream

    override def contentType(newMediaType: MediaType, newBoundary: Option[Boundary] = None): Body =
      copy(mediaType = Some(newMediaType), boundary = boundary.orElse(newBoundary))
  }

  private val zioEmptyArray = ZIO.succeed(Array.empty[Byte])

  private val zioEmptyChunk = ZIO.succeed(Chunk.empty[Byte])

}
