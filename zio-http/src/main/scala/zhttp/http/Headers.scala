package zhttp.http

import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaderNames, HttpHeaders, HttpHeaderValues}
import zhttp.http.HeaderExtension.BasicSchemeName

import java.util.Base64
import scala.jdk.CollectionConverters._

/**
 * TODO: use Chunk instead of List TODO: use Tuple2 instead of Header
 */

final case class Headers(toList: List[(CharSequence, CharSequence)]) extends HeaderExtension[Headers] {
  self =>
  def ++(other: Headers): Headers = self.combine(other)

  def combine(other: Headers): Headers = Headers(self.toList ++ other.toList)

  def combineIf(cond: Boolean)(other: Headers): Headers = if (cond) Headers(self.toList ++ other.toList) else self

  override def getHeaders: Headers = self

  override def updateHeaders(f: Headers => Headers): Headers = f(self)

  def when(cond: Boolean): Headers = if (cond) self else Headers.empty

}

object Headers {
  def accessControlAllowCredentials(bool: Boolean): Headers =
    Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, bool.toString)

  def accessControlAllowHeaders(str: String): Headers =
    Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, str)

  def accessControlAllowMethods(method: Method): Headers =
    Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, method.asHttpMethod.name())

  def accessControlAllowOrigin(origin: String): Headers =
    Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin)

  def accessControlExposeHeaders(str: String): Headers =
    Headers(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS, str)

  def accessControlRequestMethod(method: Method): Headers =
    Headers(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD, method.asHttpMethod.name())

  def apply(name: CharSequence, value: CharSequence): Headers = Headers(List((name, value)))

  def apply(tuple: (CharSequence, CharSequence)): Headers = Headers(tuple._1, tuple._2)

  def authorization(value: String): Headers = Headers(HttpHeaderNames.AUTHORIZATION, value)

  def basicHttpAuthorization(username: String, password: String): Headers = {
    val authString    = String.format("%s:%s", username, password)
    val encodedAuthCB = new String(Base64.getEncoder.encode(authString.getBytes(HTTP_CHARSET)), HTTP_CHARSET)
    val value         = String.format("%s %s", BasicSchemeName, encodedAuthCB)
    Headers(HttpHeaderNames.AUTHORIZATION, value)
  }

  def contentLength(size: Long): Headers = Headers(HttpHeaderNames.CONTENT_LENGTH, size.toString)

  def createAuthorizationHeader(value: String): Headers = Headers(HttpHeaderNames.AUTHORIZATION, value)

  /**
   * Converts a Headers to [io.netty.handler.codec.http.HttpHeaders]
   *
   * TODO: move up as a method, make package private, rename to encode
   */
  def disassemble(headers: Headers): HttpHeaders =
    headers.toList.foldLeft[HttpHeaders](new DefaultHttpHeaders()) { case (headers, entry) =>
      headers.set(entry._1, entry._2)
    }

  def host(name: String): Headers = Headers(HttpHeaderNames.HOST, name)

  def ifThenElse(cond: Boolean)(onTrue: => Headers, onFalse: => Headers): Headers = if (cond) onTrue else onFalse

  def location(value: String): Headers = Headers(HttpHeaderNames.LOCATION, value)

  def make(headers: HttpHeaders): Headers = Headers {
    headers
      .iteratorCharSequence()
      .asScala
      .map(h => (h.getKey, h.getValue))
      .toList
  }

  def origin(str: String): Headers = Headers(HttpHeaderNames.ORIGIN, str)

  /**
   * TODO: rename to decode
   */
  def parse(headers: HttpHeaders): Headers =
    Headers(headers.entries().asScala.toList.map(entry => (entry.getKey, entry.getValue)))

  def setCookie(cookie: Cookie): Headers = Headers(HttpHeaderNames.SET_COOKIE, cookie.encode)

  def userAgent(name: String): Headers = Headers(HttpHeaderNames.USER_AGENT, name)

  def when(cond: Boolean)(headers: => Headers): Headers = if (cond) headers else Headers.empty

  val empty: Headers                     = Headers(Nil)
  val connectionKeepAlive: Headers       = Headers(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
  val connectionClose: Headers           = Headers(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
  val acceptJson: Headers                = Headers(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
  val acceptXhtmlXml: Headers            = Headers(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_XHTML)
  val acceptXml: Headers                 = Headers(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_XML)
  val acceptAll: Headers                 = Headers(HttpHeaderNames.ACCEPT, "*/*")
  val contentTypeJson: Headers           = Headers(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
  val contentTypeXml: Headers            = Headers(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_XML)
  val contentTypeXhtmlXml: Headers       = Headers(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_XHTML)
  val contentTypeTextPlain: Headers      = Headers(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
  val contentTypeHtml: Headers           = Headers(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML)
  val contentTypeYaml: Headers           = Headers(HttpHeaderNames.CONTENT_TYPE, "text/yaml")
  val transferEncodingChunked: Headers   = Headers(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
  val contentTypeFormUrlEncoded: Headers =
    Headers(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)
}
