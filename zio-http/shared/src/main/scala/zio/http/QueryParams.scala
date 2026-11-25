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

import zio.http.QueryParams.JavaLinkedHashMapQueryParams
import zio.http.internal._

/**
 * A collection of query parameters. A key can have several values, and the keys
 * keep the order in which they were first added.
 *
 * {{{
 * val params   = QueryParams("page" -> "2", "tag" -> "scala", "tag" -> "http")
 * val tags     = params.getAll("tag") // Chunk(scala, http)
 * val rendered = params.encode // ?page=2&tag=scala&tag=http
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/request/ Request]]
 */
trait QueryParams extends QueryOps[QueryParams] {
  self: QueryParams =>

  /**
   * Encodes the query parameters into a string that starts with `?`, with the
   * pairs joined by `&`. Empty query parameters give the empty string. A key
   * without values is written as `key=`, and a key that is the empty string is
   * left out.
   */
  def encode: String = encode(Charsets.Utf8)

  /**
   * Encodes the query parameters into a string, as `encode` does, using the
   * specified charset.
   */
  def encode(charset: Charset): String =
    QueryParamEncoding.encode(ThreadLocals.stringBuilder, self, charset)

  /**
   * Returns `true` when `that` is a `QueryParams` whose normalized entries are
   * equal, in the same order.
   */
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
   * Retrieves all query parameter values having the specified name. The result
   * is empty when the key is missing, and the key is matched case-sensitively.
   */
  def getAll(key: String): Chunk[String] =
    seq.find(_.getKey == key).map(e => Chunk.fromIterable(e.getValue.asScala)).getOrElse(Chunk.empty)

  /** Returns a hash code that is consistent with `equals`. */
  override def hashCode: Int = normalize.seq.hashCode

  /**
   * Determines if the query parameters are empty. A key that has no values
   * still makes them non-empty.
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
   * Normalizes the query parameters by removing empty keys and blank values. A
   * key that is left without values stays in the result.
   */
  def normalize: QueryParams =
    self match {
      case _ if self.isEmpty                        => self
      case JavaLinkedHashMapQueryParams(underlying) =>
        var needsNormalization = false
        val it                 = underlying.entrySet().iterator()
        while (!needsNormalization && it.hasNext) {
          val entry = it.next()
          if (entry.getKey.isEmpty || entry.getValue == null) {
            needsNormalization = true
          } else {
            var i = 0
            while (i < entry.getValue.size()) {
              if (entry.getValue.get(i).isBlank) {
                needsNormalization = true
              }
              i += 1
            }
          }
        }
        if (!needsNormalization) self
        else {
          val normalized = new java.util.LinkedHashMap[String, java.util.List[String]]()
          var i          = 0
          while (i < seq.length) {
            val entry = seq(i)
            if (entry.getKey.nonEmpty) {
              normalized.put(entry.getKey, entry.getValue.asScala.filterNot(_.isBlank).asJava)
            }
            i += 1
          }
          JavaLinkedHashMapQueryParams(normalized)
        }
      case _                                        =>
        val seq                = self.seq
        val it                 = seq.iterator
        var needsNormalization = false
        while (it.hasNext && !needsNormalization) {
          val entry = it.next()
          if (entry.getKey.isEmpty || entry.getValue == null || entry.getValue.isEmpty) {
            needsNormalization = true
          } else {
            var i = 0
            while (i < entry.getValue.size()) {
              if (entry.getValue.get(i).isBlank) {
                needsNormalization = true
              }
              i += 1
            }
          }
        }
        if (!needsNormalization) self
        else {
          val normalized = new java.util.LinkedHashMap[String, java.util.List[String]]()
          var i          = 0
          while (i < seq.length) {
            val entry = seq(i)
            if (entry.getKey.nonEmpty) {
              normalized.put(entry.getKey, entry.getValue.asScala.filterNot(_.isBlank).asJava)
            }
            i += 1
          }
          JavaLinkedHashMapQueryParams(normalized)
        }

    }

  /** Returns these query parameters. */
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

/**
 * Constructors and codecs for `QueryParams`.
 */
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
      else if (jList.size() == 1) Chunk.single(jList.get(0))
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

  /**
   * Creates query parameters from a Java map of keys to lists of values. A map
   * that is not a `java.util.LinkedHashMap` is copied into one.
   */
  def apply(map: java.util.Map[String, java.util.List[String]]): QueryParams =
    apply(javaMapAsLinkedHashMap(map))

  /** Creates query parameters that use the given map as their storage. */
  def apply(map: java.util.LinkedHashMap[String, java.util.List[String]]): QueryParams =
    JavaLinkedHashMapQueryParams(map)

  /** Creates query parameters from a map of keys to values. */
  def apply(map: Map[String, Chunk[String]]): QueryParams =
    apply(map.toSeq: _*)

  /**
   * Creates query parameters from pairs of a key and its values. When a key
   * appears more than once, its values are appended.
   */
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
    if (entries.isEmpty) QueryParams.empty
    else {
      val result    = new util.LinkedHashMap[String, util.List[String]]()
      val entriesIt = entries.iterator
      while (entriesIt.hasNext) {
        val entry = entriesIt.next()
        result.get(entry.getKey) match {
          case previous if previous != null =>
            val combined = new util.ArrayList[String](previous.size() + entry.getValue.size())
            combined.addAll(previous)
            combined.addAll(entry.getValue)
            result.replace(entry.getKey, combined)
          case _                            =>
            result.put(entry.getKey, entry.getValue)
        }
      }
      apply(result)
    }

  }

  /**
   *    * Construct from tuples of k, v with singular v. A blank value (empty or
   * only whitespace) adds no value to its key. When the key already has values,
   * a blank value removes them, so `QueryParams("a" -> "1", "a" -> "")` has no
   * values for `a`.
   */
  def apply(tuple1: (String, String), tuples: (String, String)*): QueryParams = {
    val entries = new java.util.LinkedHashMap[String, java.util.List[String]]()
    val values  = new util.ArrayList[String](1)
    if (!tuple1._2.isBlank) {
      values.add(tuple1._2)
    }
    entries.put(tuple1._1, values)
    if (tuples.nonEmpty) {
      val it = tuples.iterator
      while (it.hasNext) {
        val (key, value) = it.next()
        val entry        = entries.get(key)
        if (entry != null && !value.isBlank) {
          entry.add(value)
        } else {
          val newValues = new util.ArrayList[String](1)
          if (!value.isBlank) {
            newValues.add(value)
          }
          entries.put(key, newValues)
        }
      }
    }
    JavaLinkedHashMapQueryParams(entries)
  }

  /**
   * Decodes the specified string into a collection of query parameters. A
   * leading `?` is skipped. The pairs are separated by `&`, and a key without a `=` or with an empty value
   * gets the empty string as its value. A pair with an empty key, as in `=1`,
   * uses its value as the key, with the empty string as its value. A `null` string gives
   * empty query parameters. The charset is used to decode the text of the keys
   * and values.
   */
  def decode(queryStringFragment: String, charset: Charset = Charsets.Utf8): QueryParams =
    QueryParamEncoding.decode(queryStringFragment, charset)

  /**
   * Empty query parameters.
   */
  val empty: QueryParams = JavaLinkedHashMapQueryParams(new java.util.LinkedHashMap[String, java.util.List[String]])

  /**
   * Constructs query parameters from a form.
   */
  def fromForm(form: Form): QueryParams = form.toQueryParams
}
