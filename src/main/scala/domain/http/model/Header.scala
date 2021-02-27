package zio-http.domain.http.model

import zio-http.core.netty.{JDefaultHttpHeaders, JHttpHeaders}
import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames, HttpHeaderValues => JHttpHeaderValues}

import scala.jdk.CollectionConverters._

final case class Header private[Header] (name: CharSequence, value: AnyRef)

object Header {

  /**
   * Converts a List[Header] to [[io.netty.handler.codec.http.HttpHeaders]]
   */
  def disassemble(headers: List[Header]): JHttpHeaders =
    headers.foldLeft[JHttpHeaders](new JDefaultHttpHeaders()) { case (headers, entry) =>
      headers.set(entry.name, entry.value)
    }

  def make(headers: JHttpHeaders): List[Header] =
    headers
      .iteratorCharSequence()
      .asScala
      .map(h => Header(h.getKey, h.getValue))
      .toList

  // Helper utils to create Header instances
  val emptyContent: Header             = Header(JHttpHeaderNames.CONTENT_LENGTH, "0")
  def contentLength(size: Int): Header = Header(JHttpHeaderNames.CONTENT_LENGTH, size.toString)
  val contentTypeJson: Header          = Header(JHttpHeaderNames.CONTENT_TYPE, JHttpHeaderValues.APPLICATION_JSON)
  val contentTypeTextPlain: Header     = Header(JHttpHeaderNames.CONTENT_TYPE, JHttpHeaderValues.TEXT_PLAIN)

  /**
   * Use built-in header methods for better performance.
   */
  def custom(name: String, value: AnyRef): Header = Header(name, value)

  def parse(headers: JHttpHeaders): List[Header] =
    headers.entries().asScala.toList.map(entry => Header(entry.getKey, entry.getValue))
}
