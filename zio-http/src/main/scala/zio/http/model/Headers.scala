package zio.http.model

import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaders}
import zio.Trace
import zio.http.model.headers._

import scala.jdk.CollectionConverters._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Represents an immutable collection of headers. It extends HeaderExtensions
 * and has a ton of powerful operators that can be used to add, remove and
 * modify headers.
 *
 * NOTE: Generic operators that are not specific to `Headers` should not be
 * defined here. A better place would be one of the traits extended by
 * `HeaderExtension`.
 */

sealed trait Headers extends HeaderExtension[Headers] with HeaderIterable {
  self =>
  final def ++(other: Headers): Headers = self.combine(other)

  final def combine(other: Headers): Headers =
    Headers.Concat(self, other)

  final def combineIf(cond: Boolean)(other: Headers): Headers =
    if (cond) self ++ other else self

  private[http] def encode: HttpHeaders

  final def get(key: String): Option[String] = Option(getUnsafe(key))

  /**
   * @return
   *   null if header is not found
   */
  private[http] def getUnsafe(key: String): String

  override final def headers: Headers = self

  override def iterator: Iterator[Header]

  final def modify(f: Header => Header): Headers = Headers.FromIterable(self.map(f))

  override final def updateHeaders(update: Headers => Headers): Headers = update(self)

  final def when(cond: Boolean): Headers = if (cond) self else Headers.EmptyHeaders

}

object Headers extends HeaderConstructors {

  final case class Header(key: CharSequence, value: CharSequence)
      extends Product2[CharSequence, CharSequence]
      with Headers {
    self =>

    override def _1: CharSequence = key

    override def _2: CharSequence = value

    private[http] override def getUnsafe(key: String): String = if (key == _1) _2.toString else null

    override private[http] def encode: HttpHeaders = Headers.encode(self.toList)

    override def hashCode(): Int = {
      var h       = 0
      val kLength = key.length()
      var i       = 0
      while (i < kLength) {
        h = 17 * h + key.charAt(i)
        i = i + 1
      }
      i = 0
      val vLength = value.length()
      while (i < vLength) {
        h = 17 * h + value.charAt(i)
        i = i + 1
      }
      h
    }

    override def equals(that: Any): Boolean = {
      that match {
        case Header(k, v) =>
          def eqs(l: CharSequence, r: CharSequence): Boolean = {
            if (l.length() != r.length()) false
            else {
              var i     = 0
              var equal = true

              while (i < l.length()) {
                if (l.charAt(i) != r.charAt(i)) {
                  equal = false
                  i = l.length()
                }
                i = i + 1
              }
              equal
            }
          }

          eqs(self.key, k) && eqs(self.value, v)

        case _ => false
      }
    }

    override def iterator: Iterator[Header] =
      Iterator.single(self)

    override def toString(): String = (key, value).toString()

  }

  private[zio] final case class FromIterable(iter: Iterable[Header]) extends Headers {
    self =>

    private[http] def encode: HttpHeaders = Headers.encode(self.toList)

    override def iterator: Iterator[Header] =
      iter.iterator.flatMap(_.iterator)

    private[http] override def getUnsafe(key: String): String = {
      val it = iter.iterator
      while (it.hasNext) {
        val entry = iterator.next()
        if (entry.key == key) {
          return entry.value.toString
        }
      }

      null
    }
  }

  private[zio] final case class FromJHeaders(toJHeaders: HttpHeaders) extends Headers {
    self =>

    override private[http] def encode: HttpHeaders = toJHeaders

    override def iterator: Iterator[Header] =
      toJHeaders.entries().asScala.map(e => Header(e.getKey, e.getValue)).iterator

    private[http] override def getUnsafe(key: String): String = {
      val iterator = toJHeaders.iteratorCharSequence()
      while (iterator.hasNext) {
        val entry = iterator.next()
        if (entry.getKey == key) {
          return entry.getValue.toString
        }
      }

      null
    }

  }

  private[zio] final case class Concat(first: Headers, second: Headers) extends Headers {
    self =>

    override private[http] def encode: HttpHeaders = Headers.encode(self.toList)

    override def iterator: Iterator[Header] =
      first.iterator ++ second.iterator

    private[http] override def getUnsafe(key: String): String = {
      val fromFirst = first.getUnsafe(key)
      if (fromFirst != null) fromFirst else second.getUnsafe(key)
    }
  }

  private[zio] case object EmptyHeaders extends Headers {
    self =>

    override private[http] def encode: HttpHeaders = new DefaultHttpHeaders()

    override def iterator: Iterator[Header] =
      Iterator.empty

    private[http] override def getUnsafe(key: String): String = null
  }

  private[http] val BasicSchemeName  = "Basic"
  private[http] val BearerSchemeName = "Bearer"

  def apply(name: CharSequence, value: CharSequence): Headers = Headers.Header(name, value)

  def apply(tuple2: (CharSequence, CharSequence)): Headers = Headers.Header(tuple2._1, tuple2._2)

  def apply(headers: Header*): Headers = FromIterable(headers)

  def apply(iter: Iterable[Header]): Headers = FromIterable(iter)

  private[http] def decode(headers: HttpHeaders): Headers = FromJHeaders(headers)

  def empty: Headers = EmptyHeaders

  private[http] def encode(headersList: List[Product2[CharSequence, CharSequence]]): HttpHeaders = {
    val (exceptions, regularHeaders) = headersList.span(h => h._1.toString.contains(HeaderNames.setCookie.toString))
    val combinedHeaders              = regularHeaders
      .groupBy(_._1)
      .map { case (key, tuples) =>
        key -> tuples.map(_._2).mkString(",")
      }
    (exceptions ++ combinedHeaders)
      .foldLeft[HttpHeaders](new DefaultHttpHeaders(true)) { case (headers, entry) =>
        headers.add(entry._1, entry._2)
      }
  }

  def ifThenElse(cond: Boolean)(onTrue: => Headers, onFalse: => Headers): Headers = if (cond) onTrue else onFalse

  def make(headers: HttpHeaders): Headers = FromJHeaders(headers)

  def when(cond: Boolean)(headers: => Headers): Headers = if (cond) headers else EmptyHeaders

}
