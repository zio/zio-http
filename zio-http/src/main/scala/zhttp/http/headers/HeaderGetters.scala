package zhttp.http.headers

trait HeaderGetters[+A] { self: HeaderExtension[A] =>
  import zhttp.http.Headers.Types.H._

  final def getAccept: Option[CharSequence] = getHeaderValue(`accept`)

  final def getAcceptCharset: Option[CharSequence] = getHeaderValue(`accept-charset`)

  final def getAcceptEncoding: Option[CharSequence] = getHeaderValue(`accept-encoding`)

  final def getAcceptLanguage: Option[CharSequence] = getHeaderValue(`accept-language`)

  final def getAcceptPatch: Option[CharSequence] = getHeaderValue(`accept-patch`)

  final def getAcceptRanges: Option[CharSequence] = getHeaderValue(`accept-ranges`)

  final def getAccessControlAllowCredentials: Option[Boolean] = getHeaderValue(
    `access-control-allow-credentials`,
  ) match {
    case Some(string) =>
      try Some(string.toBoolean)
      catch { case _: Throwable => None }
    case None         => None
  }

  final def getAccessControlAllowHeaders: Option[CharSequence] = getHeaderValue(`access-control-allow-headers`)

  final def getAccessControlAllowMethods: Option[CharSequence] = getHeaderValue(`access-control-allow-methods`)

  final def getAccessControlAllowOrigin: Option[CharSequence] = getHeaderValue(`access-control-allow-origin`)

  final def getAccessControlExposeHeaders: Option[CharSequence] = getHeaderValue(`access-control-expose-headers`)

  final def getAccessControlMaxAge: Option[CharSequence] = getHeaderValue(`access-control-max-age`)

  final def getAccessControlRequestHeaders: Option[CharSequence] = getHeaderValue(`access-control-request-headers`)

  final def getAccessControlRequestMethod: Option[CharSequence] = getHeaderValue(`access-control-request-method`)

  final def getAge: Option[CharSequence] = getHeaderValue(`age`)

  final def getAllow: Option[CharSequence] = getHeaderValue(`allow`)

  final def getAuthorization: Option[CharSequence] = getHeaderValue(`authorization`)

  final def getCacheControl: Option[CharSequence] = getHeaderValue(`cache-control`)

  final def getConnection: Option[CharSequence] = getHeaderValue(`connection`)

  final def getContentBase: Option[CharSequence] = getHeaderValue(`content-base`)

  final def getContentDisposition: Option[CharSequence] = getHeaderValue(`content-disposition`)

  final def getContentEncoding: Option[CharSequence] = getHeaderValue(`content-encoding`)

  final def getContentLanguage: Option[CharSequence] = getHeaderValue(`content-language`)

  final def getContentLength: Option[Long] = getHeaderValue(`content-length`) match {
    case Some(str) =>
      try Some(str.toString.toLong)
      catch {
        case _: Throwable => None
      }
    case None      => None
  }

  final def getContentLocation: Option[CharSequence] = getHeaderValue(`content-location`)

  final def getContentMd5: Option[CharSequence] = getHeaderValue(`content-md5`)

  final def getContentRange: Option[CharSequence] = getHeaderValue(`content-range`)

  final def getContentSecurityPolicy: Option[CharSequence] = getHeaderValue(`content-security-policy`)

  final def getContentTransferEncoding: Option[CharSequence] = getHeaderValue(`content-transfer-encoding`)

  final def getContentType: Option[CharSequence] = getHeaderValue(`content-type`)

  final def getCookie: Option[CharSequence] = getHeaderValue(`cookie`)

  final def getDate: Option[CharSequence] = getHeaderValue(`date`)

  final def getDnt: Option[CharSequence] = getHeaderValue(`dnt`)

  final def getEtag: Option[CharSequence] = getHeaderValue(`etag`)

  final def getExpect: Option[CharSequence] = getHeaderValue(`expect`)

  final def getExpires: Option[CharSequence] = getHeaderValue(`expires`)

  final def getFrom: Option[CharSequence] = getHeaderValue(`from`)

  final def getHost: Option[CharSequence] = getHeaderValue(`host`)

  final def getIfMatch: Option[CharSequence] = getHeaderValue(`if-match`)

  final def getIfModifiedSince: Option[CharSequence] = getHeaderValue(`if-modified-since`)

  final def getIfNoneMatch: Option[CharSequence] = getHeaderValue(`if-none-match`)

  final def getIfRange: Option[CharSequence] = getHeaderValue(`if-range`)

  final def getIfUnmodifiedSince: Option[CharSequence] = getHeaderValue(`if-unmodified-since`)

  final def getLastModified: Option[CharSequence] = getHeaderValue(`last-modified`)

  final def getLocation: Option[CharSequence] = getHeaderValue(`location`)

  final def getMaxForwards: Option[CharSequence] = getHeaderValue(`max-forwards`)

  final def getOrigin: Option[CharSequence] = getHeaderValue(`origin`)

  final def getPragma: Option[CharSequence] = getHeaderValue(`pragma`)

  final def getProxyAuthenticate: Option[CharSequence] = getHeaderValue(`proxy-authenticate`)

  final def getProxyAuthorization: Option[CharSequence] = getHeaderValue(`proxy-authorization`)

  final def getRange: Option[CharSequence] = getHeaderValue(`range`)

  final def getReferer: Option[CharSequence] = getHeaderValue(`referer`)

  final def getRetryAfter: Option[CharSequence] = getHeaderValue(`retry-after`)

  final def getSecWebsocketAccept: Option[CharSequence] = getHeaderValue(`sec-websocket-accept`)

  final def getSecWebsocketExtensions: Option[CharSequence] = getHeaderValue(`sec-websocket-extensions`)

  final def getSecWebsocketKey: Option[CharSequence] = getHeaderValue(`sec-websocket-key`)

  final def getSecWebsocketKey1: Option[CharSequence] = getHeaderValue(`sec-websocket-key1`)

  final def getSecWebsocketKey2: Option[CharSequence] = getHeaderValue(`sec-websocket-key2`)

  final def getSecWebsocketLocation: Option[CharSequence] = getHeaderValue(`sec-websocket-location`)

  final def getSecWebsocketOrigin: Option[CharSequence] = getHeaderValue(`sec-websocket-origin`)

  final def getSecWebsocketProtocol: Option[CharSequence] = getHeaderValue(`sec-websocket-protocol`)

  final def getSecWebsocketVersion: Option[CharSequence] = getHeaderValue(`sec-websocket-version`)

  final def getServer: Option[CharSequence] = getHeaderValue(`server`)

  final def getSetCookie: Option[CharSequence] = getHeaderValue(`set-cookie`)

  final def getSetCookie2: Option[CharSequence] = getHeaderValue(`set-cookie2`)

  final def getTe: Option[CharSequence] = getHeaderValue(`te`)

  final def getTrailer: Option[CharSequence] = getHeaderValue(`trailer`)

  final def getTransferEncoding: Option[CharSequence] = getHeaderValue(`transfer-encoding`)

  final def getUpgrade: Option[CharSequence] = getHeaderValue(`upgrade`)

  final def getUpgradeInsecureRequests: Option[CharSequence] = getHeaderValue(`upgrade-insecure-requests`)

  final def getUserAgent: Option[CharSequence] = getHeaderValue(`user-agent`)

  final def getVary: Option[CharSequence] = getHeaderValue(`vary`)

  final def getVia: Option[CharSequence] = getHeaderValue(`via`)

  final def getWarning: Option[CharSequence] = getHeaderValue(`warning`)

  final def getWebsocketLocation: Option[CharSequence] = getHeaderValue(`websocket-location`)

  final def getWebsocketOrigin: Option[CharSequence] = getHeaderValue(`websocket-origin`)

  final def getWebsocketProtocol: Option[CharSequence] = getHeaderValue(`websocket-protocol`)

  final def getWwwAuthenticate: Option[CharSequence] = getHeaderValue(`www-authenticate`)

  final def getXFrameOptions: Option[CharSequence] = getHeaderValue(`x-frame-options`)

  final def getXRequestedWith: Option[CharSequence] = getHeaderValue(`x-requested-with`)

}
