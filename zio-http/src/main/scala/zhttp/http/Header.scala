package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames, HttpHeaderValues => JHttpHeaderValues}
import io.netty.util.AsciiString
import io.netty.util.AsciiString.toLowerCase
import zhttp.core.{JDefaultHttpHeaders, JHttpHeaders}

import scala.jdk.CollectionConverters._

final case class Header private[Header] (name: CharSequence, value: CharSequence)

object Header {

  /**
   * Converts a List[Header] to [io.netty.handler.codec.http.HttpHeaders]
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
  val acceptJson: Header     = Header(JHttpHeaderNames.ACCEPT, JHttpHeaderValues.APPLICATION_JSON)
  val acceptXhtmlXml: Header = Header(JHttpHeaderNames.ACCEPT, JHttpHeaderValues.APPLICATION_XHTML)
  val acceptXml: Header      = Header(JHttpHeaderNames.ACCEPT, JHttpHeaderValues.APPLICATION_XML)
  val acceptAll: Header      = Header(JHttpHeaderNames.ACCEPT, "*/*")

  val contentTypeJson: Header           = Header(JHttpHeaderNames.CONTENT_TYPE, JHttpHeaderValues.APPLICATION_JSON)
  val contentTypeXml: Header            = Header(JHttpHeaderNames.CONTENT_TYPE, JHttpHeaderValues.APPLICATION_XML)
  val contentTypeXhtmlXml: Header       = Header(JHttpHeaderNames.CONTENT_TYPE, JHttpHeaderValues.APPLICATION_XHTML)
  val contentTypeTextPlain: Header      = Header(JHttpHeaderNames.CONTENT_TYPE, JHttpHeaderValues.TEXT_PLAIN)
  val contentTypeFormUrlEncoded: Header =
    Header(JHttpHeaderNames.CONTENT_TYPE, JHttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)

  def createAuthorizationHeader(value: String): Header = Header(JHttpHeaderNames.AUTHORIZATION, value)

  /**
   * Use built-in header methods for better performance.
   */
  def custom(name: String, value: CharSequence): Header = Header(name, value)

  def parse(headers: JHttpHeaders): List[Header] =
    headers.entries().asScala.toList.map(entry => Header(entry.getKey, entry.getValue))

  private def equalsIgnoreCase(a: Char, b: Char) = a == b || toLowerCase(a) == toLowerCase(b)

  private def contentEqualsIgnoreCase(a: CharSequence, b: CharSequence): Boolean = {
    if (a == b)
      true
    else if (a.length() != b.length())
      false
    else if (a.isInstanceOf[AsciiString]) {
      a.asInstanceOf[AsciiString].contentEqualsIgnoreCase(b)
    } else if (b.isInstanceOf[AsciiString]) {
      b.asInstanceOf[AsciiString].contentEqualsIgnoreCase(a)
    } else {
      (0 until a.length()).forall(i => equalsIgnoreCase(a.charAt(i), b.charAt(i)))
    }
  }

  def getHeaderValue(headerName: CharSequence, headers: List[Header]): Option[String] =
    getHeader(headerName, headers).map(_.value.toString)

  def getHeader(headerName: CharSequence, headers: List[Header]): Option[Header] =
    headers.find(h => contentEqualsIgnoreCase(h.name, headerName))

  def getHeaderValues(headerName: CharSequence, headers: List[Header]): List[String] =
    headers.filter(h => contentEqualsIgnoreCase(h.name, headerName)).map(_.value.toString)

  def getContentType(headers: List[Header]): Option[String] =
    getHeaderValue(JHttpHeaderNames.CONTENT_TYPE, headers)

  private def checkContentType(headers: List[Header], value: AsciiString): Boolean =
    getContentType(headers)
      .exists(v => value.contentEquals(v))

  def isJsonContentType(headers: List[Header]): Boolean =
    checkContentType(headers, JHttpHeaderValues.APPLICATION_JSON)

  def isTextPlainContentType(headers: List[Header]): Boolean =
    checkContentType(headers, JHttpHeaderValues.TEXT_PLAIN)

  def isXmlContentType(headers: List[Header]): Boolean =
    checkContentType(headers, JHttpHeaderValues.APPLICATION_XML)

  def isXhtmlXmlContentType(headers: List[Header]): Boolean =
    checkContentType(headers, JHttpHeaderValues.APPLICATION_XHTML)

  def isFormUrlencodedContentType(headers: List[Header]): Boolean =
    checkContentType(headers, JHttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)

  def getAuthorization(headers: List[Header]): Option[String] =
    getHeaderValue(JHttpHeaderNames.AUTHORIZATION, headers)

}
