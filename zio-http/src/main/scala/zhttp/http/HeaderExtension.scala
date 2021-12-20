package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues, HttpUtil}
import io.netty.util.AsciiString
import io.netty.util.AsciiString.toLowerCase
import zhttp.http.HeaderExtension.{BasicSchemeName, BearerSchemeName}

import java.nio.charset.Charset
import java.util.Base64
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

private[zhttp] trait HeaderExtension[+A] { self: A =>

  final def addHeader(header: Headers): A = addHeaders(header)

  final def addHeader(name: CharSequence, value: CharSequence): A = addHeader(Headers(name, value))

  final def addHeaders(headers: Headers): A = updateHeaders(list => list ++ headers)

  final def getAuthorization: Option[String] =
    getHeaderValue(HttpHeaderNames.AUTHORIZATION)

  final def getBasicAuthorizationCredentials: Option[Header] = {
    getAuthorization
      .flatMap(v => {
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

  final def getBearerToken: Option[String] = getAuthorization
    .flatMap(v => {
      val indexOfBearer = v.indexOf(BearerSchemeName)
      if (indexOfBearer != 0 || v.length == BearerSchemeName.length)
        None
      else
        Some(v.substring(BearerSchemeName.length + 1))
    })

  final def getCharset: Charset =
    getHeaderValue(HttpHeaderNames.CONTENT_TYPE) match {
      case Some(value) => HttpUtil.getCharset(value, HTTP_CHARSET)
      case None        => HTTP_CHARSET
    }

  final def getContentLength: Option[Long] =
    getHeaderValue(HttpHeaderNames.CONTENT_LENGTH).flatMap(a =>
      Try(a.toLong) match {
        case Failure(_)     => None
        case Success(value) => Some(value)
      },
    )

  final def getContentType: Option[String] =
    getHeaderValue(HttpHeaderNames.CONTENT_TYPE)

  final def getCookies(implicit ev: HasCookie[A]): List[Cookie] = ev.decode(self)

  final def getCookiesRaw(implicit ev: HasCookie[A]): List[CharSequence] = ev.headers(self)

  final def getHeader(headerName: CharSequence): Option[Header] =
    getHeaders.toList
      .find(h => contentEqualsIgnoreCase(h._1, headerName))
      .map { case (name, value) => (name.toString, value.toString) }

  final def getHeaderValue(headerName: CharSequence): Option[String] =
    getHeader(headerName).map(_._2.toString)

  final def getHeaderValues(headerName: CharSequence): List[String] =
    getHeaders.toList.collect { case h if contentEqualsIgnoreCase(h._1, headerName) => h._2.toString }

  def getHeaders: Headers

  final def hasHeader(name: CharSequence, value: CharSequence): Boolean =
    getHeaderValue(name) match {
      case Some(v1) => v1 == value
      case None     => false
    }

  final def hasHeader(name: CharSequence): Boolean =
    getHeaderValue(name).nonEmpty

  final def isFormUrlencodedContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)

  final def isJsonContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_JSON)

  final def isTextPlainContentType: Boolean =
    checkContentType(HttpHeaderValues.TEXT_PLAIN)

  final def isXhtmlXmlContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_XHTML)

  final def isXmlContentType: Boolean =
    checkContentType(HttpHeaderValues.APPLICATION_XML)

  final def removeHeader(name: String): A = removeHeaders(List(name))

  final def removeHeaders(headers: List[String]): A =
    updateHeaders(orig => Headers(orig.toList.filterNot(h => headers.contains(h._1))))

  final def setChunkedEncoding: A =
    addHeader(Headers(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED))

  final def setContentLength(value: Long): A =
    addHeader(Headers(HttpHeaderNames.CONTENT_LENGTH, value.toString))

  def updateHeaders(f: Headers => Headers): A

  private def checkContentType(value: AsciiString): Boolean =
    getContentType
      .exists(v => value.contentEquals(v))

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

  private def decodeHttpBasic(encoded: String): Option[Header] = {
    val decoded    = new String(Base64.getDecoder.decode(encoded))
    val colonIndex = decoded.indexOf(":")
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

  private[zhttp] final def getHeadersAsList: List[(String, String)] =
    self.getHeaders.toList.map { case (name, value) =>
      (name.toString, value.toString)
    }
}

object HeaderExtension {
  val BasicSchemeName  = "Basic"
  val BearerSchemeName = "Bearer"
}
