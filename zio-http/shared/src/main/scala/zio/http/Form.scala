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

import java.io.UnsupportedEncodingException
import java.nio.charset.Charset

import scala.jdk.CollectionConverters._

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.stream._

import zio.http.FormDecodingError._
import zio.http.FormField._
import zio.http.internal.FormAST

/**
 * Represents a form that can be either multipart or url encoded. A form holds
 * its fields in order, and several fields may have the same name.
 *
 * {{{
 * val form    = Form.fromStrings("user" -> "ada", "lang" -> "en")
 * val encoded = form.urlEncoded // user=ada&lang=en
 * val lang    = form.get("lang").flatMap(_.stringValue) // Some(en)
 * }}}
 *
 * @see [[https://zio.dev/zio-http/reference/body/form/ Form]]
 * @param formData
 *   the fields of the form, in order
 */
final case class Form(formData: Chunk[FormField]) {

  /**
   * Returns a new form with the specified field appended.
   */
  def +(field: FormField): Form = append(field)

  /**
   * Returns a new form with the specified field appended.
   */
  def append(field: FormField): Form = Form(formData :+ field)

  /**
   * Runs all streaming form data and stores them in memory, returning a Form
   * that has no streaming parts.
   */
  def collectAll(implicit trace: Trace): ZIO[Any, Throwable, Form] =
    ZIO
      .foreach(formData) {
        case streamingBinary: StreamingBinary =>
          streamingBinary.collect
        case other                            =>
          ZIO.succeed(other)
      }
      .map(Form(_))

  /**
   * Returns the field with the specified name, or `None` when there is none.
   * When several fields have the name, the last one is returned.
   */
  def get(name: String): Option[FormField] = map.get(name)

  /**
   * Returns a map view of the form, where the keys in the map are the field
   * names, and the values are the field data. When several fields have the
   * same name, the map holds the last one.
   */
  lazy val map: Map[String, FormField] = formData.map(fd => fd.name -> fd).toMap

  /**
   * Encodes the form using multipart encoding, choosing a random UUID as the
   * boundary.
   */
  def multipartBytesUUID(implicit trace: Trace): zio.UIO[(Boundary, ZStream[Any, Nothing, Byte])] =
    Boundary.randomUUID.map { boundary =>
      boundary -> multipartBytes(boundary)
    }

  /**
   * Encodes the form using multipart encoding, using the specified boundary.
   */
  def multipartBytes(
    boundary: Boundary,
  )(implicit trace: Trace): ZStream[Any, Nothing, Byte] = {

    val encapsulatingBoundary = FormAST.EncapsulatingBoundary(boundary)
    val closingBoundary       = FormAST.ClosingBoundary(boundary)

    val astStreams = formData.map {
      case fd @ Simple(name, value) =>
        ZStream.fromChunk(
          Chunk(
            encapsulatingBoundary,
            FormAST.EoL,
            FormAST.Header.contentDisposition(name),
            FormAST.EoL,
            FormAST.Header.contentType(fd.contentType),
            FormAST.EoL,
            FormAST.EoL,
            FormAST.Content(Chunk.fromArray(value.getBytes(boundary.charset))),
            FormAST.EoL,
          ),
        )

      case Text(name, value, contentType, filename)                    =>
        ZStream.fromChunk(
          Chunk(
            encapsulatingBoundary,
            FormAST.EoL,
            FormAST.Header.contentDisposition(name, filename),
            FormAST.EoL,
            FormAST.Header.contentType(contentType),
            FormAST.EoL,
            FormAST.EoL,
            FormAST.Content(Chunk.fromArray(value.getBytes(boundary.charset))),
            FormAST.EoL,
          ),
        )
      case Binary(name, data, contentType, transferEncoding, filename) =>
        val xferEncoding =
          transferEncoding
            .map(enc => Chunk(FormAST.Header.contentTransferEncoding(enc), FormAST.EoL))
            .getOrElse(Chunk.empty)

        ZStream.fromChunk(
          Chunk(
            encapsulatingBoundary,
            FormAST.EoL,
            FormAST.Header.contentDisposition(name, filename),
            FormAST.EoL,
            FormAST.Header.contentType(contentType),
            FormAST.EoL,
          ) ++ xferEncoding ++ Chunk(FormAST.EoL, FormAST.Content(data), FormAST.EoL),
        )

      case StreamingBinary(name, contentType, transferEncoding, filename, data) =>
        val xferEncoding =
          transferEncoding
            .map(enc => Chunk(FormAST.Header.contentTransferEncoding(enc), FormAST.EoL))
            .getOrElse(Chunk.empty)

        ZStream.fromChunk(
          Chunk(
            encapsulatingBoundary,
            FormAST.EoL,
            FormAST.Header.contentDisposition(name, filename),
            FormAST.EoL,
            FormAST.Header.contentType(contentType),
            FormAST.EoL,
          ) ++ xferEncoding :+ FormAST.EoL,
        ) ++ data.chunks.map(FormAST.Content(_)) ++ ZStream(FormAST.EoL)
    }

    val stream = ZStream.fromChunk(astStreams).flatten ++ ZStream.fromChunk(Chunk(closingBoundary, FormAST.EoL))

    stream.map(_.bytes).flattenChunks
  }

  /**
   * Converts the form to query parameters. Only the `FormField.Text` and
   * `FormField.Simple` fields are kept, and fields with the same name are
   * grouped under one key. Binary fields are left out.
   */
  def toQueryParams: QueryParams =
    QueryParams(formData.collect {
      case t: FormField.Text   => (t.name, Chunk(t.value))
      case s: FormField.Simple => (s.name, Chunk(s.value))
    }: _*)

  /**
   * Encodes the form using URL encoding, using the default charset. The
   * result is `name=value` pairs joined by `&`, without a leading `?`, and
   * binary fields are left out.
   */
  def urlEncoded: String = urlEncoded(Charsets.Utf8)

  /**
   * Encodes the form using URL encoding, using the specified charset. Ignores
   * any data that cannot be URL encoded.
   */
  def urlEncoded(charset: Charset): String = toQueryParams.encode(charset).drop(1)
}

/**
 * Constructors for `Form`.
 */
object Form {

  /**
   * Creates a form from the specified form data.
   */
  def apply(formData: FormField*): Form = Form(Chunk.fromIterable(formData))

  /**
   * An empty form, without any fields.
   */
  val empty: Form = Form()

  /**
   * Creates a form from the specified form data, expressed as a sequence of
   * string key-value pairs.
   */
  def fromStrings(formData: (String, String)*): Form = apply(
    formData.map(pair => FormField.Simple(pair._1, pair._2)): _*,
  )

  /**
   * Creates a form from the specified form data, encoded as multipart bytes.
   * When no boundary is given, it is read from the start of the bytes with
   * `Boundary.fromContent`, and the effect fails with a
   * `FormDecodingError.FormDecodingException` that holds
   * `FormDecodingError.BoundaryNotFoundInContent` when there is none. Parts
   * whose content type is not binary become `FormField.Text` fields, and the
   * others become `FormField.Binary` fields.
   */
  def fromMultipartBytes(
    bytes: Chunk[Byte],
    charset: Charset = Charsets.Utf8,
    boundary: Option[Boundary] = None,
  )(implicit trace: Trace): ZIO[Any, Throwable, Form] =
    for {
      boundary <- ZIO
        .fromOption(boundary.orElse(Boundary.fromContent(bytes, charset)))
        .orElseFail(FormDecodingError.BoundaryNotFoundInContent.asException)
      form     <- StreamingForm(ZStream.fromChunk(bytes), boundary).collectAll
    } yield form

  /** Creates a form from query parameters, with one `FormField.Simple` field for each key. The values of a key are joined with `,`. */
  def fromQueryParams(queryParams: QueryParams): Form = {
    queryParams.seq.foldLeft[Form](Form.empty) { case (acc, entry) =>
      acc + FormField.simpleField(entry.getKey, entry.getValue.asScala.mkString(","))
    }
  }

  /**
   * Creates a form from the specified URL encoded data. The data is decoded
   * with `QueryParams.decode`, so the values of a repeated name are joined
   * with `,` as in `Form.fromQueryParams`. The result is a `Left` when the
   * decoding throws.
   */
  def fromURLEncoded(encoded: String, charset: Charset): Either[FormDecodingError, Form] =
    scala.util.Try(fromQueryParams(QueryParams.decode(encoded, charset))).toEither.left.map {
      case e: UnsupportedEncodingException => InvalidCharset(e.getMessage)
      case e                               => InvalidURLEncodedFormat(e.getMessage)
    }
}
