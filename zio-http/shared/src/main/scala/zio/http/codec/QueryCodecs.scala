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

private[codec] trait QueryCodecs {

  def query(name: String): QueryCodec[String] = singleValueCodec(name, TextCodec.string)

  def queryBool(name: String): QueryCodec[Boolean] = singleValueCodec(name, TextCodec.boolean)

  def queryInt(name: String): QueryCodec[Int] = singleValueCodec(name, TextCodec.int)

  def queryTo[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[A] = singleValueCodec(name, codec)

  def queryAll(name: String): QueryCodec[Chunk[String]] = multiValueCodec(name, TextCodec.string)

  def queryAllBool(name: String): QueryCodec[Chunk[Boolean]] = multiValueCodec(name, TextCodec.boolean)

  def queryAllInt(name: String): QueryCodec[Chunk[Int]] = multiValueCodec(name, TextCodec.int)

  def queryAllTo[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[Chunk[A]] = multiValueCodec(name, codec)

  private def singleValueCodec[A](name: String, textCodec: TextCodec[A]): QueryCodec[A] =
    HttpCodec
      .Query(name, textCodec, QueryParamHint.One)
      .transformOrFail {
        case chunk if chunk.size == 1 => Right(chunk.head)
        case chunk => Left(s"Expected single value for query parameter $name, but got ${chunk.size} instead")
      }(s => Right(Chunk(s)))

  private def multiValueCodec[A](name: String, textCodec: TextCodec[A]): QueryCodec[Chunk[A]] =
    HttpCodec.Query(name, textCodec, QueryParamHint.Many)
}
