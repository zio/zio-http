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

import zio.{Chunk, Trace}

import zio.http.internal.{CaseMode, CharSequenceExtensions, HeaderOps}

/**
 * Represents an immutable collection of headers. It is an `Iterable[Header]`
 * that also has operators to read the headers by name or by header type, and
 * the operators defined here to combine and modify headers. Header names are
 * matched ignoring case.
 *
 * {{{
 * val headers = Headers("X-Request-Id" -> "42") ++ Headers(Header.ContentLength(3))
 * val id      = headers.get("x-request-id") // Some(42)
 * val length  = headers.get(Header.ContentLength) // Some(ContentLength(3))
 * }}}
 *
 * NOTE: Generic operators that are not specific to `Headers` should not be
 * defined here. A better place would be one of the traits extended by
 * `HeaderOps`.
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/headers/ Headers]]
 */
sealed trait Headers extends HeaderOps[Headers] with Iterable[Header] {
  self =>

  /**
   * Returns the headers of this collection followed by the headers of `other`.
   * It is the same as `combine`.
   */
  final def ++(other: Headers): Headers = self.combine(other)

  /**
   * Returns the headers of this collection followed by the headers of `other`.
   * Headers with the same name are kept. When both collections have a header
   * with a given name, `get` returns the value from `other`.
   */
  final def combine(other: Headers): Headers =
    Headers.Concat(self, other)

  /**
   * Returns the result of `this ++ other` when `cond` is `true`, and this
   * collection otherwise.
   */
  final def combineIf(cond: Boolean)(other: Headers): Headers =
    if (cond) self ++ other else self

  /**
   * Returns `true` when a header with the given name exists. The name is
   * matched ignoring case.
   */
  def contains(key: CharSequence): Boolean

  /**
   * Returns the value of the first header with the given name, matched ignoring
   * case, or `None` when there is none. For `Headers` built with `++`, a value
   * from the right operand is preferred.
   */
  final def get(key: CharSequence): Option[String] = Option(getUnsafe(key))

  /**
   * Returns the value of the header with the name of the given type, found as
   * `get(key)` finds it, parsed as that type, or `None` when there is none or
   * it cannot be parsed.
   */
  final def get(headerType: Header.HeaderType): Option[headerType.HeaderValue] = header(headerType)

  /**
   * Returns the parsed values of all headers with the name of the given type,
   * in order. A header that cannot be parsed is skipped.
   */
  final def getAll(headerType: Header.HeaderType): Chunk[headerType.HeaderValue] = headers(headerType)

  /**
   * @return
   *   null if header is not found
   */
  private[http] def getUnsafe(key: CharSequence): String

  /** Returns this collection. */
  override final def headers: Headers = self

  /** Returns an iterator over the headers, in order. */
  override def iterator: Iterator[Header]

  /** Returns the headers in the form `Headers(h1, h2, ...)`. */
  override def toString: String = mkString("Headers(", ", ", ")")

  /** Returns a collection with `f` applied to each header. */
  final def modify(f: Header => Header): Headers = Headers.FromIterable(self.map(f))

  /** Returns the result of applying `update` to this collection. */
  override final def updateHeaders(update: Headers => Headers)(implicit trace: Trace): Headers = update(self)

  /**
   * Returns this collection when `cond` is `true`, and empty headers otherwise.
   */
  final def when(cond: Boolean): Headers = if (cond) self else Headers.Empty

}

/**
 * Constructors for `Headers`.
 */
object Headers {

  private[zio] final case class FromIterable(iter: Iterable[Header]) extends Headers {
    self =>

    override def contains(key: CharSequence): Boolean =
      iter.exists { entry =>
        CharSequenceExtensions.equals(entry.headerName, key, CaseMode.Insensitive)
      }

    override def iterator: Iterator[Header] =
      iter.iterator

    private[http] override def getUnsafe(key: CharSequence): String = {
      val it             = iter.iterator
      var result: String = null
      while (it.hasNext && (result eq null)) {
        val entry = it.next()
        if (CharSequenceExtensions.equals(entry.headerName, key, CaseMode.Insensitive)) {
          result = entry.renderedValue
        }
      }

      result
    }
  }

  private[zio] final case class Native[T](
    value: T,
    iterate: T => Iterator[Header],
    unsafeGet: (T, CharSequence) => String,
    getAll: (T, CharSequence) => Chunk[String],
    contains: (T, CharSequence) => Boolean,
  ) extends Headers {
    override def contains(key: CharSequence): Boolean = contains(value, key)

    override def iterator: Iterator[Header] = iterate(value)

    override private[http] def getUnsafe(key: CharSequence): String = unsafeGet(value, key)

    override def rawHeaders(name: CharSequence): Chunk[String] = getAll(value, name)

  }

  private[zio] final case class Concat(first: Headers, second: Headers) extends Headers {
    self =>

    override def contains(key: CharSequence): Boolean =
      first.contains(key) || second.contains(key)

    override def iterator: Iterator[Header] =
      first.iterator ++ second.iterator

    private[http] override def getUnsafe(key: CharSequence): String = {
      val fromSecond = second.getUnsafe(key)
      if (fromSecond ne null) fromSecond else first.getUnsafe(key)
    }
  }

  private[zio] case object Empty extends Headers {

    override def contains(key: CharSequence): Boolean = false

    override def iterator: Iterator[Header] =
      Iterator.empty

    private[http] override def getUnsafe(key: CharSequence): String = null
  }

  /** Creates headers with one custom header of the given name and value. */
  def apply(name: CharSequence, value: CharSequence): Headers = Headers.FromIterable(Chunk(Header.Custom(name, value)))

  /**
   * Creates headers with one custom header from a pair of a name and a value.
   */
  def apply(tuple2: (CharSequence, CharSequence)): Headers = apply(tuple2._1, tuple2._2)

  /**
   * Creates headers with one custom header for each pair of a name and a value,
   * in order.
   */
  def apply(value: (CharSequence, CharSequence), values: (CharSequence, CharSequence)*): Headers =
    Headers.FromIterable((value +: values).map { case (k, v) => Header.Custom(k, v) })

  /** Creates headers from the given headers, in order. */
  def apply(headers: Header*): Headers = FromIterable(headers)

  /** Creates headers from the given headers, in order. */
  def apply(iter: Iterable[Header]): Headers = FromIterable(iter)

  /** The headers with no header. */
  def empty: Headers = Empty

  /** Creates headers from the given headers, in order. */
  def fromIterable(iter: Iterable[Header]): Headers = FromIterable(iter)

  /**
   * Returns `onTrue` when `cond` is `true` and `onFalse` otherwise. Only the
   * chosen argument is evaluated.
   */
  def ifThenElse(cond: Boolean)(onTrue: => Headers, onFalse: => Headers): Headers = if (cond) onTrue else onFalse

  /**
   * Returns `headers` when `cond` is `true` and empty headers otherwise.
   * `headers` is evaluated only when `cond` is `true`.
   */
  def when(cond: Boolean)(headers: => Headers): Headers = if (cond) headers else Empty

}
