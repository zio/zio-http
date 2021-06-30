package zhttp.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.base64.Base64
import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames, HttpHeaderValues => JHttpHeaderValues}
import io.netty.util.CharsetUtil
import zhttp.core.{JDefaultHttpHeaders, JHttpHeaders}
import zhttp.http.HeadersHelpers.BasicSchemeName

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
  val transferEncodingChunked: Header   = Header(JHttpHeaderNames.TRANSFER_ENCODING, JHttpHeaderValues.CHUNKED)
  def contentLength(size: Long): Header = Header(JHttpHeaderNames.CONTENT_LENGTH, size.toString)
  val contentTypeFormUrlEncoded: Header =
    Header(JHttpHeaderNames.CONTENT_TYPE, JHttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)

  def host(name: String): Header           = Header(JHttpHeaderNames.HOST, name)
  def userAgent(name: String): Header      = Header(JHttpHeaderNames.USER_AGENT, name)
  def authorization(value: String): Header = Header(JHttpHeaderNames.AUTHORIZATION, value)

  def cookieParser(cookie: List[Cookie[Nothing]]): String =
    cookie.map(a => s"${a.name}=${a.content}").reduce((a, b) => a + ";" + b)

  def cookies(cookie: List[Cookie[Nothing]]): Header = Header(JHttpHeaderNames.COOKIE, cookieParser(cookie))
  def cookie(cookie: Cookie[Nothing]): Header        = Header(JHttpHeaderNames.COOKIE, cookie.name + "=" + cookie.content)

  def setCookie[M <: Meta](cookie: Cookie[M]): Header =
    Header(JHttpHeaderNames.SET_COOKIE, cookie.fromCookie)

  def removeCookie[M <: Meta](cookie: String): Header =
    Header(JHttpHeaderNames.SET_COOKIE, Cookie(cookie, "", None).fromCookie)

  def basicHttpAuthorization(username: String, password: String): Header = {
    val authString    = String.format("%s:%s", username, password)
    val authCB        = Unpooled.wrappedBuffer(authString.getBytes(CharsetUtil.UTF_8))
    val encodedAuthCB = Base64.encode(authCB)
    val value         = String.format("%s %s", BasicSchemeName, encodedAuthCB.toString(CharsetUtil.UTF_8))
    Header(JHttpHeaderNames.AUTHORIZATION, value)
  }

  def createAuthorizationHeader(value: String): Header = Header(JHttpHeaderNames.AUTHORIZATION, value)

  /**
   * Use built-in header methods for better performance.
   */
  def custom(name: String, value: CharSequence): Header = Header(name, value)

  def parse(headers: JHttpHeaders): List[Header] =
    headers.entries().asScala.toList.map(entry => Header(entry.getKey, entry.getValue))
}
