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
import zio.Chunk
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.codec.HttpCodec.Query.QueryParamHint
import zio.http.codec.HttpCodec.{Annotated, Metadata}

private[codec] trait QueryCodecs {

  def query(name: String): QueryCodec[String] = singleValueCodec(name, TextCodec.string)

  def queryOptional(name: String): QueryCodec[Option[String]] = maybeSingleValueCodec(name, TextCodec.string)

  def queryBool(name: String): QueryCodec[Boolean] = singleValueCodec(name, TextCodec.boolean)

  def queryOptionalBool(name: String): QueryCodec[Option[Boolean]] = maybeSingleValueCodec(name, TextCodec.boolean)

  def queryInt(name: String): QueryCodec[Int] = singleValueCodec(name, TextCodec.int)

  def queryOptionalInt(name: String): QueryCodec[Option[Int]] = maybeSingleValueCodec(name, TextCodec.int)

  def queryTo[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[A] = singleValueCodec(name, codec)

  def queryOptionalTo[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[Option[A]] =
    maybeSingleValueCodec(name, codec)

  def queryAll(name: String): QueryCodec[Chunk[String]] = multiValueCodec(name, TextCodec.string)

  def queryAllOptional(name: String): QueryCodec[Chunk[String]] = maybeMultiValueCodec(name, TextCodec.string)

  def queryAllBool(name: String): QueryCodec[Chunk[Boolean]] = multiValueCodec(name, TextCodec.boolean)

  def queryAllOptionalBool(name: String): QueryCodec[Chunk[Boolean]] = maybeMultiValueCodec(name, TextCodec.boolean)

  def queryAllInt(name: String): QueryCodec[Chunk[Int]] = multiValueCodec(name, TextCodec.int)

  def queryAllOptionalInt(name: String): QueryCodec[Chunk[Int]] = maybeMultiValueCodec(name, TextCodec.int)

  def queryAllTo[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[Chunk[A]] = multiValueCodec(name, codec)

  def queryAllOptionalTo[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[Chunk[A]] =
    maybeMultiValueCodec(name, codec)

  private def singleValueCodec[A](name: String, textCodec: TextCodec[A]): QueryCodec[A] =
    HttpCodec
      .Query(name, textCodec, QueryParamHint.One)
      .transformOrFail {
        case chunk if chunk.size == 1 => Right(chunk.head)
        case chunk if chunk.isEmpty   => throw HttpCodecError.MissingQueryParam(name)
        case chunk => Left(s"Expected single value for query parameter $name, but got ${chunk.size} instead")
      }(s => Right(Chunk(s)))

  private def maybeSingleValueCodec[A](name: String, textCodec: TextCodec[A]): QueryCodec[Option[A]] = Annotated(
    {
      HttpCodec
        .Query(name, textCodec, QueryParamHint.One)
        .transformOrFail {
          case chunk if chunk.size <= 1 => Right(chunk.headOption)
          case chunk                    =>
            Left(s"Expected maximally single value for query parameter $name, but got ${chunk.size} instead")
        }(s => Right(Chunk.fromIterable(s)))
    },
    Metadata.Optional(),
  )

  private def multiValueCodec[A](name: String, textCodec: TextCodec[A]): QueryCodec[Chunk[A]] =
    HttpCodec
      .Query(name, textCodec, QueryParamHint.Many)
      .transformOrFail {
        case chunk if chunk.isEmpty => throw HttpCodecError.MissingQueryParam(name)
        case chunk                  => Right(chunk)
      }(s => Right(s))

  private def maybeMultiValueCodec[A](name: String, textCodec: TextCodec[A]): QueryCodec[Chunk[A]] = Annotated(
    HttpCodec.Query(name, textCodec, QueryParamHint.Any),
    Metadata.Optional(),
  )
}
