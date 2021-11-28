package zhttp.http

import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaderNames, HttpHeaders, HttpHeaderValues}
import zhttp.http.HeaderExtension.BasicSchemeName

import java.util.Base64
import scala.jdk.CollectionConverters._

final case class Header(name: CharSequence, value: CharSequence) {
  def toTuple: (String, String) = (name.toString, value.toString)
}

object Header {
  def accessControlAllowCredentials(bool: Boolean): Header =
    Header(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, bool.toString)

  def accessControlAllowHeaders(str: String): Header =
    Header(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, str)

  def accessControlAllowMethods(method: Method): Header =
    Header(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, method.asHttpMethod.name())

  def accessControlAllowOrigin(origin: String): Header =
    Header(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin)

  def accessControlExposeHeaders(str: String): Header =
    Header(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS, str)

  def accessControlRequestMethod(method: Method): Header =
    Header(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD, method.asHttpMethod.name())

  def authorization(value: String): Header = Header(HttpHeaderNames.AUTHORIZATION, value)

  def basicHttpAuthorization(username: String, password: String): Header = {
    val authString    = String.format("%s:%s", username, password)
    val encodedAuthCB = new String(Base64.getEncoder.encode(authString.getBytes(HTTP_CHARSET)), HTTP_CHARSET)
    val value         = String.format("%s %s", BasicSchemeName, encodedAuthCB)
    Header(HttpHeaderNames.AUTHORIZATION, value)
  }

  def contentLength(size: Long): Header = Header(HttpHeaderNames.CONTENT_LENGTH, size.toString)

  def createAuthorizationHeader(value: String): Header = Header(HttpHeaderNames.AUTHORIZATION, value)

  /**
   * Use built-in header methods for better performance.
   */
  def custom(name: String, value: CharSequence): Header = Header(name, value)

  /**
   * Converts a List[Header] to [io.netty.handler.codec.http.HttpHeaders]
   */
  def disassemble(headers: List[Header]): HttpHeaders =
    headers.foldLeft[HttpHeaders](new DefaultHttpHeaders()) { case (headers, entry) =>
      headers.set(entry.name, entry.value)
    }

  def host(name: String): Header = Header(HttpHeaderNames.HOST, name)

  def location(value: String): Header = Header(HttpHeaderNames.LOCATION, value)

  def make(headers: HttpHeaders): List[Header] =
    headers
      .iteratorCharSequence()
      .asScala
      .map(h => Header(h.getKey, h.getValue))
      .toList

  def origin(str: String): Header = Header(HttpHeaderNames.ORIGIN, str)

  def parse(headers: HttpHeaders): List[Header] =
    headers.entries().asScala.toList.map(entry => Header(entry.getKey, entry.getValue))

  def setCookie(cookie: Cookie): Header = Header(HttpHeaderNames.SET_COOKIE, cookie.encode)

  def userAgent(name: String): Header = Header(HttpHeaderNames.USER_AGENT, name)

  val connectionKeepAlive: Header       = Header(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
  val connectionClose: Header           = Header(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
  val acceptJson: Header                = Header(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
  val acceptXhtmlXml: Header            = Header(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_XHTML)
  val acceptXml: Header                 = Header(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_XML)
  val acceptAll: Header                 = Header(HttpHeaderNames.ACCEPT, "*/*")
  val contentTypeJson: Header           = Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
  val contentTypeXml: Header            = Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_XML)
  val contentTypeXhtmlXml: Header       = Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_XHTML)
  val contentTypeTextPlain: Header      = Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
  val contentTypeHtml: Header           = Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML)
  val contentTypeYaml: Header           = Header(HttpHeaderNames.CONTENT_TYPE, "text/yaml")
  val transferEncodingChunked: Header   = Header(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
  val contentTypeFormUrlEncoded: Header =
    Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)
}
