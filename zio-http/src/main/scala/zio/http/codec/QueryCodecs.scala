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
import zio.{Chunk, NonEmptyChunk}
private[codec] trait QueryCodecs {
  @inline def queryAs[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[A] =
    HttpCodec.Query(name, TextChunkCodec.one(codec))

  def query(name: String): QueryCodec[String] = queryAs[String](name)

  def queryBool(name: String): QueryCodec[Boolean] = queryAs[Boolean](name)

  def queryInt(name: String): QueryCodec[Int] = queryAs[Int](name)

  def queryOpt[I](name: String)(implicit codec: TextCodec[I]): QueryCodec[Option[I]] =
    HttpCodec.Query(name, TextChunkCodec.optional(codec))

  def queryAll[I](name: String)(implicit codec: TextCodec[I]): QueryCodec[Chunk[I]] =
    HttpCodec.Query(name, TextChunkCodec.any(codec))

  def queryOneOrMore[I](name: String)(implicit codec: TextCodec[I]): QueryCodec[NonEmptyChunk[I]] =
    HttpCodec.Query(name, TextChunkCodec.oneOrMore(codec))
}
