package zhttp.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.base64.Base64
import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues, HttpUtil}
import io.netty.util.AsciiString.toLowerCase
import io.netty.util.{AsciiString, CharsetUtil}
import zhttp.http.HeaderExtension.{BasicSchemeName, BearerSchemeName}

import java.nio.charset.Charset
import scala.util.control.NonFatal

private[zhttp] trait HeaderExtension[+A] { self =>

  def addHeader(header: Header): A = addHeaders(List(header))

  def addHeaders(headers: List[Header]): A

  /**
   * Gets the [[HttpHeaderNames.AUTHORIZATION]] header value if present.
   */
  def getAuthorizationValue: Option[String] = getHeaderValue(HttpHeaderNames.AUTHORIZATION)

  /**
   * Gets the Basic Authorization Credentials if present.
   */
  def getBasicAuthorizationCredentials: Option[(String, String)] = {
    getAuthorizationValue.flatMap(v => {
      val indexOfBasic = v.indexOf(BasicSchemeName)
      if (indexOfBasic != 0 || v.length == BasicSchemeName.length)
        None
      else {
        try {
          val encoded = v.substring(BasicSchemeName.length + 1)
          decodeHttpBasic(encoded)
        } catch {
          case NonFatal(_) => None
        }
      }
    })
  }

  /**
   * Gets the Bearer Token from the [[HttpHeaderNames.AUTHORIZATION]] [[Header]], if present.
   */
  def getBearerToken: Option[String] = getAuthorizationValue.flatMap(v => {
    val indexOfBearer = v.indexOf(BearerSchemeName)
    if (indexOfBearer != 0 || v.length == BearerSchemeName.length)
      None
    else
      Some(v.substring(BearerSchemeName.length + 1))
  })

  /**
   * Gets the [[Charset]] of the content type from the [[HttpHeaderNames.CONTENT_TYPE]] [[Header]], if present.
   */
  def getCharset: Option[Charset] =
    getHeaderValue(HttpHeaderNames.CONTENT_TYPE).map(HttpUtil.getCharset(_, HTTP_CHARSET))

  /**
   * Gets the value of [[HttpHeaderNames.CONTENT_TYPE]], if present.
   */
  def getContentType: Option[String] = getHeaderValue(HttpHeaderNames.CONTENT_TYPE)

  /**
   * @param headerName
   *   the cookie header name.
   * @return
   *   A [[List]] of [[Cookie]] for the given headerName.
   */
  def getCookieFromHeader(headerName: AsciiString): List[Cookie] =
    getHeaderValues(headerName).flatMap(Cookie.decode(_) match {
      case Left(_)      => Nil
      case Right(value) => List(value)
    })

  /**
   * Gets the [[Header]] for this headerName, if present.
   */
  def getHeader(headerName: CharSequence): Option[Header] =
    headers.find(h => contentEqualsIgnoreCase(h.name, headerName))

  /**
   * Gets the header value for this headerName, if present.
   */
  def getHeaderValue(headerName: CharSequence): Option[String] =
    getHeader(headerName).map(_.value.toString)

  /**
   * Gets the header values for this headerName.
   *
   * @param headerName
   *   the name of the header.
   * @return
   *   the matched header values.
   */
  def getHeaderValues(headerName: CharSequence): List[String] =
    headers.filter(h => contentEqualsIgnoreCase(h.name, headerName)).map(_.value.toString)

  /**
   * Checks if a header is present with the given name and value.
   *
   * @param name
   *   the name of the header.
   * @param value
   *   the value of the header.
   * @return
   *   true if a matching header found.
   */
  def hasHeader(name: CharSequence, value: CharSequence): Boolean =
    getHeaderValue(name) match {
      case Some(v1) => v1 == value
      case None     => false
    }

  /**
   * Checks if a header is present with the given name.
   *
   * @param name
   *   the name of the header.
   * @return
   *   true if a matching header found.
   */
  def hasHeader(name: CharSequence): Boolean = getHeaderValue(name).nonEmpty

  def headers: List[Header]

  /**
   * Checks if the [[Header]]'s [[HttpHeaderNames.CONTENT_TYPE]] is
   * [[HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED]]
   */
  def isFormUrlencodedContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)

  /**
   * Checks if the [[Header]]'s [[HttpHeaderNames.CONTENT_TYPE]] is [[HttpHeaderValues.APPLICATION_JSON]]
   */
  def isJsonContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_JSON)

  /**
   * Checks if the [[Header]]'s [[HttpHeaderNames.CONTENT_TYPE]] is [[HttpHeaderValues.TEXT_PLAIN]]
   */
  def isTextPlainContentType: Boolean =
    checkContentType(HttpHeaderValues.TEXT_PLAIN)

  /**
   * Checks if the [[Header]]'s [[HttpHeaderNames.CONTENT_TYPE]] is [[HttpHeaderValues.APPLICATION_XML]]
   */
  def isXmlContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_XML)

  /**
   * Checks if the [[Header]]'s [[HttpHeaderNames.CONTENT_TYPE]] is [[HttpHeaderValues.APPLICATION_XHTML]]
   */
  def isXhtmlXmlContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_XHTML)

  def removeHeader(name: String): A = removeHeaders(List(name))

  def removeHeaders(headers: List[String]): A

  /**
   * Sets the [[HttpHeaderNames.CONTENT_LENGTH]] [[Header]]
   */
  def setContentLength(value: Long): A = addHeader(Header.contentLength(value))

  /**
   * Sets the [[HttpHeaderNames.TRANSFER_ENCODING]] [[Header]] as [[HttpHeaderValues.CHUNKED]]
   */
  def setTransferEncodingChunked: A = addHeader(Header.transferEncodingChunked)

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

  private def checkContentType(value: AsciiString): Boolean =
    getContentType
      .exists(v => value.contentEquals(v))

  private def decodeHttpBasic(encoded: String): Option[(String, String)] = {
    val authChannelBuffer        = Unpooled.wrappedBuffer(encoded.getBytes(CharsetUtil.UTF_8))
    val decodedAuthChannelBuffer = Base64.decode(authChannelBuffer)
    val decoded                  = decodedAuthChannelBuffer.toString(CharsetUtil.UTF_8)
    val colonIndex               = decoded.indexOf(":")
    if (colonIndex == -1)
      None
    else {
      val username = decoded.substring(0, colonIndex)
      val password =
        if (colonIndex == decoded.length - 1)
          ""
        else
          decoded.substring(colonIndex + 1)
      Some((username, password))
    }
  }

  private def equalsIgnoreCase(a: Char, b: Char) = a == b || toLowerCase(a) == toLowerCase(b)
}

object HeaderExtension {
  val BasicSchemeName  = "Basic"
  val BearerSchemeName = "Bearer"
}
