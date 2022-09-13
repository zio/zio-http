package zio.http

import io.netty.handler.codec.http.{CombinedHttpHeaders, DefaultHttpHeaders, HttpHeaders}
import zio.Chunk
import zio.http.headers.{HeaderConstructors, HeaderExtension}

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

sealed trait Headers extends HeaderExtension[Headers] {
  self =>
  def ++(other: Headers): Headers = self.combine(other)

  def combine(other: Headers): Headers

  def combineIf(cond: Boolean)(other: Headers): Headers =
    if (cond) self ++ other else self

  override def headers: Headers = self

  def modify(f: Header => Header): Headers

  def toList: List[(String, String)]

  override def updateHeaders(update: Headers => Headers): Headers = update(self)

  def when(cond: Boolean): Headers = if (cond) self else EmptyHeaders

  private[zio] def encode: HttpHeaders
}

final case class SingleHeader private[zio] (header: Header) extends Headers {
  self =>
  override def combine(other: Headers): Headers = other match {
    case SingleHeader(h)              => FromChunk(Chunk(header, h))
    case FromChunk(toChunk)           => FromChunk(toChunk.prepended(header))
    case FromJHeaders(toJHeaders)     => FromAll(Chunk(header), toJHeaders)
    case FromAll(toChunk, toJHeaders) => FromAll(toChunk.prepended(header), toJHeaders)
    case EmptyHeaders                 => self
  }

  override def modify(f: Header => Header): Headers = SingleHeader(f(header))

  override def toList: List[(String, String)] = List((header._1.toString, header._2.toString))

  override private[zio] def encode = {
    val headers = new DefaultHttpHeaders(true)
    headers.add(header._1, header._2)
    headers
  }
}

final case class FromChunk private[zio] (toChunk: Chunk[Header]) extends Headers {
  self =>

  override def combine(other: Headers): Headers = other match {
    case FromChunk(otherChunk)        => FromChunk(toChunk ++ otherChunk)
    case FromJHeaders(toJHeaders)     => FromAll(toChunk, toJHeaders)
    case FromAll(toChunk, toJHeaders) =>
      FromAll(toChunk ++ self.toChunk, toJHeaders)
    case SingleHeader(header)         => FromChunk(toChunk.appended(header))
    case EmptyHeaders                 => other
  }

  override def modify(f: Header => Header): Headers = FromChunk(toChunk.map(f))

  override def toList: List[(String, String)] = toChunk.map(a => (a._1.toString, a._2.toString)).toList

  private[zio] def encode: HttpHeaders =
    self.toList
      .foldLeft[HttpHeaders](new CombinedHttpHeaders(true)) { case (headers, entry) =>
        headers.add(entry._1, entry._2)
      }

}

final case class FromJHeaders private[zio] (toJHeaders: HttpHeaders) extends Headers {
  self =>

  override def combine(other: Headers): Headers = other match {
    case FromChunk(otherChunk)              => FromAll(otherChunk, toJHeaders)
    case FromJHeaders(otherJHeaders)        => FromJHeaders(toJHeaders.add(otherJHeaders))
    case FromAll(otherChunk, otherJHeaders) =>
      FromAll(otherChunk, toJHeaders.add(otherJHeaders))
    case SingleHeader(header)               => FromAll(Chunk(header), toJHeaders)
    case EmptyHeaders                       => other
  }

  override def modify(f: Header => Header): Headers = {
    val combinedHttpHeaders = self.toList
      .foldLeft[HttpHeaders](new CombinedHttpHeaders(true)) { case (headers, entry) =>
        val h = f((entry._1, entry._2))
        headers.add(h._1, h._2)
      }
    Headers.make(combinedHttpHeaders)
  }

  override def toList: List[(String, String)] = toJHeaders.entries().asScala.map(e => (e.getKey, e.getValue)).toList

  override private[zio] def encode: HttpHeaders = toJHeaders
}

final case class FromAll private[zio] (toChunk: Chunk[Header], toJHeaders: HttpHeaders) extends Headers { self =>

  override def combine(other: Headers): Headers = other match {
    case FromChunk(otherChunk)              => FromAll(toChunk ++ otherChunk, toJHeaders)
    case FromJHeaders(otherJHeaders)        => FromAll(toChunk, toJHeaders.add(otherJHeaders))
    case FromAll(otherChunk, otherJHeaders) =>
      FromAll(toChunk ++ otherChunk, toJHeaders.add(otherJHeaders))
    case SingleHeader(header)               => FromAll(toChunk.appended(header), toJHeaders)
    case EmptyHeaders                       => other
  }

  override def modify(f: Header => Header): Headers = {
    val combinedHttpHeaders = self.toList
      .foldLeft[HttpHeaders](new CombinedHttpHeaders(true)) { case (headers, entry) =>
        val h = f((entry._1, entry._2))
        headers.add(h._1, h._2)
      }
    FromAll(toChunk.map(f), combinedHttpHeaders)
  }

  override def toList: List[(String, String)] =
    toChunk
      .map(a => (a._1.toString, a._2.toString))
      .toList ++ toJHeaders.entries().asScala.map(e => (e.getKey, e.getValue)).toList

  override private[zio] def encode: HttpHeaders =
    self.toList
      .foldLeft[HttpHeaders](new CombinedHttpHeaders(true)) { case (headers, entry) =>
        headers.add(entry._1, entry._2)
      }

}

case object EmptyHeaders extends Headers { self =>
  override def combine(other: Headers): Headers = other

  override def modify(f: Header => Header): Headers = self

  override def toList: List[(String, String)] = Nil

  override private[zio] def encode: HttpHeaders = new DefaultHttpHeaders()
}

object Headers extends HeaderConstructors {

  val BasicSchemeName  = "Basic"
  val BearerSchemeName = "Bearer"

  def apply(name: CharSequence, value: CharSequence): Headers = FromChunk(Chunk((name, value)))

  def apply(tuples: Header*): Headers = FromChunk(Chunk.fromIterable(tuples))

  def apply(iter: Iterable[Header]): Headers = FromChunk(Chunk.fromIterable(iter))

  def ifThenElse(cond: Boolean)(onTrue: => Headers, onFalse: => Headers): Headers = if (cond) onTrue else onFalse

  def make(headers: HttpHeaders): Headers = FromJHeaders(headers)

  def when(cond: Boolean)(headers: => Headers): Headers = if (cond) headers else EmptyHeaders

  private[zio] def decode(headers: HttpHeaders): Headers = FromJHeaders(headers)

  def empty: Headers = EmptyHeaders
}
