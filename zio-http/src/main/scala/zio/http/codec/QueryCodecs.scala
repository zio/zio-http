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
import zio.stacktracer.TracingImplicits.disableAutoTrace
private[codec] trait QueryCodecs {
  def query(name: String): QueryCodec[String] =
    HttpCodec.Query(name, TextCodec.string)

  def queryBool(name: String): QueryCodec[Boolean] =
    HttpCodec.Query(name, TextCodec.boolean)

  def queryInt(name: String): QueryCodec[Int] =
    HttpCodec.Query(name, TextCodec.int)

  def queryAs[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[A] =
    HttpCodec.Query(name, codec)

  def paramStr(name: String): QueryCodec[String] =
    HttpCodec.Query(name, TextCodec.string)

  def paramBool(name: String): QueryCodec[Boolean] =
    HttpCodec.Query(name, TextCodec.boolean)

  def paramInt(name: String): QueryCodec[Int] =
    HttpCodec.Query(name, TextCodec.int)

  def paramAs[A](name: String)(implicit codec: TextCodec[A]): QueryCodec[A] =
    HttpCodec.Query(name, codec)

}
