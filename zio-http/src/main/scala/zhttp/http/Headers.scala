package zhttp.http

import io.netty.handler.codec.http.{CombinedHttpHeaders, HttpHeaders}
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

/**
 * Represents an immutable collection of headers i.e. essentially a
 * Chunk[(String, String)]. It extends HeaderExtensions and has a ton of
 * powerful operators that can be used to add, remove and modify headers.
 *
 * NOTE: Generic operators that are not specific to `Headers` should not be
 * defined here. A better place would be one of the traits extended by
 * `HeaderExtension`.
 */
sealed trait Headers extends HeaderExtension[Headers] {
  self =>

  def ++(other: Headers): Headers = HeadersCons(self, other)

  def combine(other: Headers): Headers = HeadersCons(self, other)

  def combineIf(cond: Boolean)(other: Headers): Headers = if (cond)
    HeadersCons(self, other)
  else self

  override def headers: Headers = self

  def modify(f: Header => Header): Headers = ModifyHeaders(f, self)

  def toList: List[(String, String)] = self match {
    case EmptyHeaders                    => List()
    case HeadersCons(a, b)               => a.toList ++ b.toList
    case HeadersFromChunk(chunk)         => chunk.map(h => (h._1.toString, h._2.toString)).toList
    case HeadersFromHttp(headers)        =>
      headers
        .iteratorCharSequence()
        .asScala
        .map(h => (h.getKey.toString, h.getValue.toString))
        .toList
    case ModifyHeaders(modify, headers)  => headers.toList.map(modify).map(h => (h._1.toString, h._2.toString))
    case UpdatedHeaders(update, headers) =>
      update(headers).toList
  }

  override def updateHeaders(update: Headers => Headers): Headers =
    UpdatedHeaders(update, this)

  def when(cond: Boolean): Headers = if (cond) self else Headers.empty

  /**
   * Converts a Headers to [io.netty.handler.codec.http.HttpHeaders]
   */
  private[zhttp] def encode: HttpHeaders =
    self.toList.foldLeft[HttpHeaders](new CombinedHttpHeaders(true)) { case (headers, entry) =>
      headers.add(entry._1, entry._2)
    }
}

case object EmptyHeaders                                              extends Headers
case class HeadersFromChunk(value: Chunk[Header])                     extends Headers
case class HeadersFromHttp(value: HttpHeaders)                        extends Headers
case class UpdatedHeaders(update: Headers => Headers, value: Headers) extends Headers
case class ModifyHeaders(modify: Header => Header, value: Headers)    extends Headers
case class HeadersCons(a: Headers, b: Headers)                        extends Headers

object Headers extends HeaderConstructors {

  val empty: Headers   = EmptyHeaders
  val BasicSchemeName  = "Basic"
  val BearerSchemeName = "Bearer"

  def apply(name: CharSequence, value: CharSequence): Headers = HeadersFromChunk(Chunk((name, value)))

  def apply(tuples: Header*): Headers = HeadersFromChunk(Chunk.fromIterable(tuples))

  def apply(iter: Iterable[Header]): Headers = HeadersFromChunk(Chunk.fromIterable(iter))

  def ifThenElse(cond: Boolean)(onTrue: => Headers, onFalse: => Headers): Headers = if (cond) onTrue else onFalse

  def make(headers: HttpHeaders): Headers = HeadersFromHttp(headers)

  def when(cond: Boolean)(headers: => Headers): Headers = if (cond) headers else EmptyHeaders

  private[zhttp] def decode(headers: HttpHeaders): Headers =
    Headers(headers.entries().asScala.toList.map(entry => (entry.getKey, entry.getValue)))
}
