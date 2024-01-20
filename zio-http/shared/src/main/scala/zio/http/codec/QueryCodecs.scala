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
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[codec] trait QueryCodecs {

  def query(name: String): QueryCodec[String] =
    toSingleValue(HttpCodec.Query(name, TextCodec.string))

  def queryBool(name: String): QueryCodec[Boolean] =
    toSingleValue(HttpCodec.Query(name, TextCodec.boolean))

  def queryInt(name: String): QueryCodec[Int] =
    toSingleValue(HttpCodec.Query(name, TextCodec.int))

  def queryTo[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[A] =
    toSingleValue(HttpCodec.Query(name, codec))

  def queryMultiValue(name: String): QueryCodec[Chunk[String]] =
    HttpCodec.Query(name, TextCodec.string)

  def queryMultiValueBool(name: String): QueryCodec[Chunk[Boolean]] =
    HttpCodec.Query(name, TextCodec.boolean)

  def queryMultiValueInt(name: String): QueryCodec[Chunk[Int]] =
    HttpCodec.Query(name, TextCodec.int)

  def queryMultiValueTo[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[Chunk[A]] =
    HttpCodec.Query(name, codec)

  private def toSingleValue[A](queryCodec: QueryCodec[Chunk[A]]): QueryCodec[A] =
    queryCodec.transform { (c: Chunk[A]) => c.head }(s => NonEmptyChunk(s))

}
