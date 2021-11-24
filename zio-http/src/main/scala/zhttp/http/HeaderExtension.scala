package zhttp.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.base64.Base64
import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues, HttpUtil}
import io.netty.util.AsciiString.toLowerCase
import io.netty.util.{AsciiString, CharsetUtil}
import zhttp.http.HeaderExtension.{BasicSchemeName, BearerSchemeName}

import java.nio.charset.Charset
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

private[zhttp] trait HeaderExtension[+A] { self: A =>
  def getHeaders: List[Header]

  def updateHeaders(f: List[Header] => List[Header]): A

  final def addHeaders(headers: List[Header]): A = updateHeaders(list => list ++ headers)

  final def removeHeaders(headers: List[String]): A =
    updateHeaders(orig => orig.filterNot(h => headers.contains(h.name)))

  final def addHeader(header: Header): A = addHeaders(List(header))

  final def addHeader(name: CharSequence, value: CharSequence): A = addHeader(Header(name, value))

  final def removeHeader(name: String): A = removeHeaders(List(name))

  final def setContentLength(value: Long): A =
    addHeader(Header(HttpHeaderNames.CONTENT_LENGTH, value.toString))

  final def setChunkedEncoding: A =
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

  final def getHeaderValue(headerName: CharSequence): Option[String] =
    getHeader(headerName).map(_.value.toString)

  final def getHeader(headerName: CharSequence): Option[Header] =
    getHeaders.find(h => contentEqualsIgnoreCase(h.name, headerName))

  final def getHeaderValues(headerName: CharSequence): List[String] =
    getHeaders.filter(h => contentEqualsIgnoreCase(h.name, headerName)).map(_.value.toString)

  final def getContentType: Option[String] =
    getHeaderValue(HttpHeaderNames.CONTENT_TYPE)

  final def getContentLength: Option[Long] =
    getHeaderValue(HttpHeaderNames.CONTENT_LENGTH).flatMap(a =>
      Try(a.toLong) match {
        case Failure(_)     => None
        case Success(value) => Some(value)
      },
    )

  private def checkContentType(value: AsciiString): Boolean =
    getContentType
      .exists(v => value.contentEquals(v))

  final def isJsonContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_JSON)

  final def isTextPlainContentType: Boolean =
    checkContentType(HttpHeaderValues.TEXT_PLAIN)

  final def isXmlContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_XML)

  final def isXhtmlXmlContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_XHTML)

  final def isFormUrlencodedContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)

  final def getAuthorization: Option[String] =
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

  final def getBasicAuthorizationCredentials: Option[(String, String)] = {
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

  final def getBearerToken: Option[String] = getAuthorization.flatMap(v => {
    val indexOfBearer = v.indexOf(BearerSchemeName)
    if (indexOfBearer != 0 || v.length == BearerSchemeName.length)
      None
    else
      Some(v.substring(BearerSchemeName.length + 1))
  })

  final def getCharset: Option[Charset] =
    getHeaderValue(HttpHeaderNames.CONTENT_TYPE).map(HttpUtil.getCharset(_, HTTP_CHARSET))

  final def hasHeader(name: CharSequence, value: CharSequence): Boolean =
    getHeaderValue(name) match {
      case Some(v1) => v1 == value
      case None     => false
    }

  final def hasHeader(name: CharSequence): Boolean =
    getHeaderValue(name).nonEmpty

}

object HeaderExtension {
  val BasicSchemeName  = "Basic"
  val BearerSchemeName = "Bearer"

  case class Only(getHeaders: List[Header]) extends HeaderExtension[Only] {
    override def updateHeaders(f: List[Header] => List[Header]): Only = Only(f(getHeaders))
  }

  def empty: HeaderExtension[Only]                        = Only(Nil)
  def apply(headers: List[Header]): HeaderExtension[Only] = Only(headers)
}
