package zhttp.http.headers

import io.netty.handler.codec.http.HttpUtil
import io.netty.util.AsciiString.contentEqualsIgnoreCase
import zhttp.http.Headers.{BasicSchemeName, BearerSchemeName}
import zhttp.http.{Cookie, HTTP_CHARSET, Header, HeaderNames, Headers}

import java.nio.charset.Charset
import java.util.Base64
import scala.util.control.NonFatal

/**
 * Maintains a list of operators that parse and extract data from the headers.
 *
 * NOTE: Add methods here if it performs some kind of processing on the header and returns the result.
 */
trait HeaderGetters[+A] { self =>

  final def getAccept: Option[CharSequence] =
    getHeaderValue(HeaderNames.accept)

  final def getAcceptCharset: Option[CharSequence] =
    getHeaderValue(HeaderNames.acceptCharset)

  final def getAcceptEncoding: Option[CharSequence] =
    getHeaderValue(HeaderNames.acceptEncoding)

  final def getAcceptLanguage: Option[CharSequence] =
    getHeaderValue(HeaderNames.acceptLanguage)

  final def getAcceptPatch: Option[CharSequence] =
    getHeaderValue(HeaderNames.acceptPatch)

  final def getAcceptRanges: Option[CharSequence] =
    getHeaderValue(HeaderNames.acceptRanges)

  final def getAccessControlAllowCredentials: Option[Boolean] =
    getHeaderValue(HeaderNames.accessControlAllowCredentials) match {
      case Some(string) =>
        try Some(string.toBoolean)
        catch { case _: Throwable => None }
      case None         => None
    }

  final def getAccessControlAllowHeaders: Option[CharSequence] =
    getHeaderValue(HeaderNames.accessControlAllowHeaders)

  final def getAccessControlAllowMethods: Option[CharSequence] =
    getHeaderValue(HeaderNames.accessControlAllowMethods)

  final def getAccessControlAllowOrigin: Option[CharSequence] =
    getHeaderValue(HeaderNames.accessControlAllowOrigin)

  final def getAccessControlExposeHeaders: Option[CharSequence] =
    getHeaderValue(HeaderNames.accessControlExposeHeaders)

  final def getAccessControlMaxAge: Option[CharSequence] =
    getHeaderValue(HeaderNames.accessControlMaxAge)

  final def getAccessControlRequestHeaders: Option[CharSequence] =
    getHeaderValue(HeaderNames.accessControlRequestHeaders)

  final def getAccessControlRequestMethod: Option[CharSequence] =
    getHeaderValue(HeaderNames.accessControlRequestMethod)

  final def getAge: Option[CharSequence] =
    getHeaderValue(HeaderNames.age)

  final def getAllow: Option[CharSequence] =
    getHeaderValue(HeaderNames.allow)

  final def getAuthorization: Option[CharSequence] =
    getHeaderValue(HeaderNames.authorization)

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

  final def getCacheControl: Option[CharSequence] =
    getHeaderValue(HeaderNames.cacheControl)

  final def getCharset: Charset =
    getHeaderValue(HeaderNames.contentType) match {
      case Some(value) => HttpUtil.getCharset(value, HTTP_CHARSET)
      case None        => HTTP_CHARSET
    }

  final def getConnection: Option[CharSequence] =
    getHeaderValue(HeaderNames.connection)

  final def getContentBase: Option[CharSequence] =
    getHeaderValue(HeaderNames.contentBase)

  final def getContentDisposition: Option[CharSequence] =
    getHeaderValue(HeaderNames.contentDisposition)

  final def getContentEncoding: Option[CharSequence] =
    getHeaderValue(HeaderNames.contentEncoding)

  final def getContentLanguage: Option[CharSequence] =
    getHeaderValue(HeaderNames.contentLanguage)

  final def getContentLength: Option[Long] =
    getHeaderValue(HeaderNames.contentLength) match {
      case Some(str) =>
        try Some(str.toString.toLong)
        catch {
          case _: Throwable => None
        }
      case None      => None
    }

  final def getContentLocation: Option[CharSequence] =
    getHeaderValue(HeaderNames.contentLocation)

  final def getContentMd5: Option[CharSequence] =
    getHeaderValue(HeaderNames.contentMd5)

  final def getContentRange: Option[CharSequence] =
    getHeaderValue(HeaderNames.contentRange)

  final def getContentSecurityPolicy: Option[CharSequence] =
    getHeaderValue(HeaderNames.contentSecurityPolicy)

  final def getContentTransferEncoding: Option[CharSequence] =
    getHeaderValue(HeaderNames.contentTransferEncoding)

  final def getContentType: Option[CharSequence] =
    getHeaderValue(HeaderNames.contentType)

  final def getCookie: Option[CharSequence] =
    getHeaderValue(HeaderNames.cookie)

  final def getCookiesDecoded: List[Cookie] =
    getHeaderValues(HeaderNames.cookie).flatMap { header =>
      Cookie.decodeRequestCookie(header) match {
        case None       => Nil
        case Some(list) => list
      }
    }

  final def getDate: Option[CharSequence] =
    getHeaderValue(HeaderNames.date)

  final def getDnt: Option[CharSequence] =
    getHeaderValue(HeaderNames.dnt)

  final def getEtag: Option[CharSequence] =
    getHeaderValue(HeaderNames.etag)

  final def getExpect: Option[CharSequence] =
    getHeaderValue(HeaderNames.expect)

  final def getExpires: Option[CharSequence] =
    getHeaderValue(HeaderNames.expires)

  final def getFrom: Option[CharSequence] =
    getHeaderValue(HeaderNames.from)

  final def getHeader(headerName: CharSequence): Option[Header] =
    getHeaders.toList
      .find(h => contentEqualsIgnoreCase(h._1, headerName))

  final def getHeaderValue(headerName: CharSequence): Option[String] =
    getHeader(headerName).map(_._2.toString)

  final def getHeaderValues(headerName: CharSequence): List[String] =
    getHeaders.toList.collect { case h if contentEqualsIgnoreCase(h._1, headerName) => h._2.toString }

  /**
   * Returns the Headers object on the current type A
   */
  def getHeaders: Headers

  final def getHeadersAsList: List[(String, String)] = self.getHeaders.toList

  final def getHost: Option[CharSequence] =
    getHeaderValue(HeaderNames.host)

  final def getIfMatch: Option[CharSequence] =
    getHeaderValue(HeaderNames.ifMatch)

  final def getIfModifiedSince: Option[CharSequence] =
    getHeaderValue(HeaderNames.ifModifiedSince)

  final def getIfNoneMatch: Option[CharSequence] =
    getHeaderValue(HeaderNames.ifNoneMatch)

  final def getIfRange: Option[CharSequence] =
    getHeaderValue(HeaderNames.ifRange)

  final def getIfUnmodifiedSince: Option[CharSequence] =
    getHeaderValue(HeaderNames.ifUnmodifiedSince)

  final def getLastModified: Option[CharSequence] =
    getHeaderValue(HeaderNames.lastModified)

  final def getLocation: Option[CharSequence] =
    getHeaderValue(HeaderNames.location)

  final def getMaxForwards: Option[CharSequence] =
    getHeaderValue(HeaderNames.maxForwards)

  final def getOrigin: Option[CharSequence] =
    getHeaderValue(HeaderNames.origin)

  final def getPragma: Option[CharSequence] =
    getHeaderValue(HeaderNames.pragma)

  final def getProxyAuthenticate: Option[CharSequence] =
    getHeaderValue(HeaderNames.proxyAuthenticate)

  final def getProxyAuthorization: Option[CharSequence] =
    getHeaderValue(HeaderNames.proxyAuthorization)

  final def getRange: Option[CharSequence] =
    getHeaderValue(HeaderNames.range)

  final def getReferer: Option[CharSequence] =
    getHeaderValue(HeaderNames.referer)

  final def getRetryAfter: Option[CharSequence] =
    getHeaderValue(HeaderNames.retryAfter)

  final def getSecWebSocketAccept: Option[CharSequence] =
    getHeaderValue(HeaderNames.secWebSocketAccept)

  final def getSecWebSocketExtensions: Option[CharSequence] =
    getHeaderValue(HeaderNames.secWebSocketExtensions)

  final def getSecWebSocketKey: Option[CharSequence] =
    getHeaderValue(HeaderNames.secWebSocketKey)

  final def getSecWebSocketLocation: Option[CharSequence] =
    getHeaderValue(HeaderNames.secWebSocketLocation)

  final def getSecWebSocketOrigin: Option[CharSequence] =
    getHeaderValue(HeaderNames.secWebSocketOrigin)

  final def getSecWebSocketProtocol: Option[CharSequence] =
    getHeaderValue(HeaderNames.secWebSocketProtocol)

  final def getSecWebSocketVersion: Option[CharSequence] =
    getHeaderValue(HeaderNames.secWebSocketVersion)

  final def getServer: Option[CharSequence] =
    getHeaderValue(HeaderNames.server)

  final def getSetCookie: Option[CharSequence] =
    getHeaderValue(HeaderNames.setCookie)

  final def getSetCookiesDecoded: List[Cookie] =
    getHeaderValues(HeaderNames.cookie)
      .map(Cookie.decodeResponseCookie)
      .collect { case Some(cookie) => cookie }

  final def getTe: Option[CharSequence] =
    getHeaderValue(HeaderNames.te)

  final def getTrailer: Option[CharSequence] =
    getHeaderValue(HeaderNames.trailer)

  final def getTransferEncoding: Option[CharSequence] =
    getHeaderValue(HeaderNames.transferEncoding)

  final def getUpgrade: Option[CharSequence] =
    getHeaderValue(HeaderNames.upgrade)

  final def getUpgradeInsecureRequests: Option[CharSequence] =
    getHeaderValue(HeaderNames.upgradeInsecureRequests)

  final def getUserAgent: Option[CharSequence] =
    getHeaderValue(HeaderNames.userAgent)

  final def getVary: Option[CharSequence] =
    getHeaderValue(HeaderNames.vary)

  final def getVia: Option[CharSequence] =
    getHeaderValue(HeaderNames.via)

  final def getWarning: Option[CharSequence] =
    getHeaderValue(HeaderNames.warning)

  final def getWebSocketLocation: Option[CharSequence] =
    getHeaderValue(HeaderNames.webSocketLocation)

  final def getWebSocketOrigin: Option[CharSequence] =
    getHeaderValue(HeaderNames.webSocketOrigin)

  final def getWebSocketProtocol: Option[CharSequence] =
    getHeaderValue(HeaderNames.webSocketProtocol)

  final def getWwwAuthenticate: Option[CharSequence] =
    getHeaderValue(HeaderNames.wwwAuthenticate)

  final def getXFrameOptions: Option[CharSequence] =
    getHeaderValue(HeaderNames.xFrameOptions)

  final def getXRequestedWith: Option[CharSequence] =
    getHeaderValue(HeaderNames.xRequestedWith)

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

}
