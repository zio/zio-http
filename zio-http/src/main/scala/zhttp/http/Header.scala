package zhttp.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.base64.Base64
import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaderNames, HttpHeaderValues, HttpHeaders}
import io.netty.util.CharsetUtil
import zhttp.http.HeaderExtension.BasicSchemeName

import scala.jdk.CollectionConverters._

final case class Header(name: CharSequence, value: CharSequence)

object Header {

  // Helper utils to create Header instances
  val acceptAll: Header                 = Header(HttpHeaderNames.ACCEPT, "*/*")
  val acceptJson: Header                = Header(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
  val acceptXhtmlXml: Header            = Header(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_XHTML)
  val acceptXml: Header                 = Header(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_XML)
  val contentTypeFormUrlEncoded: Header =
    Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)
  val contentTypeHtml: Header           = Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML)
  val contentTypeJson: Header           = Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
  val contentTypeTextPlain: Header      = Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
  val contentTypeXml: Header            = Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_XML)
  val contentTypeXhtmlXml: Header       = Header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_XHTML)
  val contentTypeYaml: Header           = Header(HttpHeaderNames.CONTENT_TYPE, "text/yaml")
  val transferEncodingChunked: Header   = Header(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)

  def authorization(value: String): Header = Header(HttpHeaderNames.AUTHORIZATION, value)

  def basicHttpAuthorization(username: String, password: String): Header = {
    val authString    = String.format("%s:%s", username, password)
    val authCB        = Unpooled.wrappedBuffer(authString.getBytes(CharsetUtil.UTF_8))
    val encodedAuthCB = Base64.encode(authCB)
    val value         = String.format("%s %s", BasicSchemeName, encodedAuthCB.toString(CharsetUtil.UTF_8))
    authorization(value)
  }

  def contentLength(size: Long): Header = Header(HttpHeaderNames.CONTENT_LENGTH, size.toString)

  /**
   * Use built-in header methods for better performance.
   */
  def custom(name: String, value: CharSequence): Header = Header(name, value)

  /**
   * Converts a [[List]] of [[Header]] to [[io.netty.handler.codec.http.HttpHeaders]]
   */
  def disassemble(headers: List[Header]): HttpHeaders =
    headers.foldLeft[HttpHeaders](new DefaultHttpHeaders()) { case (headers, entry) =>
      headers.set(entry.name, entry.value)
    }

  /**
   * Builds a [[List]] of [[Header]] from [[io.netty.handler.codec.http.HttpHeaders]]
   */
  def fromHttpHeaders(headers: HttpHeaders): List[Header] =
    headers
      .iteratorCharSequence()
      .asScala
      .map(h => Header(h.getKey, h.getValue))
      .toList

  def host(name: String): Header = Header(HttpHeaderNames.HOST, name)

  def location(value: String): Header = Header(HttpHeaderNames.LOCATION, value)

  def userAgent(name: String): Header = Header(HttpHeaderNames.USER_AGENT, name)
}
