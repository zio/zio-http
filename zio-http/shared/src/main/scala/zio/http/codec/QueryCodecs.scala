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
import zio.NonEmptyChunk
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[codec] trait QueryCodecs {

  def query(name: String): QueryCodec[String] =
    HttpCodec
      .Query(name, TextCodec.string)
      .transform[String] { (c: NonEmptyChunk[String]) => c.head }(s => NonEmptyChunk(s))

  def queryBool(name: String): QueryCodec[Boolean] =
    HttpCodec
      .Query(name, TextCodec.boolean)
      .transform { (c: NonEmptyChunk[Boolean]) => c.head }(s => NonEmptyChunk(s))

  def queryInt(name: String): QueryCodec[Int] =
    HttpCodec
      .Query(name, TextCodec.int)
      .transform { (c: NonEmptyChunk[Int]) => c.head }(s => NonEmptyChunk(s))

  def queryTo[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[A] =
    HttpCodec
      .Query(name, codec)
      .transform { (c: NonEmptyChunk[A]) => c.head }(s => NonEmptyChunk(s))

  def queryMultiValue(name: String): QueryCodec[NonEmptyChunk[String]] =
    HttpCodec.Query(name, TextCodec.string)

  def queryMultiValueBool(name: String): QueryCodec[NonEmptyChunk[Boolean]] =
    HttpCodec.Query(name, TextCodec.boolean)

  def queryMultiValueInt(name: String): QueryCodec[NonEmptyChunk[Int]] =
    HttpCodec.Query(name, TextCodec.int)

  def queryMultiValueTo[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[NonEmptyChunk[A]] =
    HttpCodec.Query(name, codec)

}
