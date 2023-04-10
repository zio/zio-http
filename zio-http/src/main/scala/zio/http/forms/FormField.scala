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

import zio.stream.{Take, ZStream}

import zio.http.Header.ContentTransferEncoding
import zio.http.MediaType
import zio.http.forms.FormAST._
import zio.http.forms.FormDecodingError._

/**
 * Represents a field in a form. Every field contains name, content type
 * (perhaps just plaintext), type-specific content, and an optional filename.
 */
sealed trait FormField {
  def name: String
  def contentType: MediaType
  def filename: Option[String]

  final def valueAsString: Option[String] = this match {
    case FormField.Text(_, value, _, _) => Some(value)
    case FormField.Simple(_, value)     => Some(value)
    case _                              => None
  }
}

object FormField {

  /**
   * A binary form data part.
   *
   * @param name
   *   Name of this form data part. This is the value of the `name` field in the
   *   `Content-Disposition` within this part.
   * @param data
   *   The data of this form data part. This is the data between the headers and
   *   the boundary.
   * @param contentType
   *   The content type of this form data part. This is the value of the
   *   `Content-Type` with in this part.
   * @param transferEncoding
   *   The transfer encoding of this form data part. This is the value of the
   *   `Content-Transfer-Encoding` within this part. IMPORTANT NOTE: The data is
   *   not encoded in any way relative to the provided `transferEncoding`. It is
   *   the responsibility of the user to encode the `data` accordingly.
   * @param filename
   */
  final case class Binary(
    name: String,
    data: Chunk[Byte],
    contentType: MediaType,
    transferEncoding: Option[ContentTransferEncoding] = None,
    filename: Option[String] = None,
  ) extends FormField

  final case class StreamingBinary(
    name: String,
    contentType: MediaType,
    transferEncoding: Option[ContentTransferEncoding] = None,
    filename: Option[String] = None,
    data: ZStream[Any, Nothing, Byte],
  ) extends FormField {
    def collect: ZIO[Any, Nothing, Binary] = {
      data.runCollect.map { bytes =>
        Binary(name, bytes, contentType, transferEncoding, filename)
      }
    }
  }

  final case class Text(
    name: String,
    value: String,
    contentType: MediaType,
    filename: Option[String] = None,
  ) extends FormField

  final case class Simple(name: String, value: String) extends FormField {
    override val contentType: MediaType   = MediaType.text.plain
    override val filename: Option[String] = None
  }

  private[http] def fromFormAST(
    ast: Chunk[FormAST],
    defaultCharset: Charset = StandardCharsets.UTF_8,
  ): ZIO[Any, FormDecodingError, FormField] = {
    val extract =
      ast.foldLeft((Option.empty[Header], Option.empty[Header], Option.empty[Header], Chunk.empty[Content])) {
        case (accum, header: Header) if header.name == "Content-Disposition"       =>
          (Some(header), accum._2, accum._3, accum._4)
        case (accum, content: Content)                                             =>
          (accum._1, accum._2, accum._3, accum._4 :+ content)
        case (accum, header: Header) if header.name == "Content-Type"              =>
          (accum._1, Some(header), accum._3, accum._4)
        case (accum, header: Header) if header.name == "Content-Transfer-Encoding" =>
          (accum._1, accum._2, Some(header), accum._4)
        case (accum, _)                                                            => accum
      }

    for {
      disposition <- ZIO.fromOption(extract._1).orElseFail(FormDataMissingContentDisposition)
      name        <- ZIO.fromOption(extract._1.flatMap(_.fields.get("name"))).orElseFail(ContentDispositionMissingName)
      charset     <- ZIO
        .attempt(extract._2.flatMap(x => x.fields.get("charset").map(Charset.forName)).getOrElse(defaultCharset))
        .mapError(e => InvalidCharset(e.getMessage))
      contentParts     = extract._4.tail // Skip the first empty line
      content          = contentParts.foldLeft(Chunk.empty[Byte])(_ ++ _.bytes)
      contentType      = extract._2
        .flatMap(x => MediaType.forContentType(x.preposition))
        .getOrElse(MediaType.application.`octet-stream`)
      transferEncoding = extract._3
        .flatMap(x => ContentTransferEncoding.parse(x.preposition).toOption)

    } yield
      if (!contentType.binary)
        Text(name, new String(content.toArray, charset), contentType, disposition.fields.get("filename"))
      else Binary(name, content, contentType, transferEncoding, disposition.fields.get("filename"))
  }

  private[http] def getContentType(ast: Chunk[FormAST]): MediaType =
    ast.collectFirst {
      case header: Header if header.name == "Content-Type" =>
        MediaType.forContentType(header.preposition)
    }.flatten.getOrElse(MediaType.text.plain)

  private[http] def incomingStreamingBinary(
    ast: Chunk[FormAST],
    queue: Queue[Take[Nothing, Byte]],
  ): ZIO[Any, FormDecodingError, FormField] = {
    val extract =
      ast.foldLeft((Option.empty[Header], Option.empty[Header], Option.empty[Header])) {
        case (accum, header: Header) if header.name == "Content-Disposition"       =>
          (Some(header), accum._2, accum._3)
        case (accum, header: Header) if header.name == "Content-Type"              =>
          (accum._1, Some(header), accum._3)
        case (accum, header: Header) if header.name == "Content-Transfer-Encoding" =>
          (accum._1, accum._2, Some(header))
        case (accum, _)                                                            => accum
      }

    for {
      disposition <- ZIO.fromOption(extract._1).orElseFail(FormDataMissingContentDisposition)
      name        <- ZIO.fromOption(extract._1.flatMap(_.fields.get("name"))).orElseFail(ContentDispositionMissingName)
      contentType      = extract._2
        .flatMap(x => MediaType.forContentType(x.preposition))
        .getOrElse(MediaType.text.plain)
      transferEncoding = extract._3
        .flatMap(x => ContentTransferEncoding.parse(x.preposition).toOption)

    } yield StreamingBinary(
      name,
      contentType,
      transferEncoding,
      disposition.fields.get("filename"),
      ZStream.fromQueue(queue).flattenTake,
    )
  }

  def textField(name: String, value: String, mediaType: MediaType = MediaType.text.plain): FormField =
    Text(name, value, mediaType, None)

  def simpleField(name: String, value: String): FormField = Simple(name, value)

  def binaryField(
    name: String,
    data: Chunk[Byte],
    mediaType: MediaType,
    transferEncoding: Option[ContentTransferEncoding] = None,
    filename: Option[String] = None,
  ): FormField = Binary(name, data, mediaType, transferEncoding, filename)

  def streamingBinaryField(
    name: String,
    data: ZStream[Any, Nothing, Byte],
    mediaType: MediaType,
    transferEncoding: Option[ContentTransferEncoding] = None,
    filename: Option[String] = None,
  ): FormField = StreamingBinary(name, mediaType, transferEncoding, filename, data)
}
