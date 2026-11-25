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

package zio.http.codec

import zio.stream.ZStream

import zio.http.MediaType

private[codec] trait ContentCodecs {

  /**
   * A codec for a body of type `A`, named `name`. When several content codecs
   * are combined with `++`, the body is a `multipart/form-data` form and the
   * name is the name of this part. The implicit [[HttpContentCodec]], by
   * default derived from a `Schema[A]`, sets the supported media types.
   *
   * {{{
   * import zio.http._
   * import zio.http.codec._
   * import zio.schema._
   *
   * final case class Book(title: String, pages: Int)
   * implicit val bookSchema: Schema[Book] = DeriveSchema.gen[Book]
   *
   * val book: ContentCodec[Book] = HttpCodec.content[Book]("book")
   * val request: Request         = book.encodeRequest(Book("Dune", 412))
   * // body: {"title":"Dune","pages":412}, Content-Type: application/json
   * }}}
   */
  def content[A](name: String)(implicit codec: HttpContentCodec[A]): ContentCodec[A] =
    HttpCodec.Content(codec, Some(name))

  /**
   * A codec for a body of type `A`, with the media types of the implicit
   * [[HttpContentCodec]].
   */
  def content[A](implicit codec: HttpContentCodec[A]): ContentCodec[A] =
    HttpCodec.Content(codec, None)

  /**
   * A codec for a body of type `A`, named `name`, in `mediaType` only. It
   * throws an `IllegalArgumentException` when the implicit [[HttpContentCodec]]
   * does not support `mediaType`.
   */
  def content[A](name: String, mediaType: MediaType)(implicit codec: HttpContentCodec[A]): ContentCodec[A] =
    HttpCodec.Content(codec.only(mediaType), Some(name))

  /**
   * A codec for a body of type `A` in `mediaType` only. It throws an
   * `IllegalArgumentException` when the implicit [[HttpContentCodec]] does not
   * support `mediaType`.
   */
  def content[A](mediaType: MediaType)(implicit codec: HttpContentCodec[A]): ContentCodec[A] =
    HttpCodec.Content(codec.only(mediaType), None)

  /**
   * A codec for a body that is a stream of `A` values, named `name`. With the
   * default [[CodecConfig]], JSON values are written one per line; with
   * `treatStreamsAsArrays = true`, as one JSON array.
   */
  def contentStream[A](name: String)(implicit codec: HttpContentCodec[A]): ContentCodec[ZStream[Any, Nothing, A]] =
    HttpCodec.ContentStream(codec, Some(name))

  /**
   * A codec for a body that is a stream of `A` values, with the media types of
   * the implicit [[HttpContentCodec]].
   */
  def contentStream[A](implicit codec: HttpContentCodec[A]): ContentCodec[ZStream[Any, Nothing, A]] =
    HttpCodec.ContentStream(codec, None)

  /**
   * A codec for a body that is a stream of `A` values, named `name`, in
   * `mediaType` only. It throws an `IllegalArgumentException` when the implicit
   * [[HttpContentCodec]] does not support `mediaType`.
   */
  def contentStream[A](name: String, mediaType: MediaType)(implicit
    codec: HttpContentCodec[A],
  ): ContentCodec[ZStream[Any, Nothing, A]] =
    HttpCodec.ContentStream(codec.only(mediaType), Some(name))

  /**
   * A codec for a body that is a stream of `A` values in `mediaType` only. It
   * throws an `IllegalArgumentException` when the implicit [[HttpContentCodec]]
   * does not support `mediaType`.
   */
  def contentStream[A](mediaType: MediaType)(implicit
    codec: HttpContentCodec[A],
  ): ContentCodec[ZStream[Any, Nothing, A]] =
    HttpCodec.ContentStream(codec.only(mediaType), None)

  /**
   * A codec for a body that is a stream of bytes, named `name`, in any binary
   * media type. Without an accepted media type, the encoded body has the
   * `Content-Type` `application/x-bdoc`, the first media type of
   * `HttpContentCodec.byteCodec`.
   */
  def binaryStream(name: String): ContentCodec[ZStream[Any, Nothing, Byte]] =
    HttpCodec.ContentStream(HttpContentCodec.byteCodec, Some(name))

  /**
   * A codec for a body that is a stream of bytes, in any binary media type.
   * Without an accepted media type, the encoded body has the `Content-Type`
   * `application/x-bdoc`, the first media type of `HttpContentCodec.byteCodec`.
   */
  def binaryStream: ContentCodec[ZStream[Any, Nothing, Byte]] =
    HttpCodec.ContentStream(HttpContentCodec.byteCodec, None)

  /**
   * A codec for a body that is a stream of bytes, named `name`, in `mediaType`
   * only. It throws an `IllegalArgumentException` when `mediaType` is not a
   * binary media type.
   */
  def binaryStream(name: String, mediaType: MediaType): ContentCodec[ZStream[Any, Nothing, Byte]] =
    HttpCodec.ContentStream(HttpContentCodec.byteCodec.only(mediaType), Some(name))

  /**
   * A codec for a body that is a stream of bytes in `mediaType` only. It throws
   * an `IllegalArgumentException` when `mediaType` is not a binary media type.
   */
  def binaryStream(mediaType: MediaType): ContentCodec[ZStream[Any, Nothing, Byte]] =
    HttpCodec.ContentStream(HttpContentCodec.byteCodec.only(mediaType), None)
}
