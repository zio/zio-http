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

package zio.http

import java.nio.charset.Charset

import zio.Chunk

import zio.http.Charsets
import zio.http.forms.Form
import zio.http.internal.QueryParamEncoding

/**
 * A collection of query parameters.
 */
final case class QueryParams(map: Map[String, Chunk[String]]) {
  self =>

  def ++(that: QueryParams): QueryParams =
    QueryParams(that.map.foldLeft(map) { case (map, (k, v)) =>
      map.updated(
        k,
        map.get(k) match {
          case Some(v1) => v1 ++ v
          case None     => v
        },
      )
    })

  def add(key: String, value: String): QueryParams = addAll(key, Chunk(value))

  def addAll(key: String, value: Chunk[String]): QueryParams = {
    val previousValue = map.get(key)
    val newValue      = previousValue match {
      case Some(prev) => prev ++ value
      case None       => value
    }
    QueryParams(map.updated(key, newValue))
  }

  def encode: String = encode(Charsets.Utf8)

  def encode(charset: Charset): String = QueryParamEncoding.default.encode("", self, charset)

  override def equals(that: Any): Boolean = that match {
    case that: QueryParams => self.normalize.map == that.normalize.map
    case _                 => false
  }

  def filter(p: ((String, Chunk[String])) => Boolean): QueryParams =
    QueryParams(map.filter(p))

  def get(key: String): Option[Chunk[String]] = map.get(key)

  def getOrElse(key: String, default: => Iterable[String]): Chunk[String] =
    map.getOrElse(key, Chunk.fromIterable(default))

  override def hashCode: Int = normalize.map.hashCode

  def isEmpty: Boolean = map.isEmpty

  def nonEmpty: Boolean = map.nonEmpty

  def normalize: QueryParams =
    if (isEmpty) self
    else QueryParams(map.filter(i => i._1.nonEmpty && i._2.nonEmpty))

  def remove(key: String): QueryParams = QueryParams(map - key)

  def removeAll(keys: Iterable[String]): QueryParams = QueryParams(map -- keys)

  def toForm: Form = Form.fromQueryParams(self)
}

object QueryParams {

  def apply(tuples: (String, Chunk[String])*): QueryParams =
    QueryParams(map = Chunk.fromIterable(tuples).groupBy(_._1).map { case (key, values) =>
      key -> values.flatMap(_._2)
    })

  def apply(tuple1: (String, String), tuples: (String, String)*): QueryParams =
    QueryParams(map = Chunk.fromIterable(tuple1 +: tuples.toVector).groupBy(_._1).map { case (key, values) =>
      key -> values.map(_._2)
    })

  def decode(queryStringFragment: String, charset: Charset = Charsets.Utf8): QueryParams =
    QueryParamEncoding.default.decode(queryStringFragment, charset)

  val empty: QueryParams = QueryParams(Map.empty[String, Chunk[String]])

  def fromForm(form: Form): QueryParams = form.toQueryParams
}
