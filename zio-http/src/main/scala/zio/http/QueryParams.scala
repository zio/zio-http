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

import scala.collection.immutable.ListMap

import zio.{Chunk, IO, NonEmptyChunk, ZIO}

import zio.http.codec.TextCodec
import zio.http.internal.QueryParamEncoding

trait QueryParams {
  self: QueryParams =>

  /**
   * All query parameters as a map
   */
  def map: Map[String, Chunk[String]]

  /**
   * Combines two collections of query parameters together. If there are
   * duplicate keys, the values from both sides are preserved, in order from
   * left-to-right.
   */
  def ++(that: QueryParams): QueryParams

  /**
   * Adds the specified key/value pair to the query parameters.
   */
  def add(key: String, value: String): QueryParams

  /**
   * Adds the specified key/value pairs to the query parameters.
   */
  def addAll(key: String, value: Chunk[String]): QueryParams

  /**
   * Encodes the query parameters into a string.
   */
  def encode: String

  /**
   * Encodes the query parameters into a string using the specified charset.
   */
  def encode(charset: Charset): String

  def equals(that: Any): Boolean

  /**
   * Filters the query parameters using the specified predicate.
   */
  def filter(p: (String, Chunk[String]) => Boolean): QueryParams

  /**
   * Retrieves all query parameter values having the specified name.
   */
  def getAll(key: String): Option[Chunk[String]]

  /**
   * Retrieves all typed query parameter values having the specified name.
   */
  def getAllAs[A](key: String)(implicit codec: TextCodec[A]): Either[QueryParamsError, Chunk[A]]

  /**
   * Retrieves all typed query parameter values having the specified name as
   * ZIO.
   */
  def getAllAsZIO[A](key: String)(implicit codec: TextCodec[A]): IO[QueryParamsError, Chunk[A]]

  /**
   * Retrieves the first query parameter value having the specified name.
   */
  def get(key: String): Option[String]

  /**
   * Retrieves the first typed query parameter value having the specified name.
   */
  def getAs[A](key: String)(implicit codec: TextCodec[A]): Either[QueryParamsError, A]

  /**
   * Retrieves the first typed query parameter value having the specified name
   * as ZIO.
   */
  def getAsZIO[A](key: String)(implicit codec: TextCodec[A]): IO[QueryParamsError, A]

  /**
   * Retrieves all query parameter values having the specified name, or else
   * uses the default iterable.
   */
  def getAllOrElse(key: String, default: => Iterable[String]): Chunk[String]

  /**
   * Retrieves all query parameter values having the specified name, or else
   * uses the default iterable.
   */
  def getAllAsOrElse[A](key: String, default: => Iterable[A])(implicit codec: TextCodec[A]): Chunk[A]

  /**
   * Retrieves the first query parameter value having the specified name, or
   * else uses the default value.
   */
  def getOrElse(key: String, default: => String): String

  /**
   * Retrieves the first typed query parameter value having the specified name,
   * or else uses the default value.
   */
  def getAsOrElse[A](key: String, default: => A)(implicit codec: TextCodec[A]): A

  def hashCode: Int

  /**
   * Determines if the query parameters are empty.
   */
  def isEmpty: Boolean

  /**
   * Determines if the query parameters are non-empty.
   */
  def nonEmpty: Boolean

  /**
   * Normalizes the query parameters by removing empty keys and values.
   */
  def normalize: QueryParams

  /**
   * Removes the specified key from the query parameters.
   */
  def remove(key: String): QueryParams

  /**
   * Removes the specified keys from the query parameters.
   */
  def removeAll(keys: Iterable[String]): QueryParams

  /**
   * Converts the query parameters into a form.
   */
  def toForm: Form

}

/**
 * A collection of query parameters.
 */
final case class ListMapQueryParams(map: ListMap[String, Chunk[String]]) extends QueryParams {
  self =>

  /**
   * Combines two collections of query parameters together. If there are
   * duplicate keys, the values from both sides are preserved, in order from
   * left-to-right.
   */
  override def ++(that: QueryParams): QueryParams =
    ListMapQueryParams(this.map.foldLeft(ListMap.from(that.map)) { case (map, (k, v2)) =>
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
  override def add(key: String, value: String): ListMapQueryParams = addAll(key, Chunk(value))

  /**
   * Adds the specified key/value pairs to the query parameters.
   */
  override def addAll(key: String, value: Chunk[String]): ListMapQueryParams = {
    val previousValue = map.get(key)
    val newValue      = previousValue match {
      case Some(prev) => prev ++ value
      case None       => value
    }
    ListMapQueryParams(map.updated(key, newValue))
  }

  /**
   * Encodes the query parameters into a string.
   */
  override def encode: String = encode(Charsets.Utf8)

  /**
   * Encodes the query parameters into a string using the specified charset.
   */
  override def encode(charset: Charset): String = QueryParamEncoding.default.encode("", self, charset)

  override def equals(that: Any): Boolean = that match {
    case that: ListMapQueryParams => self.normalize.map == that.normalize.map
    case _                        => false
  }

  /**
   * Filters the query parameters using the specified predicate.
   */
  override def filter(p: (String, Chunk[String]) => Boolean): ListMapQueryParams =
    ListMapQueryParams(map.filter(p.tupled))

  /**
   * Retrieves all query parameter values having the specified name.
   */
  override def getAll(key: String): Option[Chunk[String]] = map.get(key)

  /**
   * Retrieves all typed query parameter values having the specified name.
   */
  override def getAllAs[A](key: String)(implicit codec: TextCodec[A]): Either[QueryParamsError, Chunk[A]] = for {
    params <- map.get(key).toRight(QueryParamsError.Missing(key))
    (failed, typed) = params.partitionMap(p => codec.decode(p).toRight(p))
    result <- NonEmptyChunk
      .fromChunk(failed)
      .map(fails => QueryParamsError.Malformed(key, codec, fails))
      .toLeft(typed)
  } yield result

  /**
   * Retrieves all typed query parameter values having the specified name as
   * ZIO.
   */
  override def getAllAsZIO[A](key: String)(implicit codec: TextCodec[A]): IO[QueryParamsError, Chunk[A]] =
    ZIO.fromEither(getAllAs[A](key))

  /**
   * Retrieves the first query parameter value having the specified name.
   */
  override def get(key: String): Option[String] = getAll(key).flatMap(_.headOption)

  /**
   * Retrieves the first typed query parameter value having the specified name.
   */
  override def getAs[A](key: String)(implicit codec: TextCodec[A]): Either[QueryParamsError, A] = for {
    param      <- get(key).toRight(QueryParamsError.Missing(key))
    typedParam <- codec.decode(param).toRight(QueryParamsError.Malformed(key, codec, NonEmptyChunk(param)))
  } yield typedParam

  /**
   * Retrieves the first typed query parameter value having the specified name
   * as ZIO.
   */
  override def getAsZIO[A](key: String)(implicit codec: TextCodec[A]): IO[QueryParamsError, A] =
    ZIO.fromEither(getAs[A](key))

  /**
   * Retrieves all query parameter values having the specified name, or else
   * uses the default iterable.
   */
  override def getAllOrElse(key: String, default: => Iterable[String]): Chunk[String] =
    getAll(key).getOrElse(Chunk.fromIterable(default))

  /**
   * Retrieves all query parameter values having the specified name, or else
   * uses the default iterable.
   */
  override def getAllAsOrElse[A](key: String, default: => Iterable[A])(implicit codec: TextCodec[A]): Chunk[A] =
    getAllAs[A](key).getOrElse(Chunk.fromIterable(default))

  /**
   * Retrieves the first query parameter value having the specified name, or
   * else uses the default value.
   */
  override def getOrElse(key: String, default: => String): String =
    get(key).getOrElse(default)

  /**
   * Retrieves the first typed query parameter value having the specified name,
   * or else uses the default value.
   */
  override def getAsOrElse[A](key: String, default: => A)(implicit codec: TextCodec[A]): A =
    getAs[A](key).getOrElse(default)

  override def hashCode: Int = normalize.map.hashCode

  /**
   * Determines if the query parameters are empty.
   */
  override def isEmpty: Boolean = map.isEmpty

  /**
   * Determines if the query parameters are non-empty.
   */
  override def nonEmpty: Boolean = map.nonEmpty

  /**
   * Normalizes the query parameters by removing empty keys and values.
   */
  override def normalize: ListMapQueryParams =
    if (isEmpty) self
    else ListMapQueryParams(map.filter(i => i._1.nonEmpty && i._2.nonEmpty))

  /**
   * Removes the specified key from the query parameters.
   */
  override def remove(key: String): ListMapQueryParams = ListMapQueryParams(map - key)

  /**
   * Removes the specified keys from the query parameters.
   */
  override def removeAll(keys: Iterable[String]): ListMapQueryParams = ListMapQueryParams(map -- keys)

  /**
   * Converts the query parameters into a form.
   */
  override def toForm: Form = Form.fromQueryParams(self)
}

object QueryParams {

  def apply(map: Map[String, Seq[String]]): ListMapQueryParams =
    ListMapQueryParams(map = ListMap(map.toSeq.map { case (key, value) => key -> Chunk.fromIterable(value) }: _*))

  def apply(tuples: (String, Chunk[String])*): ListMapQueryParams = {
    var result = ListMap.empty[String, Chunk[String]]
    tuples.foreach { case (key, values) =>
      result.get(key) match {
        case Some(previous) =>
          result = result.updated(key, previous ++ values)
        case None           =>
          result = result.updated(key, values)
      }
    }
    ListMapQueryParams(map = result)
  }

  def apply(tuple1: (String, String), tuples: (String, String)*): ListMapQueryParams =
    ListMapQueryParams(map = ListMap.from(Chunk.fromIterable(tuple1 +: tuples).groupBy(_._1).map { case (key, values) =>
      key -> values.map(_._2)
    }))

  /**
   * Decodes the specified string into a collection of query parameters.
   */
  def decode(queryStringFragment: String, charset: Charset = Charsets.Utf8): QueryParams =
    QueryParamEncoding.default.decode(queryStringFragment, charset)

  /**
   * Empty query parameters.
   */
  val empty: ListMapQueryParams = ListMapQueryParams(ListMap.empty[String, Chunk[String]])

  /**
   * Constructs query parameters from a form.
   */
  def fromForm(form: Form): QueryParams = form.toQueryParams
}
