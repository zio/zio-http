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

package zio.http.forms

import java.nio.charset.Charset

import zio.Chunk

import zio.http.forms.FormAST.Header
import zio.http.model.{Charsets, Headers, MediaType}

final case class Boundary(id: String, charset: Charset) {

  def isEncapsulating(bytes: Chunk[Byte]): Boolean = bytes == encapsulationBoundaryBytes

  def isClosing(bytes: Chunk[Byte]): Boolean = bytes == closingBoundaryBytes

  def contentTypeHeader: Headers = Headers(
    zio.http.model.Header.ContentType(MediaType.multipart.`form-data`, boundary = Some(id)),
  )

  lazy val encapsulationBoundary: String = s"--$id"

  lazy val closingBoundary: String = s"--$id--"

  private[forms] val encapsulationBoundaryBytes = Chunk.fromArray(encapsulationBoundary.getBytes(charset))

  private[forms] val closingBoundaryBytes = Chunk.fromArray(closingBoundary.getBytes(charset))

}

object Boundary {
  def apply(boundary: String): Boundary = Boundary(boundary, Charsets.Utf8)

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

  def fromHeaders(headers: Headers): Option[Boundary] = {

    val charset =
      headers
        .rawHeader(zio.http.model.Header.ContentType)
        .flatMap(value => Header("Content-Type", value).fields.get("charset"))
        .map(Charset.forName)
        .getOrElse(Charsets.Utf8)

    for {
      disp     <- headers.rawHeader(zio.http.model.Header.ContentDisposition)
      boundary <- Header("Content-Disposition", disp).fields.get("boundary")

    } yield Boundary(boundary, charset)

  }

  def randomUUID: zio.UIO[Boundary] =
    zio.Random.nextUUID.map { id =>
      Boundary(s"(((${id.toString()})))")
    }
}
