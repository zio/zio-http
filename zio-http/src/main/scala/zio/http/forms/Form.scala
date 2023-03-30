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

import zio.http.forms.FormAST._
import zio.http.forms.FormData._
import zio.http.forms.FormDecodingError._

/**
 * Represents a form that can be either multipart or url encoded.
 */
final case class Form(formData: Chunk[FormData]) {

  def +(field: FormData): Form = append(field)

  def append(field: FormData): Form = Form(formData :+ field)

  /**
   * Runs all streaming form data and stores them in memory, returning a Form
   * that has no streaming parts
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

  def get(name: String): Option[FormData] = formData.find(_.name == name)

  def encodeAsURLEncoded(charset: Charset = StandardCharsets.UTF_8): String = {

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

  def encodeAsMultipartBytes(
    charset: Charset = StandardCharsets.UTF_8,
    rng: () => String = () => new SecureRandom().nextLong().toString,
  ): (CharSequence, ZStream[Any, Nothing, Byte]) =
    encodeAsMultipartBytes(charset, Boundary.generate(rng))

  def encodeAsMultipartBytes(
    charset: Charset,
    boundary: Boundary,
  ): (CharSequence, ZStream[Any, Nothing, Byte]) = {

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
            Content(Chunk.fromArray(value.getBytes(charset))),
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
            Content(Chunk.fromArray(value.getBytes(charset))),
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

    boundary.id -> stream.map(_.bytes).flattenChunks
  }
}

object Form {

  def apply(formData: FormData*): Form = Form(Chunk.fromIterable(formData))

  def fromStrings(formData: (String, String)*): Form = apply(
    formData.map(pair => FormData.Simple(pair._1, pair._2)): _*,
  )

  def fromMultipartBytes(
    bytes: Chunk[Byte],
    charset: Charset = StandardCharsets.UTF_8,
  ): ZIO[Any, Throwable, Form] =
    for {
      boundary <- ZIO
        .fromOption(Boundary.fromContent(bytes, charset))
        .orElseFail(FormDecodingError.BoundaryNotFoundInContent.asException)
      form     <- StreamingForm(ZStream.fromChunk(bytes), boundary, charset).collectAll
    } yield form

  def fromURLEncoded(encoded: String, encoding: Charset): ZIO[Any, FormDecodingError, Form] = {
    val fields = ZIO.foreach(encoded.split("&")) { pair =>
      val array = pair.split("=")
      if (array.length == 2)
        ZIO
          .attempt((URLDecoder.decode(array(0), encoding.name()), URLDecoder.decode(array(1), encoding.name)))
          .mapError {
            case e: UnsupportedEncodingException => InvalidCharset(e.getMessage)
            case e                               => InvalidURLEncodedFormat(e.getMessage)
          }
      else
        ZIO.fail(
          InvalidURLEncodedFormat(s"Invalid form field.  Expected: '{name}={value}'', Got '$pair' instead."),
        )
    }

    fields.map(fields => Form(Chunk.fromArray(fields.map(pair => FormData.Simple(pair._1, pair._2)))))
  }

}
