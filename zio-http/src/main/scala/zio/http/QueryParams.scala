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
import zio.{Chunk, NonEmptyChunk}
import zio.http.Charsets
import zio.http.internal.QueryParamEncoding

import scala.jdk.CollectionConverters.{CollectionHasAsScala, MapHasAsScala}

/**
 * A collection of query parameters.
 */
final case class QueryParams(map: Map[String, NonEmptyChunk[String]]) { self =>

  /**
   * Combines two collections of query parameters together. If there are
   * duplicate keys, the values from both sides are preserved, in order from
   * left-to-right.
   */
  def ++(that: QueryParams): QueryParams =
    QueryParams(that.map.foldLeft(map) { case (map, (k, v2)) =>
      map.updated(
        k,
        map.get(k) match {
          case Some(v1) => v1 ++ v2
          case None     => v2
        },
      )
    })

  /**
   * Adds the specified key/value pair to the query parameters.
   */
  def add(key: String, value: String): QueryParams = addAll(key, NonEmptyChunk.single(value))

  /**
   * Adds the specified key/value pairs to the query parameters.
   */
  def addAll(key: String, value: NonEmptyChunk[String]): QueryParams = {
    val previousValue = map.get(key)
    val newValue      = previousValue match {
      case Some(prev) => prev ++ value
      case None       => value
    }
    QueryParams(map.updated(key, newValue))
  }

  /**
   * Encodes the query parameters into a string.
   */
  def encode: String = encode(Charsets.Utf8)

  /**
   * Encodes the query parameters into a string using the specified charset.
   */
  def encode(charset: Charset): String = QueryParamEncoding.default.encode("", self, charset)

  override def equals(that: Any): Boolean = that match {
    case that: QueryParams => self.normalize.map == that.normalize.map
    case _                 => false
  }

  /**
   * Filters the query parameters using the specified predicate.
   */
  def filter(p: (String, NonEmptyChunk[String]) => Boolean): QueryParams =
    QueryParams(map.filter(p.tupled))

  /**
   * Retrieves the query parameter values having the specified name.
   */
  def get(key: String): Option[NonEmptyChunk[String]] = map.get(key)

  /**
   * Retrieves the query parameter value having the specified name, or else uses
   * the default iterable.
   */
  def getOrElse(key: String, default: => NonEmptyChunk[String]): NonEmptyChunk[String] =
    map.getOrElse(key, default)

  override def hashCode: Int = normalize.map.hashCode

  /**
   * Determines if the query parameters are empty.
   */
  def isEmpty: Boolean = map.isEmpty

  /**
   * Determines if the query parameters are non-empty.
   */
  def nonEmpty: Boolean = map.nonEmpty

  /**
   * Normalizes the query parameters by removing empty keys and values.
   */
  def normalize: QueryParams =
    if (isEmpty) self
    else QueryParams(map.filter(i => i._1.nonEmpty && i._2.nonEmpty))

  /**
   * Removes the specified key from the query parameters.
   */
  def remove(key: String): QueryParams = QueryParams(map - key)

  /**
   * Removes the specified keys from the query parameters.
   */
  def removeAll(keys: Iterable[String]): QueryParams = QueryParams(map -- keys)

  /**
   * Converts the query parameters into a form.
   */
  def toForm: Form = Form.fromQueryParams(self)
}

object QueryParams {

  def apply(tuples: (String, NonEmptyChunk[String])*): QueryParams =
    QueryParams(map = tuples.groupBy(_._1).flatMap { case (key, values) =>
      NonEmptyChunk
        .fromIterableOption(values)
        .map(_.flatMap(_._2))
        .map(key -> _)
    })

  def apply(tuple1: (String, String), tuples: (String, String)*): QueryParams =
    QueryParams((tuple1 +: tuples).map { case (k, v) => k -> NonEmptyChunk.single(v) }: _*)

  /**
   * Decodes the specified string into a collection of query parameters.
   */
  def decode(queryStringFragment: String, charset: Charset = Charsets.Utf8): QueryParams =
    QueryParamEncoding.default.decode(queryStringFragment, charset)

  /**
   * Empty query parameters.
   */
  val empty: QueryParams = QueryParams(Map.empty[String, NonEmptyChunk[String]])

  /**
   * Constructs query parameters from a form.
   */
  def fromForm(form: Form): QueryParams = form.toQueryParams

  def fromJava(map: java.util.Map[String, java.util.List[String]]): QueryParams =
    QueryParams(map.asScala.view.flatMap { case (k, v) =>
      NonEmptyChunk.fromIterableOption(v.asScala).map(k -> _)
    }.toMap)
}
