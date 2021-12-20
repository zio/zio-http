package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderValues, HttpUtil}
import io.netty.util.AsciiString
import io.netty.util.AsciiString.toLowerCase
import zhttp.http.HeaderExtension.{BasicSchemeName, BearerSchemeName}

import java.nio.charset.Charset
import java.util.Base64
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

private[zhttp] trait HeaderExtension[+A] { self: A =>
  import HeaderName._

  final def addHeader(header: Headers): A = addHeaders(header)

  final def addHeader(name: CharSequence, value: CharSequence): A = addHeader(Headers(name, value))

  final def addHeaders(headers: Headers): A = updateHeaders(_ ++ headers)

  final def getAuthorization: Option[String] =
    getHeaderValue(`authorization`)

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
    getHeaderValue(`content-type`) match {
      case Some(value) => HttpUtil.getCharset(value, HTTP_CHARSET)
      case None        => HTTP_CHARSET
    }

  final def getContentLength: Option[Long] =
    getHeaderValue(`content-length`).flatMap(a =>
      Try(a.toLong) match {
        case Failure(_)     => None
        case Success(value) => Some(value)
      },
    )

  final def getContentType: Option[String] =
    getHeaderValue(`content-type`)

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
    hasContentType(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)

  final def hasHeader(name: CharSequence, value: CharSequence): Boolean =
    getHeaderValue(name) match {
      case Some(v1) => v1 == value
      case None     => false
    }

  final def hasHeader(name: CharSequence): Boolean =
    getHeaderValue(name).nonEmpty

  final def hasJsonContentType: Boolean =
    hasContentType(HttpHeaderValues.APPLICATION_JSON)

  final def hasTextPlainContentType: Boolean =
    hasContentType(HttpHeaderValues.TEXT_PLAIN)

  final def hasXhtmlXmlContentType: Boolean =
    hasContentType(HttpHeaderValues.APPLICATION_XHTML)

  final def hasXmlContentType: Boolean =
    hasContentType(HttpHeaderValues.APPLICATION_XML)

  final def removeHeader(name: String): A = removeHeaders(List(name))

  final def removeHeaders(headers: List[String]): A =
    updateHeaders(orig => Headers(orig.toList.filterNot(h => headers.contains(h._1))))

  def updateHeaders(f: Headers => Headers): A

  final def withAcceptCharsetHeader(value: CharSequence): A =
    addHeader(`accept-charset`, value)

  final def withAcceptEncodingHeader(value: CharSequence): A =
    addHeader(`accept-encoding`, value)

  final def withAcceptHeader(value: CharSequence): A =
    addHeader(`accept`, value)

  final def withAcceptLanguageHeader(value: CharSequence): A =
    addHeader(`accept-language`, value)

  final def withAcceptPatchHeader(value: CharSequence): A =
    addHeader(`accept-patch`, value)

  final def withAcceptRangesHeader(value: CharSequence): A =
    addHeader(`accept-ranges`, value)

  final def withAccessControlAllowCredentialsHeader(value: Boolean): A =
    addHeader(`access-control-allow-credentials`, value.toString)

  final def withAccessControlAllowHeadersHeader(value: CharSequence): A =
    addHeader(`access-control-allow-headers`, value)

  final def withAccessControlAllowMethodsHeader(value: CharSequence): A =
    addHeader(`access-control-allow-methods`, value)

  final def withAccessControlAllowOriginHeader(value: CharSequence): A =
    addHeader(`access-control-allow-origin`, value)

  final def withAccessControlExposeHeadersHeader(value: CharSequence): A =
    addHeader(`access-control-expose-headers`, value)

  final def withAccessControlMaxAgeHeader(value: CharSequence): A =
    addHeader(`access-control-max-age`, value)

  final def withAccessControlRequestHeadersHeader(value: CharSequence): A =
    addHeader(`access-control-request-headers`, value)

  final def withAccessControlRequestMethodHeader(value: CharSequence): A =
    addHeader(`access-control-request-method`, value)

  final def withAgeHeader(value: CharSequence): A =
    addHeader(`age`, value)

  final def withAllowHeader(value: CharSequence): A =
    addHeader(`allow`, value)

  final def withAuthorizationHeader(value: CharSequence): A =
    addHeader(`authorization`, value)

  final def withCacheControlHeader(value: CharSequence): A =
    addHeader(`cache-control`, value)

  final def withConnectionHeader(value: CharSequence): A =
    addHeader(`connection`, value)

  final def withContentBaseHeader(value: CharSequence): A =
    addHeader(`content-base`, value)

  final def withContentDispositionHeader(value: CharSequence): A =
    addHeader(`content-disposition`, value)

  final def withContentEncodingHeader(value: CharSequence): A =
    addHeader(`content-encoding`, value)

  final def withContentLanguageHeader(value: CharSequence): A =
    addHeader(`content-language`, value)

  final def withContentLengthHeader(value: CharSequence): A =
    addHeader(`content-length`, value)

  final def withContentLocationHeader(value: CharSequence): A =
    addHeader(`content-location`, value)

  final def withContentMd5Header(value: CharSequence): A =
    addHeader(`content-md5`, value)

  final def withContentRangeHeader(value: CharSequence): A =
    addHeader(`content-range`, value)

  final def withContentSecurityPolicyHeader(value: CharSequence): A =
    addHeader(`content-security-policy`, value)

  final def withContentTransferEncodingHeader(value: CharSequence): A =
    addHeader(`content-transfer-encoding`, value)

  final def withContentTypeHeader(value: CharSequence): A =
    addHeader(`content-type`, value)

  final def withCookieHeader(value: CharSequence): A =
    addHeader(`cookie`, value)

  final def withDateHeader(value: CharSequence): A =
    addHeader(HeaderName.`date`, value)

  final def withDntHeader(value: CharSequence): A =
    addHeader(`dnt`, value)

  final def withEtagHeader(value: CharSequence): A =
    addHeader(`etag`, value)

  final def withExpectHeader(value: CharSequence): A =
    addHeader(`expect`, value)

  final def withExpiresHeader(value: CharSequence): A =
    addHeader(`expires`, value)

  final def withFromHeader(value: CharSequence): A =
    addHeader(`from`, value)

  final def withHostHeader(value: CharSequence): A =
    addHeader(`host`, value)

  final def withIfMatchHeader(value: CharSequence): A =
    addHeader(`if-match`, value)

  final def withIfModifiedSinceHeader(value: CharSequence): A =
    addHeader(`if-modified-since`, value)

  final def withIfNoneMatchHeader(value: CharSequence): A =
    addHeader(`if-none-match`, value)

  final def withIfRangeHeader(value: CharSequence): A =
    addHeader(`if-range`, value)

  final def withIfUnmodifiedSinceHeader(value: CharSequence): A =
    addHeader(`if-unmodified-since`, value)

  final def withLastModifiedHeader(value: CharSequence): A =
    addHeader(`last-modified`, value)

  final def withLocationHeader(value: CharSequence): A =
    addHeader(`location`, value)

  final def withMaxForwardsHeader(value: CharSequence): A =
    addHeader(`max-forwards`, value)

  final def withOriginHeader(value: CharSequence): A =
    addHeader(`origin`, value)

  final def withPragmaHeader(value: CharSequence): A =
    addHeader(`pragma`, value)

  final def withProxyAuthenticateHeader(value: CharSequence): A =
    addHeader(`proxy-authenticate`, value)

  final def withProxyAuthorizationHeader(value: CharSequence): A =
    addHeader(`proxy-authorization`, value)

  final def withRangeHeader(value: CharSequence): A =
    addHeader(`range`, value)

  final def withRefererHeader(value: CharSequence): A =
    addHeader(`referer`, value)

  final def withRetryAfterHeader(value: CharSequence): A =
    addHeader(`retry-after`, value)

  final def withSecWebSocketAcceptHeader(value: CharSequence): A =
    addHeader(`sec-websocket-accept`, value)

  final def withSecWebSocketExtensionsHeader(value: CharSequence): A =
    addHeader(`sec-websocket-extensions`, value)

  final def withSecWebSocketKeyHeader(value: CharSequence): A =
    addHeader(`sec-websocket-key`, value)

  final def withSecWebSocketLocationHeader(value: CharSequence): A =
    addHeader(`sec-websocket-location`, value)

  final def withSecWebSocketOriginHeader(value: CharSequence): A =
    addHeader(`sec-websocket-origin`, value)

  final def withSecWebSocketProtocolHeader(value: CharSequence): A =
    addHeader(`sec-websocket-protocol`, value)

  final def withSecWebSocketVersionHeader(value: CharSequence): A =
    addHeader(`sec-websocket-version`, value)

  final def withServerHeader(value: CharSequence): A =
    addHeader(`server`, value)

  final def withSetCookieHeader(value: CharSequence): A =
    addHeader(`set-cookie`, value)

  final def withTeHeader(value: CharSequence): A =
    addHeader(`te`, value)

  final def withTrailerHeader(value: CharSequence): A =
    addHeader(`trailer`, value)

  final def withTransferEncodingHeader(value: CharSequence): A =
    addHeader(`transfer-encoding`, value)

  final def withUpgradeHeader(value: CharSequence): A =
    addHeader(`upgrade`, value)

  final def withUpgradeInsecureRequestsHeader(value: CharSequence): A =
    addHeader(`upgrade-insecure-requests`, value)

  final def withUserAgentHeader(value: CharSequence): A =
    addHeader(`user-agent`, value)

  final def withVaryHeader(value: CharSequence): A =
    addHeader(`vary`, value)

  final def withViaHeader(value: CharSequence): A =
    addHeader(`via`, value)

  final def withWarningHeader(value: CharSequence): A =
    addHeader(`warning`, value)

  final def withWebSocketLocationHeader(value: CharSequence): A =
    addHeader(`websocket-location`, value)

  final def withWebSocketOriginHeader(value: CharSequence): A =
    addHeader(`websocket-origin`, value)

  final def withWebSocketProtocolHeader(value: CharSequence): A =
    addHeader(`websocket-protocol`, value)

  final def withWwwAuthenticateHeader(value: CharSequence): A =
    addHeader(`www-authenticate`, value)

  final def withXFrameOptionsHeader(value: CharSequence): A =
    addHeader(`x-frame-options`, value)

  final def withXRequestedWithHeader(value: CharSequence): A =
    addHeader(`x-requested-with`, value)

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
