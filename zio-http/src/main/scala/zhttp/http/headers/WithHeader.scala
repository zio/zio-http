package zhttp.http.headers

import zhttp.http.{Cookie, Headers, Method}

trait WithHeader[+A] { self: HeaderExtension[A] =>
  final def withAcceptCharset(value: CharSequence): A =
    addHeaders(Headers.makeAcceptCharset(value))

  final def withAcceptEncoding(value: CharSequence): A =
    addHeaders(Headers.makeAcceptEncoding(value))

  final def withAccept(value: CharSequence): A =
    addHeaders(Headers.makeAccept(value))

  final def withAcceptLanguage(value: CharSequence): A =
    addHeaders(Headers.makeAcceptLanguage(value))

  final def withAcceptPatch(value: CharSequence): A =
    addHeaders(Headers.makeAcceptPatch(value))

  final def withAcceptRanges(value: CharSequence): A =
    addHeaders(Headers.makeAcceptRanges(value))

  final def withAccessControlAllowCredentials(value: Boolean): A =
    addHeaders(Headers.makeAccessControlAllowCredentials(value))

  final def withAccessControlAllowHeaders(value: CharSequence): A =
    addHeaders(Headers.makeAccessControlAllowHeaders(value))

  final def withAccessControlAllowMethods(value: Method*): A =
    addHeaders(Headers.makeAccessControlAllowMethods(value: _*))

  final def withAccessControlAllowOrigin(value: CharSequence): A =
    addHeaders(Headers.makeAccessControlAllowOrigin(value))

  final def withAccessControlExposeHeaders(value: CharSequence): A =
    addHeaders(Headers.makeAccessControlExposeHeaders(value))

  final def withAccessControlMaxAge(value: CharSequence): A =
    addHeaders(Headers.makeAccessControlMaxAge(value))

  final def withAccessControlRequestHeaders(value: CharSequence): A =
    addHeaders(Headers.makeAccessControlRequestHeaders(value))

  final def withAccessControlRequestMethod(value: Method): A =
    addHeaders(Headers.makeAccessControlRequestMethod(value))

  final def withAge(value: CharSequence): A =
    addHeaders(Headers.makeAge(value))

  final def withAllow(value: CharSequence): A =
    addHeaders(Headers.makeAllow(value))

  final def withAuthorization(value: CharSequence): A =
    addHeaders(Headers.makeAuthorization(value))

  final def withBasicAuthorization(username: String, password: String): A =
    addHeaders(Headers.makeBasicAuthorizationHeader(username, password))

  final def withCacheControl(value: CharSequence): A =
    addHeaders(Headers.makeCacheControl(value))

  final def withConnection(value: CharSequence): A =
    addHeaders(Headers.makeConnection(value))

  final def withContentBase(value: CharSequence): A =
    addHeaders(Headers.makeContentBase(value))

  final def withContentDisposition(value: CharSequence): A =
    addHeaders(Headers.makeContentDisposition(value))

  final def withContentEncoding(value: CharSequence): A =
    addHeaders(Headers.makeContentEncoding(value))

  final def withContentLanguage(value: CharSequence): A =
    addHeaders(Headers.makeContentLanguage(value))

  final def withContentLength(value: Long): A =
    addHeaders(Headers.makeContentLength(value))

  final def withContentLocation(value: CharSequence): A =
    addHeaders(Headers.makeContentLocation(value))

  final def withContentMd5(value: CharSequence): A =
    addHeaders(Headers.makeContentMd5(value))

  final def withContentRange(value: CharSequence): A =
    addHeaders(Headers.makeContentRange(value))

  final def withContentSecurityPolicy(value: CharSequence): A =
    addHeaders(Headers.makeContentSecurityPolicy(value))

  final def withContentTransferEncoding(value: CharSequence): A =
    addHeaders(Headers.makeContentTransferEncoding(value))

  final def withContentType(value: CharSequence): A =
    addHeaders(Headers.makeContentType(value))

  final def withCookie(value: CharSequence): A =
    addHeaders(Headers.makeCookie(value))

  final def withDate(value: CharSequence): A =
    addHeaders(Headers.makeDate(value))

  final def withDnt(value: CharSequence): A =
    addHeaders(Headers.makeDnt(value))

  final def withEtag(value: CharSequence): A =
    addHeaders(Headers.makeEtag(value))

  final def withExpect(value: CharSequence): A =
    addHeaders(Headers.makeExpect(value))

  final def withExpires(value: CharSequence): A =
    addHeaders(Headers.makeExpires(value))

  final def withFrom(value: CharSequence): A =
    addHeaders(Headers.makeFrom(value))

  final def withHost(value: CharSequence): A =
    addHeaders(Headers.makeHost(value))

  final def withIfMatch(value: CharSequence): A =
    addHeaders(Headers.makeIfMatch(value))

  final def withIfModifiedSince(value: CharSequence): A =
    addHeaders(Headers.makeIfModifiedSince(value))

  final def withIfNoneMatch(value: CharSequence): A =
    addHeaders(Headers.makeIfNoneMatch(value))

  final def withIfRange(value: CharSequence): A =
    addHeaders(Headers.makeIfRange(value))

  final def withIfUnmodifiedSince(value: CharSequence): A =
    addHeaders(Headers.makeIfUnmodifiedSince(value))

  final def withLastModified(value: CharSequence): A =
    addHeaders(Headers.makeLastModified(value))

  final def withLocation(value: CharSequence): A =
    addHeaders(Headers.makeLocation(value))

  final def withMaxForwards(value: CharSequence): A =
    addHeaders(Headers.makeMaxForwards(value))

  final def withOrigin(value: CharSequence): A =
    addHeaders(Headers.makeOrigin(value))

  final def withPragma(value: CharSequence): A =
    addHeaders(Headers.makePragma(value))

  final def withProxyAuthenticate(value: CharSequence): A =
    addHeaders(Headers.makeProxyAuthenticate(value))

  final def withProxyAuthorization(value: CharSequence): A =
    addHeaders(Headers.makeProxyAuthorization(value))

  final def withRange(value: CharSequence): A =
    addHeaders(Headers.makeRange(value))

  final def withReferer(value: CharSequence): A =
    addHeaders(Headers.makeReferer(value))

  final def withRetryAfter(value: CharSequence): A =
    addHeaders(Headers.makeRetryAfter(value))

  final def withSecWebSocketAccept(value: CharSequence): A =
    addHeaders(Headers.makeSecWebSocketAccept(value))

  final def withSecWebSocketExtensions(value: CharSequence): A =
    addHeaders(Headers.makeSecWebSocketExtensions(value))

  final def withSecWebSocketKey(value: CharSequence): A =
    addHeaders(Headers.makeSecWebSocketKey(value))

  final def withSecWebSocketLocation(value: CharSequence): A =
    addHeaders(Headers.makeSecWebSocketLocation(value))

  final def withSecWebSocketOrigin(value: CharSequence): A =
    addHeaders(Headers.makeSecWebSocketOrigin(value))

  final def withSecWebSocketProtocol(value: CharSequence): A =
    addHeaders(Headers.makeSecWebSocketProtocol(value))

  final def withSecWebSocketVersion(value: CharSequence): A =
    addHeaders(Headers.makeSecWebSocketVersion(value))

  final def withServer(value: CharSequence): A =
    addHeaders(Headers.makeServer(value))

  final def withSetCookie(value: Cookie): A =
    addHeaders(Headers.makeSetCookie(value))

  final def withTe(value: CharSequence): A =
    addHeaders(Headers.makeTe(value))

  final def withTrailer(value: CharSequence): A =
    addHeaders(Headers.makeTrailer(value))

  final def withTransferEncoding(value: CharSequence): A =
    addHeaders(Headers.makeTransferEncoding(value))

  final def withUpgrade(value: CharSequence): A =
    addHeaders(Headers.makeUpgrade(value))

  final def withUpgradeInsecureRequests(value: CharSequence): A =
    addHeaders(Headers.makeUpgradeInsecureRequests(value))

  final def withUserAgent(value: CharSequence): A =
    addHeaders(Headers.makeUserAgent(value))

  final def withVary(value: CharSequence): A =
    addHeaders(Headers.makeVary(value))

  final def withVia(value: CharSequence): A =
    addHeaders(Headers.makeVia(value))

  final def withWarning(value: CharSequence): A =
    addHeaders(Headers.makeWarning(value))

  final def withWebSocketLocation(value: CharSequence): A =
    addHeaders(Headers.makeWebSocketLocation(value))

  final def withWebSocketOrigin(value: CharSequence): A =
    addHeaders(Headers.makeWebSocketOrigin(value))

  final def withWebSocketProtocol(value: CharSequence): A =
    addHeaders(Headers.makeWebSocketProtocol(value))

  final def withWwwAuthenticate(value: CharSequence): A =
    addHeaders(Headers.makeWwwAuthenticate(value))

  final def withXFrameOptions(value: CharSequence): A =
    addHeaders(Headers.makeXFrameOptions(value))

  final def withXRequestedWith(value: CharSequence): A =
    addHeaders(Headers.makeXRequestedWith(value))
}
