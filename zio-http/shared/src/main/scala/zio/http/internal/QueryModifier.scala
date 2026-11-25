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

package zio.http.internal

import zio.Chunk

import zio.schema.Schema

import zio.http.QueryParams

/** Operators that return a copy with changed query parameters. */
trait QueryModifier[+A] { self: QueryOps[A] with A =>

  /**
   * Combines two collections of query parameters together. If there are
   * duplicate keys, the values from both sides are preserved, in order from
   * left-to-right.
   */
  def ++(that: QueryParams): A =
    updateQueryParams(params => QueryParams.fromEntries(params.seq ++ that.seq: _*))

  /**
   * Adds the specified key/value pair to the query parameters.
   */
  def addQueryParam(key: String, value: String): A =
    if (value.isBlank) addQueryParam(key)
    else addQueryParams(key, Chunk.single(value))

  /** Adds the key with no values. */
  def addQueryParam(key: String): A =
    addQueryParams(key, Chunk.empty)

  /**
   * Adds the specified key/value pairs to the query parameters.
   */
  def addQueryParams(key: String, value: Chunk[String]): A =
    updateQueryParams(params => params ++ QueryParams(key -> value))

  /** Adds the parameters parsed from a query string such as `a=1&b=2`. */
  def addQueryParams(values: String): A =
    updateQueryParams(params => params ++ QueryParams.decode(values))

  /**
   * Adds the key and value pairs; pairs with the same key become one entry with
   * their values in order.
   */
  def addQueryParams(queryParams: Iterable[(String, String)]): A =
    updateQueryParams(params =>
      params ++ QueryParams(queryParams.groupBy(_._1).map { case (k, v) =>
        k -> Chunk.fromIterable(v).map(_._2)
      }),
    )

  /** Adds `value`, encoded with its schema, under `key`. */
  def addQueryParam[T](key: String, value: T)(implicit schema: Schema[T]): A =
    updateQueryParams(StringSchemaCodec.queryFromSchema(schema, ErrorConstructor.query, key).encode(value, _))

  /**
   * Adds the fields of `value` as query parameters named by field, encoded with
   * its schema.
   */
  def addQueryParam[T](value: T)(implicit schema: Schema[T]): A =
    updateQueryParams(StringSchemaCodec.queryFromSchema(schema, ErrorConstructor.query, null).encode(value, _))

  /**
   * Removes the specified key from the query parameters.
   */
  def removeQueryParam(key: String): A =
    updateQueryParams { params =>
      QueryParams.fromEntries(params.seq.filter { entry => entry.getKey != key }: _*)
    }

  /**
   * Removes the specified keys from the query parameters.
   */
  def removeQueryParams(keys: Iterable[String]): A = updateQueryParams { params =>
    val keysToRemove = keys.toSet
    QueryParams.fromEntries(params.seq.filterNot { entry => keysToRemove.contains(entry.getKey) }: _*)
  }

  /** Replaces all query parameters with `values`. */
  def setQueryParams(values: QueryParams): A =
    updateQueryParams(_ => values)

  /** Replaces all query parameters with those parsed from a query string. */
  def setQueryParams(values: String): A =
    updateQueryParams(_ => QueryParams.decode(values))

  /** Replaces all query parameters with the entries of the map. */
  def setQueryParams(queryParams: Map[String, Chunk[String]]): A =
    updateQueryParams(_ => QueryParams(queryParams))

  /** Replaces all query parameters with the given entries. */
  def setQueryParams(queryParams: (String, Chunk[String])*): A =
    updateQueryParams(_ => QueryParams(queryParams: _*))

  /**
   * Returns a copy whose query parameters are the result of applying `f` to the
   * current ones.
   */
  def updateQueryParams(f: QueryParams => QueryParams): A
}
