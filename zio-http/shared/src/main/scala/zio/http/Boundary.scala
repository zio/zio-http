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

import java.nio.charset.Charset

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, Trace}

/**
 * A multipart boundary, which consists of both the boundary and its charset.
 *
 * {{{
 * import zio._
 *
 * val boundary = Boundary("form-boundary")
 * val closing  = boundary.closingBoundary // --form-boundary--
 * val parsed   = Boundary.fromContent(Chunk.fromArray("--abc\r\nrest".getBytes)) // Some(Boundary(abc,UTF-8))
 * }}}
 *
 * @see [[https://zio.dev/zio-http/reference/body/form/ Form]]
 * @param id
 *   the text of the boundary, without the leading dashes
 * @param charset
 *   the charset used to encode the boundary
 */
final case class Boundary(id: String, charset: Charset) { self =>
  /** The boundary that ends a multipart body: `--` before and after the `id`. */
  val closingBoundary: String = s"--$id--"

  /** Returns headers with a `Content-Type` of `multipart/form-data` that names this boundary. */
  def contentTypeHeader: Headers = Headers(
    Header.ContentType(MediaType.multipart.`form-data`, Some(self)),
  )

  /** The boundary that starts each part of a multipart body: `--` before the `id`. */
  val encapsulationBoundary: String = s"--$id"

  /** Returns `true` when `bytes` are exactly the `closingBoundary` encoded in the charset. */
  def isClosing(bytes: Chunk[Byte]): Boolean = bytes == closingBoundaryBytes

  /** Returns `true` when `bytes` are exactly the `encapsulationBoundary` encoded in the charset. */
  def isEncapsulating(bytes: Chunk[Byte]): Boolean = bytes == encapsulationBoundaryBytes

  private[http] val encapsulationBoundaryBytes = Chunk.fromArray(encapsulationBoundary.getBytes(charset))

  private[http] val closingBoundaryBytes = Chunk.fromArray(closingBoundary.getBytes(charset))
}

/**
 * Constructors for `Boundary`.
 */
object Boundary {
  /** Creates a boundary with the given id and the UTF-8 charset. */
  def apply(boundary: String): Boundary = Boundary(boundary, Charsets.Utf8)

  /**
   * Reads the boundary from the start of multipart content. For content that
   * starts with `--`, then the boundary, then a CR LF line break, the result
   * is that boundary: the first two `-` bytes are dropped and the bytes before
   * the second CR or LF byte are decoded with `charset`. The returned
   * `Boundary` uses UTF-8 whatever `charset` is. Returns `None` when the
   * content has fewer than two `-` bytes or fewer than two CR or LF bytes,
   * for example when the line break is a lone LF followed by text.
   */
  def fromContent(content: Chunk[Byte], charset: Charset = Charsets.Utf8): Option[Boundary] = {
    var i = 0
    var j = 0

    val boundaryBytes = content.foldWhile(Chunk.empty[Byte])(_ => i < 2) {
      case (buffer, byte) if byte == '\r' || byte == '\n' =>
        i = i + 1
        buffer
      case (buffer, byte) if byte == '-' && j < 2         =>
        j = j + 1
        buffer
      case (buffer, byte)                                 =>
        buffer :+ byte
    }
    if (i == 2 && j == 2)
      Some(Boundary(new String(boundaryBytes.toArray, charset)))
    else Option.empty
  }

  /** Returns the boundary named in the `Content-Type` header, or `None` when there is no such header or it names no boundary. */
  def fromHeaders(headers: Headers): Option[Boundary] =
    for {
      contentType <- headers.header(Header.ContentType)
      boundary    <- contentType.boundary
    } yield boundary

  /** Encodes `content` with `charset` and reads the boundary from it as `fromContent` does. */
  def fromString(content: String, charset: Charset): Option[Boundary] =
    fromContent(Chunk.fromArray(content.getBytes(charset)), charset)

  /** Creates a boundary whose id is a random UUID with four dashes before it and four dashes after it. */
  def randomUUID(implicit trace: Trace): zio.UIO[Boundary] =
    zio.Random.nextUUID.map { id =>
      Boundary(s"----${id.toString}----")
    }
}
