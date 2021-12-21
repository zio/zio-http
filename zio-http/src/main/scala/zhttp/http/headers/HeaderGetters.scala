package zhttp.http.headers

import zhttp.http.Headers.Literals.Name

trait HeaderGetters[+A] { self: HeaderExtension[A] with A =>

  final def getAccept: Option[CharSequence] =
    getHeaderValue(Name.Accept)

  final def getAcceptCharset: Option[CharSequence] =
    getHeaderValue(Name.AcceptCharset)

  final def getAcceptEncoding: Option[CharSequence] =
    getHeaderValue(Name.AcceptEncoding)

  final def getAcceptLanguage: Option[CharSequence] =
    getHeaderValue(Name.AcceptLanguage)

  final def getAcceptPatch: Option[CharSequence] =
    getHeaderValue(Name.AcceptPatch)

  final def getAcceptRanges: Option[CharSequence] =
    getHeaderValue(Name.AcceptRanges)

  final def getAccessControlAllowCredentials: Option[Boolean] =
    getHeaderValue(Name.AccessControlAllowCredentials) match {
      case Some(string) =>
        try Some(string.toBoolean)
        catch { case _: Throwable => None }
      case None         => None
    }

  final def getAccessControlAllowHeaders: Option[CharSequence] =
    getHeaderValue(Name.AccessControlAllowHeaders)

  final def getAccessControlAllowMethods: Option[CharSequence] =
    getHeaderValue(Name.AccessControlAllowMethods)

  final def getAccessControlAllowOrigin: Option[CharSequence] =
    getHeaderValue(Name.AccessControlAllowOrigin)

  final def getAccessControlExposeHeaders: Option[CharSequence] =
    getHeaderValue(Name.AccessControlExposeHeaders)

  final def getAccessControlMaxAge: Option[CharSequence] =
    getHeaderValue(Name.AccessControlMaxAge)

  final def getAccessControlRequestHeaders: Option[CharSequence] =
    getHeaderValue(Name.AccessControlRequestHeaders)

  final def getAccessControlRequestMethod: Option[CharSequence] =
    getHeaderValue(Name.AccessControlRequestMethod)

  final def getAge: Option[CharSequence] =
    getHeaderValue(Name.Age)

  final def getAllow: Option[CharSequence] =
    getHeaderValue(Name.Allow)

  final def getAuthorization: Option[CharSequence] =
    getHeaderValue(Name.Authorization)

  final def getCacheControl: Option[CharSequence] =
    getHeaderValue(Name.CacheControl)

  final def getConnection: Option[CharSequence] =
    getHeaderValue(Name.Connection)

  final def getContentBase: Option[CharSequence] =
    getHeaderValue(Name.ContentBase)

  final def getContentDisposition: Option[CharSequence] =
    getHeaderValue(Name.ContentDisposition)

  final def getContentEncoding: Option[CharSequence] =
    getHeaderValue(Name.ContentEncoding)

  final def getContentLanguage: Option[CharSequence] =
    getHeaderValue(Name.ContentLanguage)

  final def getContentLength: Option[Long] =
    getHeaderValue(Name.ContentLength) match {
      case Some(str) =>
        try Some(str.toString.toLong)
        catch {
          case _: Throwable => None
        }
      case None      => None
    }

  final def getContentLocation: Option[CharSequence] =
    getHeaderValue(Name.ContentLocation)

  final def getContentMd5: Option[CharSequence] =
    getHeaderValue(Name.ContentMd5)

  final def getContentRange: Option[CharSequence] =
    getHeaderValue(Name.ContentRange)

  final def getContentSecurityPolicy: Option[CharSequence] =
    getHeaderValue(Name.ContentSecurityPolicy)

  final def getContentTransferEncoding: Option[CharSequence] =
    getHeaderValue(Name.ContentTransferEncoding)

  final def getContentType: Option[CharSequence] =
    getHeaderValue(Name.ContentType)

  final def getCookie: Option[CharSequence] =
    getHeaderValue(Name.Cookie)

  final def getDate: Option[CharSequence] =
    getHeaderValue(Name.Date)

  final def getDnt: Option[CharSequence] =
    getHeaderValue(Name.Dnt)

  final def getEtag: Option[CharSequence] =
    getHeaderValue(Name.Etag)

  final def getExpect: Option[CharSequence] =
    getHeaderValue(Name.Expect)

  final def getExpires: Option[CharSequence] =
    getHeaderValue(Name.Expires)

  final def getFrom: Option[CharSequence] =
    getHeaderValue(Name.From)

  final def getHost: Option[CharSequence] =
    getHeaderValue(Name.Host)

  final def getIfMatch: Option[CharSequence] =
    getHeaderValue(Name.IfMatch)

  final def getIfModifiedSince: Option[CharSequence] =
    getHeaderValue(Name.IfModifiedSince)

  final def getIfNoneMatch: Option[CharSequence] =
    getHeaderValue(Name.IfNoneMatch)

  final def getIfRange: Option[CharSequence] =
    getHeaderValue(Name.IfRange)

  final def getIfUnmodifiedSince: Option[CharSequence] =
    getHeaderValue(Name.IfUnmodifiedSince)

  final def getLastModified: Option[CharSequence] =
    getHeaderValue(Name.LastModified)

  final def getLocation: Option[CharSequence] =
    getHeaderValue(Name.Location)

  final def getMaxForwards: Option[CharSequence] =
    getHeaderValue(Name.MaxForwards)

  final def getOrigin: Option[CharSequence] =
    getHeaderValue(Name.Origin)

  final def getPragma: Option[CharSequence] =
    getHeaderValue(Name.Pragma)

  final def getProxyAuthenticate: Option[CharSequence] =
    getHeaderValue(Name.ProxyAuthenticate)

  final def getProxyAuthorization: Option[CharSequence] =
    getHeaderValue(Name.ProxyAuthorization)

  final def getRange: Option[CharSequence] =
    getHeaderValue(Name.Range)

  final def getReferer: Option[CharSequence] =
    getHeaderValue(Name.Referer)

  final def getRetryAfter: Option[CharSequence] =
    getHeaderValue(Name.RetryAfter)

  final def getSecWebsocketAccept: Option[CharSequence] =
    getHeaderValue(Name.SecWebSocketAccept)

  final def getSecWebsocketExtensions: Option[CharSequence] =
    getHeaderValue(Name.SecWebSocketExtensions)

  final def getSecWebsocketKey: Option[CharSequence] =
    getHeaderValue(Name.SecWebSocketKey)

  final def getSecWebsocketKey1: Option[CharSequence] =
    getHeaderValue(Name.SecWebSocketKey1)

  final def getSecWebsocketKey2: Option[CharSequence] =
    getHeaderValue(Name.SecWebSocketKey2)

  final def getSecWebsocketLocation: Option[CharSequence] =
    getHeaderValue(Name.SecWebSocketLocation)

  final def getSecWebsocketOrigin: Option[CharSequence] =
    getHeaderValue(Name.SecWebSocketOrigin)

  final def getSecWebsocketProtocol: Option[CharSequence] =
    getHeaderValue(Name.SecWebSocketProtocol)

  final def getSecWebsocketVersion: Option[CharSequence] =
    getHeaderValue(Name.SecWebSocketVersion)

  final def getServer: Option[CharSequence] =
    getHeaderValue(Name.Server)

  final def getSetCookie: Option[CharSequence] =
    getHeaderValue(Name.SetCookie)

  final def getSetCookie2: Option[CharSequence] =
    getHeaderValue(Name.SetCookie2)

  final def getTe: Option[CharSequence] =
    getHeaderValue(Name.Te)

  final def getTrailer: Option[CharSequence] =
    getHeaderValue(Name.Trailer)

  final def getTransferEncoding: Option[CharSequence] =
    getHeaderValue(Name.TransferEncoding)

  final def getUpgrade: Option[CharSequence] =
    getHeaderValue(Name.Upgrade)

  final def getUpgradeInsecureRequests: Option[CharSequence] =
    getHeaderValue(Name.UpgradeInsecureRequests)

  final def getUserAgent: Option[CharSequence] =
    getHeaderValue(Name.UserAgent)

  final def getVary: Option[CharSequence] =
    getHeaderValue(Name.Vary)

  final def getVia: Option[CharSequence] =
    getHeaderValue(Name.Via)

  final def getWarning: Option[CharSequence] =
    getHeaderValue(Name.Warning)

  final def getWebsocketLocation: Option[CharSequence] =
    getHeaderValue(Name.WebSocketLocation)

  final def getWebsocketOrigin: Option[CharSequence] =
    getHeaderValue(Name.WebSocketOrigin)

  final def getWebsocketProtocol: Option[CharSequence] =
    getHeaderValue(Name.WebSocketProtocol)

  final def getWwwAuthenticate: Option[CharSequence] =
    getHeaderValue(Name.WwwAuthenticate)

  final def getXFrameOptions: Option[CharSequence] =
    getHeaderValue(Name.XFrameOptions)

  final def getXRequestedWith: Option[CharSequence] =
    getHeaderValue(Name.XRequestedWith)

}
