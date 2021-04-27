package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames, HttpHeaderValues => JHttpHeaderValues}
import io.netty.util.AsciiString
import io.netty.util.AsciiString.toLowerCase

trait HeadersHelpers { self: HasHeaders =>
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

  def getHeaderValue(headerName: CharSequence): Option[String] =
    getHeader(headerName).map(_.value.toString)

  def getHeader(headerName: CharSequence): Option[Header] =
    headers.find(h => contentEqualsIgnoreCase(h.name, headerName))

  def getHeaderValues(headerName: CharSequence): List[String] =
    headers.filter(h => contentEqualsIgnoreCase(h.name, headerName)).map(_.value.toString)

  def getContentType: Option[String] =
    getHeaderValue(JHttpHeaderNames.CONTENT_TYPE)

  private def checkContentType(value: AsciiString): Boolean =
    getContentType
      .exists(v => value.contentEquals(v))

  def isJsonContentType: Boolean =
    checkContentType(JHttpHeaderValues.APPLICATION_JSON)

  def isTextPlainContentType: Boolean =
    checkContentType(JHttpHeaderValues.TEXT_PLAIN)

  def isXmlContentType: Boolean =
    checkContentType(JHttpHeaderValues.APPLICATION_XML)

  def isXhtmlXmlContentType: Boolean =
    checkContentType(JHttpHeaderValues.APPLICATION_XHTML)

  def isFormUrlencodedContentType: Boolean =
    checkContentType(JHttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)

  def getAuthorization: Option[String] =
    getHeaderValue(JHttpHeaderNames.AUTHORIZATION)

}
