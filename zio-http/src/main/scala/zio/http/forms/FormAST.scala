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

import java.nio.charset._

import zio._

import zio.http.model.Header.ContentTransferEncoding
import zio.http.model._

private[forms] sealed trait FormAST { def bytes: Chunk[Byte] }

private[forms] object FormAST {

  sealed trait DecodingPart1AST extends FormAST

  sealed trait DecodingPart2AST extends DecodingPart1AST

  def makePart1(
    bytes: Chunk[Byte],
    boundary: Boundary,
    encoding: Charset = StandardCharsets.UTF_8,
  ): DecodingPart1AST = {
    val header = Header.fromBytes(bytes.toArray, encoding)
    header match {
      case Some(header)                            => header
      case None if boundary.isEncapsulating(bytes) => EncapsulatingBoundary(boundary)
      case None if boundary.isClosing(bytes)       => ClosingBoundary(boundary)
      case None                                    => Content(bytes)
    }
  }

  def makePart2(
    bytes: Chunk[Byte],
    boundary: Boundary,
  ): DecodingPart2AST = {
    if (boundary.isEncapsulating(bytes)) EncapsulatingBoundary(boundary)
    else if (boundary.isClosing(bytes)) ClosingBoundary(boundary)
    else Content(bytes)
  }

  case object EoL extends FormAST { val bytes: Chunk[Byte] = Chunk('\r', '\n') }

  case class EncapsulatingBoundary(boundary: Boundary) extends DecodingPart2AST {
    def bytes: Chunk[Byte] = boundary.encapsulationBoundaryBytes
  }

  case class ClosingBoundary(boundary: Boundary) extends DecodingPart2AST {
    def bytes: Chunk[Byte] = boundary.closingBoundaryBytes
  }

  case class Header(name: String, value: String) extends DecodingPart1AST {

    /**
     * The preposition is the first part of the header value before the first
     * semicolon. For example, the preposition of "text/html; charset=utf-8" is
     * "text/html".
     *
     * If there is no semicolon, the entire value is returned.
     *
     * @return
     */
    def preposition: String = value.split(';').head

    def fields: Map[String, String] =
      value
        .split(';')
        .map(_.trim)
        .flatMap { field =>
          val tokens = field.split('=')
          if (tokens.size > 1)
            Some(tokens(0) -> tokens.last.stripPrefix("\"").stripSuffix("\""))
          else Option.empty[(String, String)]
        }
        .toMap

    def toHeaders: Headers = Headers(name -> value)

    def bytes: Chunk[Byte] = Chunk.fromArray(s"$name: $value".getBytes(StandardCharsets.UTF_8))
  }

  object Header {

    private def makeField(name: String, value: String): String = s"""; ${name}="${value}""""

    private def makeField(name: String, value: Option[String]): String =
      value.map(makeField(name, _)).getOrElse("")

    def contentType(contentType: MediaType, charset: Option[Charset] = None): Header =
      Header("Content-Type", s"${contentType.fullType}${makeField("charset", charset.map(_.name))}")

    def contentDisposition(name: String, filename: Option[String] = None): Header =
      Header("Content-Disposition", s"""form-data${makeField("name", name)}${makeField("filename", filename)}""")

    def contentTransferEncoding(xferEncoding: ContentTransferEncoding): Header =
      Header("Content-Transfer-Encoding", xferEncoding.renderedValue.toString)

    def fromBytes(bytes: Array[Byte], encoding: Charset = StandardCharsets.UTF_8): Option[Header] = {
      val i = bytes.indexOf(':')

      if (i > -1) {
        bytes.splitAt(i) match {
          case (nameBytes, valueBytes) =>
            Some(Header(new String(nameBytes, encoding), new String(valueBytes.splitAt(1)._2, encoding).trim))
        }
      } else None
    }
  }

  final case class Content(bytes: Chunk[Byte]) extends DecodingPart2AST
}
