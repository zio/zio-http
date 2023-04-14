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

import zio.http.internal.{CaseMode, CharSequenceExtensions, HeaderOps}

/**
 * Represents an immutable collection of headers. It extends HeaderExtensions
 * and has a ton of powerful operators that can be used to add, remove and
 * modify headers.
 *
 * NOTE: Generic operators that are not specific to `Headers` should not be
 * defined here. A better place would be one of the traits extended by
 * `HeaderExtension`.
 */

sealed trait Headers extends HeaderOps[Headers] with Iterable[Header] {
  self =>
  final def ++(other: Headers): Headers = self.combine(other)

  final def combine(other: Headers): Headers =
    Headers.Concat(self, other)

  final def combineIf(cond: Boolean)(other: Headers): Headers =
    if (cond) self ++ other else self

  final def get(key: CharSequence): Option[String] = Option(getUnsafe(key))

  final def get(headerType: Header.HeaderType): Option[headerType.HeaderValue] = header(headerType)

  final def getAll(headerType: Header.HeaderType): Iterable[headerType.HeaderValue] = headers(headerType)

  /**
   * @return
   *   null if header is not found
   */
  private[http] def getUnsafe(key: CharSequence): String

  override final def headers: Headers = self

  override def iterator: Iterator[Header]

  final def modify(f: Header => Header): Headers = Headers.FromIterable(self.map(f))

  override final def updateHeaders(update: Headers => Headers): Headers = update(self)

  final def when(cond: Boolean): Headers = if (cond) self else Headers.Empty

}

object Headers {

  private[zio] final case class FromIterable(iter: Iterable[Header]) extends Headers {
    self =>

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
  ) extends Headers {
    override def iterator: Iterator[Header] = iterate(value)

    override private[http] def getUnsafe(key: CharSequence): String = unsafeGet(value, key)
  }

  private[zio] final case class Concat(first: Headers, second: Headers) extends Headers {
    self =>

    override def iterator: Iterator[Header] =
      first.iterator ++ second.iterator

    private[http] override def getUnsafe(key: CharSequence): String = {
      val fromFirst = first.getUnsafe(key)
      if (fromFirst ne null) fromFirst else second.getUnsafe(key)
    }
  }

  private[zio] case object Empty extends Headers {

    override def iterator: Iterator[Header] =
      Iterator.empty

    private[http] override def getUnsafe(key: CharSequence): String = null
  }

  def apply(name: CharSequence, value: CharSequence): Headers = Headers.FromIterable(Chunk(Header.Custom(name, value)))

  def apply(tuple2: (CharSequence, CharSequence)): Headers = apply(tuple2._1, tuple2._2)

  def apply(headers: Header*): Headers = FromIterable(headers)

  def apply(iter: Iterable[Header]): Headers = FromIterable(iter)

  def empty: Headers = Empty

  def ifThenElse(cond: Boolean)(onTrue: => Headers, onFalse: => Headers): Headers = if (cond) onTrue else onFalse

  def when(cond: Boolean)(headers: => Headers): Headers = if (cond) headers else Empty

}
