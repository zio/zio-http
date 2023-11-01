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
import zio.http.codec.TextCodec
import zio.http.internal.QueryParamEncoding

/**
 * A collection of query parameters.
 */
final case class QueryParams(map: Map[String, Chunk[String]]) {
  self =>

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
  def add(key: String, value: String): QueryParams = addAll(key, Chunk(value))

  /**
   * Adds the specified key/value pairs to the query parameters.
   */
  def addAll(key: String, value: Chunk[String]): QueryParams = {
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
  def filter(p: (String, Chunk[String]) => Boolean): QueryParams =
    QueryParams(map.filter(p.tupled))

  /**
   * Retrieves all query parameter values having the specified name.
   */
  def getAll(key: String): Option[Chunk[String]] = map.get(key)

  /**
   * Retrieves all typed query parameter values having the specified name.
   */
  def getAllAs[A](key: String)(implicit codec: TextCodec[A]): Option[Chunk[A]] =
    map.get(key).map(_.flatMap(codec.decode))

  /**
   * Retrieves the first query parameter value having the specified name.
   */
  def get(key: String): Option[String] = getAll(key).flatMap(_.headOption)

  /**
   * Retrieves the first typed query parameter value having the specified name.
   */
  def getAs[A](key: String)(implicit codec: TextCodec[A]): Option[A] = get(key).flatMap(codec.decode)

  /**
   * Retrieves all query parameter values having the specified name, or else
   * uses the default iterable.
   */
  def getAllOrElse(key: String, default: => Iterable[String]): Chunk[String] =
    getAll(key).getOrElse(Chunk.fromIterable(default))

  /**
   * Retrieves all query parameter values having the specified name, or else
   * uses the default iterable.
   */
  def getAllAsOrElse[A](key: String, default: => Iterable[A])(implicit codec: TextCodec[A]): Chunk[A] =
    getAllAs[A](key).getOrElse(Chunk.fromIterable(default))

  /**
   * Retrieves the first query parameter value having the specified name, or
   * else uses the default value.
   */
  def getOrElse(key: String, default: => String): String =
    get(key).getOrElse(default)

  /**
   * Retrieves the first typed query parameter value having the specified name,
   * or else uses the default value.
   */
  def getAsOrElse[A](key: String, default: => A)(implicit codec: TextCodec[A]): A =
    getAs[A](key).getOrElse(default)

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

  def apply(tuples: (String, Chunk[String])*): QueryParams =
    QueryParams(map = Chunk.fromIterable(tuples).groupBy(_._1).map { case (key, values) =>
      key -> values.flatMap(_._2)
    })

  def apply(tuple1: (String, String), tuples: (String, String)*): QueryParams =
    QueryParams(map = Chunk.fromIterable(tuple1 +: tuples.toVector).groupBy(_._1).map { case (key, values) =>
      key -> values.map(_._2)
    })

  /**
   * Decodes the specified string into a collection of query parameters.
   */
  def decode(queryStringFragment: String, charset: Charset = Charsets.Utf8): QueryParams =
    QueryParamEncoding.default.decode(queryStringFragment, charset)

  /**
   * Empty query parameters.
   */
  val empty: QueryParams = QueryParams(Map.empty[String, Chunk[String]])

  /**
   * Constructs query parameters from a form.
   */
  def fromForm(form: Form): QueryParams = form.toQueryParams
}
