package zio.http

import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaders}
import zio.Chunk
import zio.http.Headers.Header
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

sealed trait Headers extends HeaderExtension[Headers] with Iterable[Header] {
  self =>
  final def ++(other: Headers): Headers = self.combine(other)

  final def combine(other: Headers): Headers =
    Headers.Concat(self, other)

  final def combineIf(cond: Boolean)(other: Headers): Headers =
    if (cond) self ++ other else self

  private[http] def encode: HttpHeaders

  def flatten: Chunk[Header]

  override final def headers: Headers = self

  override def iterator: Iterator[Header]

  final def modify(f: Header => Header): Headers = Headers.FromChunk(flatten.map(f))

  override final def updateHeaders(update: Headers => Headers): Headers = update(self)

  final def when(cond: Boolean): Headers = if (cond) self else Headers.EmptyHeaders

}

object Headers extends HeaderConstructors {

  final case class Header private[zio] (key: CharSequence, value: CharSequence)
      extends Product2[CharSequence, CharSequence]
      with Headers {
    self =>

    override def _1: CharSequence = key

    override def _2: CharSequence = value

    override private[http] def encode: HttpHeaders = Headers.encode(self.toList)

    def flatten: Chunk[Header] = Chunk(self)

    override def iterator: Iterator[Header] =
      Iterator.single(self)

    // Unless we implement it headers.toString results in StackOverflow
    override def toString(): String = (key, value).toString()

  }

  // todo make it FromIterable - then flatten can return an iterable of Header
  final case class FromChunk private[zio] (toChunk: Chunk[Header]) extends Headers {
    self =>

    private[http] def encode: HttpHeaders = Headers.encode(self.toList)

    def flatten: Chunk[Header] = toChunk.flatMap(_.flatten)

    override def iterator: Iterator[Header] =
      toChunk.iterator.flatMap(_.iterator)

  }

  final case class FromJHeaders private[zio] (toJHeaders: HttpHeaders) extends Headers {
    self =>

    override private[http] def encode: HttpHeaders = toJHeaders

    def flatten: Chunk[Header] =
      Chunk.fromIterable(toJHeaders.entries().asScala.map(e => Header(e.getKey, e.getValue)))

    override def iterator: Iterator[Header] =
      toJHeaders.entries().asScala.map(e => Header(e.getKey, e.getValue)).iterator

  }

  final case class Concat private[zio] (first: Headers, second: Headers) extends Headers {
    self =>

    override private[http] def encode: HttpHeaders = Headers.encode(self.toList)

    override def flatten: Chunk[Header] = first.flatten ++ second.flatten

    override def iterator: Iterator[Header] =
      first.iterator ++ second.iterator

  }

  case object EmptyHeaders extends Headers {
    self =>

    override private[http] def encode: HttpHeaders = new DefaultHttpHeaders()

    override def flatten: Chunk[Header] = Chunk.empty

    override def iterator: Iterator[Header] =
      Iterator.empty

  }

  val BasicSchemeName  = "Basic"
  val BearerSchemeName = "Bearer"

  def apply(name: CharSequence, value: CharSequence): Headers = Headers.Header(name.toString, value.toString)

  def apply(tuple2: (CharSequence, CharSequence)): Headers = Headers.Header(tuple2._1, tuple2._2)

  def apply(headers: Header*): Headers = FromChunk(Chunk.fromIterable(headers))

  def apply(iter: Iterable[Header]): Headers = FromChunk(Chunk.fromIterable(iter))

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
