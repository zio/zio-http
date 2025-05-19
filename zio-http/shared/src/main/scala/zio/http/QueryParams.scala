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

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

import zio._

import zio.http.internal._

/**
 * A collection of query parameters.
 */
trait QueryParams extends QueryOps[QueryParams] {
  self: QueryParams =>

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

  def getAll(key: String): Chunk[String] =
    seq.find(_.getKey == key).map(e => Chunk.fromIterable(e.getValue.asScala)).getOrElse(Chunk.empty)

  /**
   * Retrieves all query parameter values having the specified name.
   */

  override def hashCode: Int = normalize.seq.hashCode

  /**
   * Determines if the query parameters are empty.
   */
  def isEmpty: Boolean = seq.isEmpty

  /**
   * All query parameters as a map. Note that by default this method constructs
   * the map on access from the underlying storage implementation, so should be
   * used with care. Prefer to use `getAll` and friends if all you need is to
   * access the values by a known key.
   */
  def map: Map[String, Chunk[String]] = ListMap(seq.map { entry =>
    (entry.getKey, Chunk.fromIterable(entry.getValue.asScala))
  }: _*)

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

  override def queryParameters: QueryParams =
    self

  /**
   * Internal representation of query parameters
   */
  private[http] def seq: Seq[util.Map.Entry[String, util.List[String]]]

  /**
   * Converts the query parameters into a form.
   */
  def toForm: Form = Form.fromQueryParams(self)

}

object QueryParams {
  private final case class JavaLinkedHashMapQueryParams(
    private val underlying: java.util.LinkedHashMap[String, java.util.List[String]],
  ) extends QueryParams { self =>
    override private[http] def seq: Seq[util.Map.Entry[String, util.List[String]]] =
      underlying.entrySet.asScala.toSeq

    /**
     * Determines if the query parameters are empty. Override avoids conversion
     * to Chunk in favor of LinkedHashMap implementation of isEmpty.
     */
    override def isEmpty: Boolean = underlying.isEmpty

    /**
     * Retrieves all query parameter values having the specified name. Override
     * takes advantage of LinkedHashMap implementation for O(1) lookup and
     * avoids conversion to Chunk.
     */
    override def getAll(key: String): Chunk[String] = {
      val jList = underlying.get(key)
      if (jList eq null) Chunk.empty
      else Chunk.fromJavaIterable(jList)
    }

    override def hasQueryParam(name: CharSequence): Boolean =
      underlying.containsKey(name.toString)

    override def updateQueryParams(f: QueryParams => QueryParams): QueryParams =
      f(self)

    override private[http] def unsafeQueryParam(key: String): String = {
      val value = underlying.get(key)
      if (value == null || value.isEmpty) null else value.get(0)
    }

    override def valueCount(name: CharSequence): Int =
      if (underlying.containsKey(name)) underlying.get(name.toString).size() else 0
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
