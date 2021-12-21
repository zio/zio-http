package zhttp.http.headers

import io.netty.handler.codec.http.HttpUtil
import io.netty.util.AsciiString
import io.netty.util.AsciiString.toLowerCase
import zhttp.http.Headers.{BasicSchemeName, BearerSchemeName}
import zhttp.http._

import java.nio.charset.Charset
import java.util.Base64
import scala.util.control.NonFatal

private[zhttp] trait HeaderExtension[+A] extends WithHeader[A] with HeaderGetters[A] { self: A =>
  import Headers.Literals._

  final def addHeader(header: Header): A = addHeaders(Headers(header))

  final def addHeader(name: CharSequence, value: CharSequence): A = addHeaders(Headers(name, value))

  final def addHeaders(headers: Headers): A = updateHeaders(_ ++ headers)

  final def getBasicAuthorizationCredentials: Option[Header] = {
    getAuthorization
      .map(_.toString)
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
    .map(_.toString)
    .flatMap(v => {
      val indexOfBearer = v.indexOf(BearerSchemeName)
      if (indexOfBearer != 0 || v.length == BearerSchemeName.length)
        None
      else
        Some(v.substring(BearerSchemeName.length + 1))
    })

  final def getCharset: Charset =
    getHeaderValue(Name.ContentType) match {
      case Some(value) => HttpUtil.getCharset(value, HTTP_CHARSET)
      case None        => HTTP_CHARSET
    }

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

  final def hasContentType(value: CharSequence): Boolean =
    getContentType.exists(contentEqualsIgnoreCase(value, _))

  final def hasFormUrlencodedContentType: Boolean =
    hasContentType(Value.ApplicationXWWWFormUrlencoded)

  final def hasHeader(name: CharSequence, value: CharSequence): Boolean =
    getHeaderValue(name) match {
      case Some(v1) => v1 == value
      case None     => false
    }

  final def hasHeader(name: CharSequence): Boolean =
    getHeaderValue(name).nonEmpty

  final def hasJsonContentType: Boolean =
    hasContentType(Value.ApplicationJson)

  final def hasTextPlainContentType: Boolean =
    hasContentType(Value.TextPlain)

  final def hasXhtmlXmlContentType: Boolean =
    hasContentType(Value.ApplicationXhtml)

  final def hasXmlContentType: Boolean =
    hasContentType(Value.ApplicationXml)

  final def removeHeader(name: String): A = removeHeaders(List(name))

  final def removeHeaders(headers: List[String]): A =
    updateHeaders(orig => Headers(orig.toList.filterNot(h => headers.contains(h._1))))

  def updateHeaders(f: Headers => Headers): A

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

  private[zhttp] final def getHeadersAsList: List[(String, String)] = self.getHeaders.toList
}

object HeaderExtension {}
