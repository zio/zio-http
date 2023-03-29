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

import java.io.UnsupportedEncodingException
import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.{Charset, StandardCharsets}
import java.security.SecureRandom

import zio._

import zio.stream._

import zio.http.QueryParams
import zio.http.forms.FormAST._
import zio.http.forms.FormData._
import zio.http.forms.FormDecodingError._
import zio.http.forms.FormState._
import zio.http.model.{Boundary, Charsets, Headers}

/**
 * Represents a form that can be either multipart or url encoded.
 */
final case class Form(formData: Chunk[FormData]) {

  /**
   * Returns a new form with the specified field appended.
   */
  def +(field: FormData): Form = append(field)

  /**
   * Returns a new form with the specified field appended.
   */
  def append(field: FormData): Form = Form(formData :+ field)

  /**
   * Runs all streaming form data and stores them in memory, returning a Form
   * that has no streaming parts.
   */
  def collectAll: ZIO[Any, Throwable, Form] =
    ZIO
      .foreach(formData) {
        case streamingBinary: StreamingBinary =>
          streamingBinary.collect
        case other                            =>
          ZIO.succeed(other)
      }
      .map(Form(_))

  /**
   * Returns the first field with the specified name.
   */
  def get(name: String): Option[FormData] = map.get(name)

  /**
   * Returns a map view of the form, where the keys in the map are the field
   * names, and the values are the field data.
   */
  lazy val map: Map[String, FormData] = formData.map(fd => fd.name -> fd).toMap

  /**
   * Encodes the form using multipart encoding, choosing a random UUID as the
   * boundary.
   */
  def multipartBytesUUID: zio.UIO[(Boundary, ZStream[Any, Nothing, Byte])] =
    Boundary.randomUUID.map { boundary =>
      boundary -> multipartBytes(boundary)
    }

  /**
   * Encodes the form using multipart encoding, using the specified boundary.
   */
  def multipartBytes(
    boundary: Boundary,
  ): ZStream[Any, Nothing, Byte] = {

    val encapsulatingBoundary = EncapsulatingBoundary(boundary)
    val closingBoundary       = ClosingBoundary(boundary)

    val astStreams = formData.map {
      case fd @ Simple(name, value) =>
        ZStream.fromChunk(
          Chunk(
            encapsulatingBoundary,
            EoL,
            Header.contentDisposition(name),
            EoL,
            Header.contentType(fd.contentType),
            EoL,
            EoL,
            Content(Chunk.fromArray(value.getBytes(boundary.charset))),
            EoL,
          ),
        )

      case Text(name, value, contentType, filename)                    =>
        ZStream.fromChunk(
          Chunk(
            encapsulatingBoundary,
            EoL,
            Header.contentDisposition(name, filename),
            EoL,
            Header.contentType(contentType),
            EoL,
            EoL,
            Content(Chunk.fromArray(value.getBytes(boundary.charset))),
            EoL,
          ),
        )
      case Binary(name, data, contentType, transferEncoding, filename) =>
        val xferEncoding =
          transferEncoding.map(enc => Chunk(Header.contentTransferEncoding(enc), EoL)).getOrElse(Chunk.empty)

        ZStream.fromChunk(
          Chunk(
            encapsulatingBoundary,
            EoL,
            Header.contentDisposition(name, filename),
            EoL,
            Header.contentType(contentType),
            EoL,
          ) ++ xferEncoding ++ Chunk(EoL, Content(data), EoL),
        )

      case StreamingBinary(name, contentType, transferEncoding, filename, data) =>
        val xferEncoding =
          transferEncoding.map(enc => Chunk(Header.contentTransferEncoding(enc), EoL)).getOrElse(Chunk.empty)

        ZStream.fromChunk(
          Chunk(
            encapsulatingBoundary,
            EoL,
            Header.contentDisposition(name, filename),
            EoL,
            Header.contentType(contentType),
            EoL,
          ) ++ xferEncoding :+ EoL,
        ) ++ data.chunks.map(Content(_)) ++ ZStream(EoL)
    }

    val stream = ZStream.fromChunk(astStreams).flatten ++ ZStream.fromChunk(Chunk(closingBoundary, EoL))

    stream.map(_.bytes).flattenChunks
  }

  /**
   * Encodes the form using URL encoding, using the default charset.
   */
  def urlEncoded: String = urlEncoded(Charsets.Utf8)

  /**
   * Encodes the form using URL encoding, using the specified charset.
   */
  def urlEncoded(charset: Charset): String = {

    def makePair(name: String, value: String): String = {
      val encodedName  = URLEncoder.encode(name, charset.name())
      val encodedValue = URLEncoder.encode(value, charset.name())
      s"$encodedName=$encodedValue"
    }

    val urlEncoded = formData.foldLeft(Chunk.empty[String]) {
      case (accum, FormData.Text(k, v, _, _)) => accum :+ makePair(k, v)
      case (accum, FormData.Simple(k, v))     => accum :+ makePair(k, v)
      case (accum, _)                         => accum
    }

    urlEncoded.mkString("&")
  }
}

object Form {

  /**
   * Creates a form from the specified form data.
   */
  def apply(formData: FormData*): Form = Form(Chunk.fromIterable(formData))

  /**
   * An empty form, without any fields.
   */
  val empty: Form = Form()

  /**
   * Creates a form from the specified form data, expressed as a sequence of
   * string key-value pairs.
   */
  def fromStrings(formData: (String, String)*): Form = apply(
    formData.map(pair => FormData.Simple(pair._1, pair._2)): _*,
  )

  /**
   * Creates a form from the specified form data, encoded as multipart bytes.
   */
  def fromMultipartBytes(
    bytes: Chunk[Byte],
    charset: Charset = Charsets.Utf8,
  ): ZIO[Any, Throwable, Form] =
    for {
      boundary <- ZIO
        .fromOption(Boundary.fromContent(bytes, charset))
        .orElseFail(FormDecodingError.BoundaryNotFoundInContent.asException)
      form     <- StreamingForm(ZStream.fromChunk(bytes), boundary, charset).collectAll
    } yield form

  def fromQueryParams(queryParams: QueryParams): Form = {
    queryParams.map.foldLeft[Form](Form.empty) { case (acc, (key, values)) =>
      acc + FormData.simpleField(key, values.mkString(","))
    }
  }

  /**
   * Creates a form from the specified URL encoded data.
   */
  def fromURLEncoded(encoded: String, charset: Charset): Either[FormDecodingError, Form] =
    scala.util.Try(fromQueryParams(QueryParams.decode(encoded, charset))).toEither.left.map {
      case e: UnsupportedEncodingException => InvalidCharset(e.getMessage)
      case e                               => InvalidURLEncodedFormat(e.getMessage)
    }
}
