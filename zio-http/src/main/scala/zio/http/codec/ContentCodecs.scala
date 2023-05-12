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

import zio.schema.Schema

import zio.http.MediaType

private[codec] trait ContentCodecs {
  def content[A](name: String)(implicit schema: Schema[A]): ContentCodec[A] =
    HttpCodec.Content(schema, mediaType = None, Some(name))

  def content[A](implicit schema: Schema[A]): ContentCodec[A] =
    HttpCodec.Content(schema, mediaType = None, None)

  def content[A](name: String, mediaType: MediaType)(implicit schema: Schema[A]): ContentCodec[A] =
    HttpCodec.Content(schema, mediaType = Some(mediaType), Some(name))

  def content[A](mediaType: MediaType)(implicit schema: Schema[A]): ContentCodec[A] =
    HttpCodec.Content(schema, mediaType = Some(mediaType), None)

  def contentStream[A](name: String)(implicit schema: Schema[A]): ContentCodec[ZStream[Any, Nothing, A]] =
    HttpCodec.ContentStream(schema, mediaType = None, Some(name))

  def contentStream[A](implicit schema: Schema[A]): ContentCodec[ZStream[Any, Nothing, A]] =
    HttpCodec.ContentStream(schema, mediaType = None, None)

  def contentStream[A](name: String, mediaType: MediaType)(implicit
    schema: Schema[A],
  ): ContentCodec[ZStream[Any, Nothing, A]] =
    HttpCodec.ContentStream(schema, mediaType = Some(mediaType), Some(name))

  def contentStream[A](mediaType: MediaType)(implicit
    schema: Schema[A],
  ): ContentCodec[ZStream[Any, Nothing, A]] =
    HttpCodec.ContentStream(schema, mediaType = Some(mediaType), None)
}
