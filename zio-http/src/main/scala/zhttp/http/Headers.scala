package zhttp.http

import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaders}
import zhttp.http.headers.{HeaderConstructors, HeaderExtension}
import zio.Chunk

import scala.jdk.CollectionConverters._

/**
 * Represents an immutable collection of headers i.e. essentially a
 * Chunk[(String, String)]. It extends HeaderExtensions and has a ton of
 * powerful operators that can be used to add, remove and modify headers.
 *
 * NOTE: Generic operators that are not specific to `Headers` should not be
 * defined here. A better place would be one of the traits extended by
 * `HeaderExtension`.
 */
final case class Headers(toChunk: Chunk[Header]) extends HeaderExtension[Headers] {
  self =>

  def ++(other: Headers): Headers = self.combine(other)

  def combine(other: Headers): Headers = Headers(self.toChunk ++ other.toChunk)

  def combineIf(cond: Boolean)(other: Headers): Headers = if (cond) Headers(self.toChunk ++ other.toChunk) else self

  override def headers: Headers = self

  def modify(f: Header => Header): Headers = Headers(toChunk.map(f(_)))

  def toList: List[(String, String)] = toChunk.map { case (name, value) => (name.toString, value.toString) }.toList

  override def updateHeaders(update: Headers => Headers): Headers = update(self)

  def when(cond: Boolean): Headers = if (cond) self else Headers.empty

  /**
   * Converts a Headers to [io.netty.handler.codec.http.HttpHeaders]
   */
  private[zhttp] def encode: HttpHeaders =
    self.toList.foldLeft[HttpHeaders](new DefaultHttpHeaders()) { case (headers, entry) =>
      headers.set(entry._1, entry._2)
    }
}

object Headers extends HeaderConstructors {

  val empty: Headers   = Headers(Nil)
  val BasicSchemeName  = "Basic"
  val BearerSchemeName = "Bearer"

  def apply(name: CharSequence, value: CharSequence): Headers = Headers(Chunk((name, value)))

  def apply(tuples: Header*): Headers = Headers(Chunk.fromIterable(tuples))

  def apply(iter: Iterable[Header]): Headers = Headers(Chunk.fromIterable(iter))

  def ifThenElse(cond: Boolean)(onTrue: => Headers, onFalse: => Headers): Headers = if (cond) onTrue else onFalse

  def make(headers: HttpHeaders): Headers = Headers {
    headers
      .iteratorCharSequence()
      .asScala
      .map(h => (h.getKey, h.getValue))
      .toList
  }

  def when(cond: Boolean)(headers: => Headers): Headers = if (cond) headers else Headers.empty

  private[zhttp] def decode(headers: HttpHeaders): Headers =
    Headers(headers.entries().asScala.toList.map(entry => (entry.getKey, entry.getValue)))
}
