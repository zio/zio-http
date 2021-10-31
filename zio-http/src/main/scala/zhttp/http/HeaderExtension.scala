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
  def headers: List[Header]

  def addHeaders(headers: List[Header]): A

  def removeHeaders(headers: List[String]): A

  def addHeader(header: Header): A = addHeaders(List(header))

  def removeHeader(name: String): A = removeHeaders(List(name))

  def setContentLength(value: Long): A =
    addHeader(Header(HttpHeaderNames.CONTENT_LENGTH, value.toString))

  def setChunkedEncoding: A =
    addHeader(Header(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED))

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
    getHeaderValue(HttpHeaderNames.CONTENT_TYPE)

  private def checkContentType(value: AsciiString): Boolean =
    getContentType
      .exists(v => value.contentEquals(v))

  def isJsonContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_JSON)

  def isTextPlainContentType: Boolean =
    checkContentType(HttpHeaderValues.TEXT_PLAIN)

  def isXmlContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_XML)

  def isXhtmlXmlContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_XHTML)

  def isFormUrlencodedContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)

  def getAuthorization: Option[String] =
    getHeaderValue(HttpHeaderNames.AUTHORIZATION)

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
  def getBasicAuthorizationCredentials: Option[(String, String)]         = {
    getAuthorization.flatMap(v => {
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
  def getBearerToken: Option[String]                                     = getAuthorization.flatMap(v => {
    val indexOfBearer = v.indexOf(BearerSchemeName)
    if (indexOfBearer != 0 || v.length == BearerSchemeName.length)
      None
    else
      Some(v.substring(BearerSchemeName.length + 1))
  })

  def getCharset: Option[Charset] =
    getHeaderValue(HttpHeaderNames.CONTENT_TYPE).map(HttpUtil.getCharset(_, HTTP_CHARSET))

  def getCookieFromHeader = getHeaderValues(HttpHeaderNames.SET_COOKIE).flatMap(Cookie.decode(_) match {
    case Left(_)      => Nil
    case Right(value) => List(value)
  })

  def getRequestCookieFromHeader: List[Cookie] = getHeaderValue(HttpHeaderNames.COOKIE) match {
    case Some(value) =>
      Cookie.decodeMultiple(value) match {
        case Left(_)     => Nil
        case Right(list) => list
      }
    case None        => Nil
  }

  def hasHeader(name: CharSequence, value: CharSequence): Boolean =
    getHeaderValue(name) match {
      case Some(v1) => v1 == value
      case None     => false
    }

  def hasHeader(name: CharSequence): Boolean =
    getHeaderValue(name).nonEmpty
}

object HeaderExtension {
  val BasicSchemeName  = "Basic"
  val BearerSchemeName = "Bearer"
}
