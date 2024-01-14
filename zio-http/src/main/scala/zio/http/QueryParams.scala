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
import java.util

import scala.collection.compat._
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

import zio.{Chunk, IO, NonEmptyChunk, ZIO}

import zio.http.codec.TextCodec
import zio.http.internal.QueryParamEncoding

/**
 * A collection of query parameters.
 */
trait QueryParams {
  self: QueryParams =>

  /**
   * Internal representation of query parameters
   */
  private[http] def seq: Seq[util.Map.Entry[String, util.List[String]]]

  /**
   * All query parameters as a map. Note that by default this method constructs
   * the map on access from the underlying storage implementation, so should be
   * used with care. Prefer to use `getAll` and friends if all you need is to
   * access the values by a known key.
   */
  def map: Map[String, Chunk[String]] = ListMap.from(seq.map { entry =>
    (entry.getKey, Chunk.fromIterable(entry.getValue.asScala))
  })

  /**
   * Combines two collections of query parameters together. If there are
   * duplicate keys, the values from both sides are preserved, in order from
   * left-to-right.
   */
  def ++(that: QueryParams): QueryParams =
    QueryParams.fromEntries(seq ++ that.seq: _*)

  /**
   * Adds the specified key/value pair to the query parameters.
   */
  def add(key: String, value: String): QueryParams =
    addAll(key, Chunk(value))

  /**
   * Adds the specified key/value pairs to the query parameters.
   */
  def addAll(key: String, value: Chunk[String]): QueryParams =
    self ++ QueryParams(key -> value)

  /**
   * Encodes the query parameters into a string.
   */
  def encode: String = encode(Charsets.Utf8)

  /**
   * Encodes the query parameters into a string using the specified charset.
   */
  def encode(charset: Charset): String = QueryParamEncoding.default.encode("", self, charset)

  override def equals(that: Any): Boolean = that match {
    case queryParams: QueryParams => normalize.seq == queryParams.normalize.seq
    case _                        => false
  }

  /**
   * Filters the query parameters using the specified predicate.
   */
  def filter(p: (String, Chunk[String]) => Boolean): QueryParams =
    QueryParams.fromEntries(seq.filter { entry => p(entry.getKey, Chunk.fromIterable(entry.getValue.asScala)) }: _*)

  /**
   * Retrieves all query parameter values having the specified name.
   */
  def getAll(key: String): Option[Chunk[String]] =
    seq.find(_.getKey == key).map(e => Chunk.fromIterable(e.getValue.asScala))

  /**
   * Retrieves all typed query parameter values having the specified name.
   */
  def getAllTo[A](key: String)(implicit codec: TextCodec[A]): Either[QueryParamsError, Chunk[A]] = for {
    params <- getAll(key).toRight(QueryParamsError.Missing(key))
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
  def getAllToZIO[A](key: String)(implicit codec: TextCodec[A]): IO[QueryParamsError, Chunk[A]] =
    ZIO.fromEither(getAllTo[A](key))

  /**
   * Retrieves the first query parameter value having the specified name.
   */
  def get(key: String): Option[String] = getAll(key).flatMap(_.headOption)

  /**
   * Retrieves the first typed query parameter value having the specified name.
   */
  def getTo[A](key: String)(implicit codec: TextCodec[A]): Either[QueryParamsError, A] = for {
    param      <- get(key).toRight(QueryParamsError.Missing(key))
    typedParam <- codec.decode(param).toRight(QueryParamsError.Malformed(key, codec, NonEmptyChunk(param)))
  } yield typedParam

  /**
   * Retrieves the first typed query parameter value having the specified name
   * as ZIO.
   */
  def getToZIO[A](key: String)(implicit codec: TextCodec[A]): IO[QueryParamsError, A] = ZIO.fromEither(getTo[A](key))

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
  def getAllToOrElse[A](key: String, default: => Iterable[A])(implicit codec: TextCodec[A]): Chunk[A] =
    getAllTo[A](key).getOrElse(Chunk.fromIterable(default))

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
  def getToOrElse[A](key: String, default: => A)(implicit codec: TextCodec[A]): A =
    getTo[A](key).getOrElse(default)

  override def hashCode: Int = normalize.seq.hashCode

  /**
   * Determines if the query parameters are empty.
   */
  def isEmpty: Boolean = seq.isEmpty

  /**
   * Determines if the query parameters are non-empty.
   */
  def nonEmpty: Boolean = !isEmpty

  /**
   * Normalizes the query parameters by removing empty keys and values.
   */
  def normalize: QueryParams =
    QueryParams.fromEntries(seq.filter { entry =>
      entry.getKey.nonEmpty && entry.getValue.asScala.nonEmpty
    }: _*)

  /**
   * Removes the specified key from the query parameters.
   */
  def remove(key: String): QueryParams =
    QueryParams.fromEntries(seq.filter { entry => entry.getKey != key }: _*)

  /**
   * Removes the specified keys from the query parameters.
   */
  def removeAll(keys: Iterable[String]): QueryParams = {
    val keysToRemove = keys.toSet
    QueryParams.fromEntries(seq.filterNot { entry => keysToRemove.contains(entry.getKey) }: _*)
  }

  /**
   * Converts the query parameters into a form.
   */
  def toForm: Form = Form.fromQueryParams(self)

}

object QueryParams {
  private final case class JavaLinkedHashMapQueryParams(
    private val underlying: java.util.LinkedHashMap[String, java.util.List[String]],
  ) extends QueryParams {
    override private[http] def seq: Seq[util.Map.Entry[String, util.List[String]]] =
      underlying.entrySet.asScala.toSeq

    /**
     * Retrieves all query parameter values having the specified name. Override
     * takes advantage of LinkedHashMap implementation for O(1) lookup and
     * avoids conversion to Chunk.
     */
    override def getAll(key: String): Option[Chunk[String]] = Option(underlying.get(key))
      .map(_.asScala)
      .map(Chunk.fromIterable)

    /**
     * Determines if the query parameters are empty. Override avoids conversion
     * to Chunk in favor of LinkedHashMap implementation of isEmpty.
     */
    override def isEmpty: Boolean = underlying.isEmpty

  }

  private def javaMapAsLinkedHashMap(
    map: java.util.Map[String, java.util.List[String]],
  ): java.util.LinkedHashMap[String, java.util.List[String]] =
    map match {
      case x: java.util.LinkedHashMap[String, java.util.List[String]] => x
      // This isn't really supposed to happen, Netty constructs LinkedHashMap
      case x                                                          => new java.util.LinkedHashMap(x)
    }

  def apply(map: java.util.Map[String, java.util.List[String]]): QueryParams =
    apply(javaMapAsLinkedHashMap(map))

  def apply(map: java.util.LinkedHashMap[String, java.util.List[String]]): QueryParams =
    JavaLinkedHashMapQueryParams(map)

  def apply(map: Map[String, Chunk[String]]): QueryParams =
    apply(map.toSeq: _*)

  def apply(tuples: (String, Chunk[String])*): QueryParams = {
    val result = new java.util.LinkedHashMap[String, java.util.List[String]]()
    tuples.foreach { case (key, values) =>
      Option(result.get(key)) match {
        case Some(previous) =>
          val combined = Chunk.fromIterable(previous.asScala) ++ values
          result.replace(key, combined.asJava)
        case None           =>
          result.put(key, values.asJava)
      }
    }
    apply(result)
  }

  private[http] def fromEntries(entries: util.Map.Entry[String, util.List[String]]*): QueryParams = {
    val result = new util.LinkedHashMap[String, util.List[String]]()
    entries.foreach { entry =>
      Option(result.get(entry.getKey)) match {
        case Some(previous) =>
          val combined = new util.ArrayList[String]()
          combined.addAll(previous)
          combined.addAll(entry.getValue)
          result.replace(entry.getKey, combined)
        case None           =>
          result.put(entry.getKey, entry.getValue)
      }
    }
    apply(result)
  }

  /**
   * Construct from tuples of k, v with singular v
   */
  def apply(tuple1: (String, String), tuples: (String, String)*): QueryParams =
    apply((tuple1 +: tuples).map { case (k, v) =>
      (k, Chunk(v))
    }: _*)

  /**
   * Decodes the specified string into a collection of query parameters.
   */
  def decode(queryStringFragment: String, charset: Charset = Charsets.Utf8): QueryParams =
    QueryParamEncoding.default.decode(queryStringFragment, charset)

  /**
   * Empty query parameters.
   */
  val empty: QueryParams = JavaLinkedHashMapQueryParams(new java.util.LinkedHashMap[String, java.util.List[String]])

  /**
   * Constructs query parameters from a form.
   */
  def fromForm(form: Form): QueryParams = form.toQueryParams
}
