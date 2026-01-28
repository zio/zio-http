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
  def content[A](name: String)(implicit codec: HttpContentCodec[A]): ContentCodec[A] =
    HttpCodec.Content(codec, Some(name))

  def content[A](implicit codec: HttpContentCodec[A]): ContentCodec[A] =
    HttpCodec.Content(codec, None)

  def content[A](name: String, mediaType: MediaType)(implicit codec: HttpContentCodec[A]): ContentCodec[A] =
    HttpCodec.Content(codec.only(mediaType), Some(name))

  def content[A](mediaType: MediaType)(implicit codec: HttpContentCodec[A]): ContentCodec[A] =
    HttpCodec.Content(codec.only(mediaType), None)

  def contentStream[A](name: String)(implicit codec: HttpContentCodec[A]): ContentCodec[ZStream[Any, Nothing, A]] =
    HttpCodec.ContentStream(codec, Some(name))

  def contentStream[A](implicit codec: HttpContentCodec[A]): ContentCodec[ZStream[Any, Nothing, A]] =
    HttpCodec.ContentStream(codec, None)

  def contentStream[A](name: String, mediaType: MediaType)(implicit
    codec: HttpContentCodec[A],
  ): ContentCodec[ZStream[Any, Nothing, A]] =
    HttpCodec.ContentStream(codec.only(mediaType), Some(name))

  def contentStream[A](mediaType: MediaType)(implicit
    codec: HttpContentCodec[A],
  ): ContentCodec[ZStream[Any, Nothing, A]] =
    HttpCodec.ContentStream(codec.only(mediaType), None)

  def binaryStream(name: String): ContentCodec[ZStream[Any, Nothing, Byte]] =
    HttpCodec.ContentStream(HttpContentCodec.byteCodec, Some(name))

  def binaryStream: ContentCodec[ZStream[Any, Nothing, Byte]] =
    HttpCodec.ContentStream(HttpContentCodec.byteCodec, None)

  def binaryStream(name: String, mediaType: MediaType): ContentCodec[ZStream[Any, Nothing, Byte]] =
    HttpCodec.ContentStream(HttpContentCodec.byteCodec.only(mediaType), Some(name))

  def binaryStream(mediaType: MediaType): ContentCodec[ZStream[Any, Nothing, Byte]] =
    HttpCodec.ContentStream(HttpContentCodec.byteCodec.only(mediaType), None)
}
