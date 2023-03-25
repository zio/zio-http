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

import zio.Chunk

import zio.http.internal.QueryParamEncoding

/**
 * A collection of query parameters.
 */
final case class QueryParams private[http] (map: Map[String, Chunk[String]])
    extends scala.collection.Map[String, Chunk[String]] {
  self =>

  override final def -(key: String): QueryParams = QueryParams(map - key)

  override final def -(key1: String, key2: String, keys: String*): QueryParams =
    QueryParams(map.--(Chunk(key1, key2) ++ keys))

  override final def +[V1 >: Chunk[String]](kv: (String, V1)): Map[String, V1] = map.+(kv)

  final def ++(that: QueryParams): QueryParams =
    QueryParams(that.map.foldLeft(map) { case (map, (k, v)) =>
      map.updated(
        k,
        map.get(k) match {
          case Some(v1) => v1 ++ v
          case None     => v
        },
      )
    })

  final def add(key: String, value: String): QueryParams = addAll(key, Chunk(value))

  final def addAll(key: String, value: Chunk[String]): QueryParams = {
    val previousValue = map.get(key)
    val newValue      = previousValue match {
      case Some(prev) => prev ++ value
      case None       => value
    }
    QueryParams(map.updated(key, newValue))
  }

  final def encode: String = QueryParamEncoding.default.encode("", self)

  override def filter(p: ((String, Chunk[String])) => Boolean): QueryParams =
    QueryParams(map.filter(p))

  override final def isEmpty: Boolean = map.isEmpty

  final def toMap: Map[String, Chunk[String]] = map

  override final def get(key: String): Option[Chunk[String]] = map.get(key)

  override final def iterator: Iterator[(String, Chunk[String])] = map.iterator

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

  def decode(queryStringFragment: String): QueryParams =
    QueryParamEncoding.default.decode(queryStringFragment)

  val empty: QueryParams = QueryParams(Map.empty[String, Chunk[String]])

}
