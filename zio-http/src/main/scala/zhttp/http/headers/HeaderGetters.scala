package zhttp.http.headers

import io.netty.handler.codec.http.HttpUtil
import io.netty.util.AsciiString.contentEqualsIgnoreCase
import zhttp.http.Headers.{BasicSchemeName, BearerSchemeName}
import zhttp.http._
import zhttp.http.middleware.Auth.Credentials
import zhttp.service.server.ServerTime

import java.nio.charset.Charset
import java.util.{Base64, Date}
import scala.util.control.NonFatal

/**
 * Maintains a list of operators that parse and extract data from the headers.
 *
 * NOTE: Add methods here if it performs some kind of processing on the header
 * and returns the result.
 */
trait HeaderGetters[+A] { self =>

  final def accept: Option[CharSequence] =
    headerValue(HeaderNames.accept)

  final def acceptCharset: Option[CharSequence] =
    headerValue(HeaderNames.acceptCharset)

  final def acceptEncoding: Option[CharSequence] =
    headerValue(HeaderNames.acceptEncoding)

  final def acceptLanguage: Option[CharSequence] =
    headerValue(HeaderNames.acceptLanguage)

  final def acceptPatch: Option[CharSequence] =
    headerValue(HeaderNames.acceptPatch)

  final def acceptRanges: Option[CharSequence] =
    headerValue(HeaderNames.acceptRanges)

  final def accessControlAllowCredentials: Option[Boolean] =
    headerValue(HeaderNames.accessControlAllowCredentials) match {
      case Some(string) =>
        try Some(string.toBoolean)
        catch { case _: Throwable => None }
      case None         => None
    }

  final def accessControlAllowHeaders: Option[CharSequence] =
    headerValue(HeaderNames.accessControlAllowHeaders)

  final def accessControlAllowMethods: Option[CharSequence] =
    headerValue(HeaderNames.accessControlAllowMethods)

  final def accessControlAllowOrigin: Option[CharSequence] =
    headerValue(HeaderNames.accessControlAllowOrigin)

  final def accessControlExposeHeaders: Option[CharSequence] =
    headerValue(HeaderNames.accessControlExposeHeaders)

  final def accessControlMaxAge: Option[CharSequence] =
    headerValue(HeaderNames.accessControlMaxAge)

  final def accessControlRequestHeaders: Option[CharSequence] =
    headerValue(HeaderNames.accessControlRequestHeaders)

  final def accessControlRequestMethod: Option[CharSequence] =
    headerValue(HeaderNames.accessControlRequestMethod)

  final def age: Option[CharSequence] =
    headerValue(HeaderNames.age)

  final def allow: Option[CharSequence] =
    headerValue(HeaderNames.allow)

  final def authorization: Option[CharSequence] =
    headerValue(HeaderNames.authorization)

  final def basicAuthorizationCredentials: Option[Credentials] = {
    authorization
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

  final def bearerToken: Option[String] = authorization
    .map(_.toString)
    .flatMap(v => {
      val indexOfBearer = v.indexOf(BearerSchemeName)
      if (indexOfBearer != 0 || v.length == BearerSchemeName.length)
        None
      else
        Some(v.substring(BearerSchemeName.length + 1))
    })

  final def cacheControl: Option[CharSequence] =
    headerValue(HeaderNames.cacheControl)

  final def charset: Charset =
    headerValue(HeaderNames.contentType) match {
      case Some(value) => HttpUtil.getCharset(value, HTTP_CHARSET)
      case None        => HTTP_CHARSET
    }

  final def connection: Option[CharSequence] =
    headerValue(HeaderNames.connection)

  final def contentBase: Option[CharSequence] =
    headerValue(HeaderNames.contentBase)

  final def contentDisposition: Option[CharSequence] =
    headerValue(HeaderNames.contentDisposition)

  final def contentEncoding: Option[CharSequence] =
    headerValue(HeaderNames.contentEncoding)

  final def contentLanguage: Option[CharSequence] =
    headerValue(HeaderNames.contentLanguage)

  final def contentLength: Option[Long] =
    headerValue(HeaderNames.contentLength) match {
      case Some(str) =>
        try Some(str.toString.toLong)
        catch {
          case _: Throwable => None
        }
      case None      => None
    }

  final def contentLocation: Option[CharSequence] =
    headerValue(HeaderNames.contentLocation)

  final def contentMd5: Option[CharSequence] =
    headerValue(HeaderNames.contentMd5)

  final def contentRange: Option[CharSequence] =
    headerValue(HeaderNames.contentRange)

  final def contentSecurityPolicy: Option[CharSequence] =
    headerValue(HeaderNames.contentSecurityPolicy)

  final def contentTransferEncoding: Option[CharSequence] =
    headerValue(HeaderNames.contentTransferEncoding)

  final def contentType: Option[CharSequence] =
    headerValue(HeaderNames.contentType)

  final def cookie: Option[CharSequence] =
    headerValue(HeaderNames.cookie)

  final def cookieValue(name: CharSequence): Option[CharSequence] =
    cookiesDecoded.find(_.name == name).map(_.content)

  final def cookiesDecoded: List[Cookie] =
    headerValues(HeaderNames.cookie).flatMap { header =>
      Cookie.decodeRequestCookie(header)
    }

  final def date: Option[CharSequence] =
    headerValue(HeaderNames.date)

  final def dnt: Option[CharSequence] =
    headerValue(HeaderNames.dnt)

  final def etag: Option[CharSequence] =
    headerValue(HeaderNames.etag)

  final def expect: Option[CharSequence] =
    headerValue(HeaderNames.expect)

  final def expires: Option[CharSequence] =
    headerValue(HeaderNames.expires)

  final def from: Option[CharSequence] =
    headerValue(HeaderNames.from)

  final def header(headerName: CharSequence): Option[Header] =
    headers.toList
      .find(h => contentEqualsIgnoreCase(h._1, headerName))

  final def headerValue(headerName: CharSequence): Option[String] =
    header(headerName).map(_._2.toString)

  final def headerValues(headerName: CharSequence): List[String] =
    headers.toList.collect { case h if contentEqualsIgnoreCase(h._1, headerName) => h._2.toString }

  /**
   * Returns the Headers object on the current type A
   */
  def headers: Headers

  final def headersAsList: List[(String, String)] = self.headers.toList

  final def host: Option[CharSequence] =
    headerValue(HeaderNames.host)

  final def ifMatch: Option[CharSequence] =
    headerValue(HeaderNames.ifMatch)

  final def ifModifiedSince: Option[CharSequence] =
    headerValue(HeaderNames.ifModifiedSince)

  final def ifModifiedSinceDecoded: Option[Date] =
    ifModifiedSince.map(date => ServerTime.parse(date.toString))

  final def ifNoneMatch: Option[CharSequence] =
    headerValue(HeaderNames.ifNoneMatch)

  final def ifRange: Option[CharSequence] =
    headerValue(HeaderNames.ifRange)

  final def ifUnmodifiedSince: Option[CharSequence] =
    headerValue(HeaderNames.ifUnmodifiedSince)

  final def lastModified: Option[CharSequence] =
    headerValue(HeaderNames.lastModified)

  final def location: Option[CharSequence] =
    headerValue(HeaderNames.location)

  final def maxForwards: Option[CharSequence] =
    headerValue(HeaderNames.maxForwards)

  final def mediaType: Option[MediaType] =
    contentType.flatMap(ct => MediaType.forContentType(ct.toString))

  final def origin: Option[CharSequence] =
    headerValue(HeaderNames.origin)

  final def pragma: Option[CharSequence] =
    headerValue(HeaderNames.pragma)

  final def proxyAuthenticate: Option[CharSequence] =
    headerValue(HeaderNames.proxyAuthenticate)

  final def proxyAuthorization: Option[CharSequence] =
    headerValue(HeaderNames.proxyAuthorization)

  final def range: Option[CharSequence] =
    headerValue(HeaderNames.range)

  final def referer: Option[CharSequence] =
    headerValue(HeaderNames.referer)

  final def retryAfter: Option[CharSequence] =
    headerValue(HeaderNames.retryAfter)

  final def secWebSocketAccept: Option[CharSequence] =
    headerValue(HeaderNames.secWebSocketAccept)

  final def secWebSocketExtensions: Option[CharSequence] =
    headerValue(HeaderNames.secWebSocketExtensions)

  final def secWebSocketKey: Option[CharSequence] =
    headerValue(HeaderNames.secWebSocketKey)

  final def secWebSocketLocation: Option[CharSequence] =
    headerValue(HeaderNames.secWebSocketLocation)

  final def secWebSocketOrigin: Option[CharSequence] =
    headerValue(HeaderNames.secWebSocketOrigin)

  final def secWebSocketProtocol: Option[CharSequence] =
    headerValue(HeaderNames.secWebSocketProtocol)

  final def secWebSocketVersion: Option[CharSequence] =
    headerValue(HeaderNames.secWebSocketVersion)

  final def server: Option[CharSequence] =
    headerValue(HeaderNames.server)

  final def setCookie: Option[CharSequence] =
    headerValue(HeaderNames.setCookie)

  final def setCookiesDecoded(secret: Option[String] = None): List[Cookie] =
    headerValues(HeaderNames.setCookie)
      .map(Cookie.decodeResponseCookie(_, secret))
      .collect { case Some(cookie) => cookie }

  final def te: Option[CharSequence] =
    headerValue(HeaderNames.te)

  final def trailer: Option[CharSequence] =
    headerValue(HeaderNames.trailer)

  final def transferEncoding: Option[CharSequence] =
    headerValue(HeaderNames.transferEncoding)

  final def upgrade: Option[CharSequence] =
    headerValue(HeaderNames.upgrade)

  final def upgradeInsecureRequests: Option[CharSequence] =
    headerValue(HeaderNames.upgradeInsecureRequests)

  final def userAgent: Option[CharSequence] =
    headerValue(HeaderNames.userAgent)

  final def vary: Option[CharSequence] =
    headerValue(HeaderNames.vary)

  final def via: Option[CharSequence] =
    headerValue(HeaderNames.via)

  final def warning: Option[CharSequence] =
    headerValue(HeaderNames.warning)

  final def webSocketLocation: Option[CharSequence] =
    headerValue(HeaderNames.webSocketLocation)

  final def webSocketOrigin: Option[CharSequence] =
    headerValue(HeaderNames.webSocketOrigin)

  final def webSocketProtocol: Option[CharSequence] =
    headerValue(HeaderNames.webSocketProtocol)

  final def wwwAuthenticate: Option[CharSequence] =
    headerValue(HeaderNames.wwwAuthenticate)

  final def xFrameOptions: Option[CharSequence] =
    headerValue(HeaderNames.xFrameOptions)

  final def xRequestedWith: Option[CharSequence] =
    headerValue(HeaderNames.xRequestedWith)

  private def decodeHttpBasic(encoded: String): Option[Credentials] = {
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
      Some(Credentials(username, password))
    }
  }

}
