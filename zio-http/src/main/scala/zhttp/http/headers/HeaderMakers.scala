package zhttp.http.headers

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http.Headers.BasicSchemeName
import zhttp.http.Headers.Types.H
import zhttp.http.{Cookie, HTTP_CHARSET, Headers, Method}

import java.util.Base64

trait HeaderMakers {
  final def makeAccept(value: CharSequence): Headers = Headers(H.`accept`, value)

  final def makeAcceptCharset(value: CharSequence): Headers = Headers(H.`accept-charset`, value)

  final def makeAcceptEncoding(value: CharSequence): Headers = Headers(H.`accept-encoding`, value)

  final def makeAcceptLanguage(value: CharSequence): Headers = Headers(H.`accept-language`, value)

  final def makeAcceptPatch(value: CharSequence): Headers = Headers(H.`accept-patch`, value)

  final def makeAcceptRanges(value: CharSequence): Headers = Headers(H.`accept-ranges`, value)

  final def makeAccessControlAllowCredentials(value: Boolean): Headers =
    Headers(H.`access-control-allow-credentials`, value.toString)

  final def makeAccessControlAllowHeaders(value: CharSequence): Headers =
    Headers(H.`access-control-allow-headers`, value)

  final def makeAccessControlAllowMethods(methods: Method*): Headers =
    Headers(H.`access-control-allow-methods`, methods.map(_.toString()).mkString(", "))

  final def makeAccessControlAllowOrigin(value: CharSequence): Headers = Headers(H.`access-control-allow-origin`, value)

  final def makeAccessControlExposeHeaders(value: CharSequence): Headers =
    Headers(H.`access-control-expose-headers`, value)

  final def makeAccessControlMaxAge(value: CharSequence): Headers = Headers(H.`access-control-max-age`, value)

  final def makeAccessControlRequestHeaders(value: CharSequence): Headers =
    Headers(H.`access-control-request-headers`, value)

  final def makeAccessControlRequestMethod(method: Method): Headers =
    Headers(H.`access-control-request-method`, method.asHttpMethod.name())

  final def makeAge(value: CharSequence): Headers = Headers(H.`age`, value)

  final def makeAllow(value: CharSequence): Headers = Headers(H.`allow`, value)

  final def makeAuthorization(value: CharSequence): Headers = Headers(H.`authorization`, value)

  final def makeBasicAuthorizationHeader(username: String, password: String): Headers = {
    val authString    = String.format("%s:%s", username, password)
    val encodedAuthCB = new String(Base64.getEncoder.encode(authString.getBytes(HTTP_CHARSET)), HTTP_CHARSET)
    val value         = String.format("%s %s", BasicSchemeName, encodedAuthCB)
    Headers(HttpHeaderNames.AUTHORIZATION, value)
  }

  final def makeCacheControl(value: CharSequence): Headers = Headers(H.`cache-control`, value)

  final def makeConnection(value: CharSequence): Headers = Headers(H.`connection`, value)

  final def makeContentBase(value: CharSequence): Headers = Headers(H.`content-base`, value)

  final def makeContentDisposition(value: CharSequence): Headers = Headers(H.`content-disposition`, value)

  final def makeContentEncoding(value: CharSequence): Headers = Headers(H.`content-encoding`, value)

  final def makeContentLanguage(value: CharSequence): Headers = Headers(H.`content-language`, value)

  final def makeContentLength(value: Long): Headers = Headers(H.`content-length`, value.toString)

  final def makeContentLocation(value: CharSequence): Headers = Headers(H.`content-location`, value)

  final def makeContentMd5(value: CharSequence): Headers = Headers(H.`content-md5`, value)

  final def makeContentRange(value: CharSequence): Headers = Headers(H.`content-range`, value)

  final def makeContentSecurityPolicy(value: CharSequence): Headers = Headers(H.`content-security-policy`, value)

  final def makeContentTransferEncoding(value: CharSequence): Headers = Headers(H.`content-transfer-encoding`, value)

  final def makeContentType(value: CharSequence): Headers = Headers(H.`content-type`, value)

  final def makeCookie(value: CharSequence): Headers = Headers(H.`cookie`, value)

  final def makeDate(value: CharSequence): Headers = Headers(H.`date`, value)

  final def makeDnt(value: CharSequence): Headers = Headers(H.`dnt`, value)

  final def makeEtag(value: CharSequence): Headers = Headers(H.`etag`, value)

  final def makeExpect(value: CharSequence): Headers = Headers(H.`expect`, value)

  final def makeExpires(value: CharSequence): Headers = Headers(H.`expires`, value)

  final def makeFrom(value: CharSequence): Headers = Headers(H.`from`, value)

  final def makeHost(value: CharSequence): Headers = Headers(H.`host`, value)

  final def makeIfMatch(value: CharSequence): Headers = Headers(H.`if-match`, value)

  final def makeIfModifiedSince(value: CharSequence): Headers = Headers(H.`if-modified-since`, value)

  final def makeIfNoneMatch(value: CharSequence): Headers = Headers(H.`if-none-match`, value)

  final def makeIfRange(value: CharSequence): Headers = Headers(H.`if-range`, value)

  final def makeIfUnmodifiedSince(value: CharSequence): Headers = Headers(H.`if-unmodified-since`, value)

  final def makeLastModified(value: CharSequence): Headers = Headers(H.`last-modified`, value)

  final def makeLocation(value: CharSequence): Headers = Headers(H.`location`, value)

  final def makeMaxForwards(value: CharSequence): Headers = Headers(H.`max-forwards`, value)

  final def makeOrigin(value: CharSequence): Headers = Headers(H.`origin`, value)

  final def makePragma(value: CharSequence): Headers = Headers(H.`pragma`, value)

  final def makeProxyAuthenticate(value: CharSequence): Headers = Headers(H.`proxy-authenticate`, value)

  final def makeProxyAuthorization(value: CharSequence): Headers = Headers(H.`proxy-authorization`, value)

  final def makeRange(value: CharSequence): Headers = Headers(H.`range`, value)

  final def makeReferer(value: CharSequence): Headers = Headers(H.`referer`, value)

  final def makeRetryAfter(value: CharSequence): Headers = Headers(H.`retry-after`, value)

  final def makeSecWebSocketAccept(value: CharSequence): Headers = Headers(H.`sec-websocket-accept`, value)

  final def makeSecWebSocketExtensions(value: CharSequence): Headers = Headers(H.`sec-websocket-extensions`, value)

  final def makeSecWebSocketKey(value: CharSequence): Headers = Headers(H.`sec-websocket-key`, value)

  final def makeSecWebSocketLocation(value: CharSequence): Headers = Headers(H.`sec-websocket-location`, value)

  final def makeSecWebSocketOrigin(value: CharSequence): Headers = Headers(H.`sec-websocket-origin`, value)

  final def makeSecWebSocketProtocol(value: CharSequence): Headers = Headers(H.`sec-websocket-protocol`, value)

  final def makeSecWebSocketVersion(value: CharSequence): Headers = Headers(H.`sec-websocket-version`, value)

  final def makeServer(value: CharSequence): Headers = Headers(H.`server`, value)

  final def makeSetCookie(value: Cookie): Headers = Headers(H.`set-cookie`, value.encode)

  final def makeTe(value: CharSequence): Headers = Headers(H.`te`, value)

  final def makeTrailer(value: CharSequence): Headers = Headers(H.`trailer`, value)

  final def makeTransferEncoding(value: CharSequence): Headers = Headers(H.`transfer-encoding`, value)

  final def makeUpgrade(value: CharSequence): Headers = Headers(H.`upgrade`, value)

  final def makeUpgradeInsecureRequests(value: CharSequence): Headers = Headers(H.`upgrade-insecure-requests`, value)

  final def makeUserAgent(value: CharSequence): Headers = Headers(H.`user-agent`, value)

  final def makeVary(value: CharSequence): Headers = Headers(H.`vary`, value)

  final def makeVia(value: CharSequence): Headers = Headers(H.`via`, value)

  final def makeWarning(value: CharSequence): Headers = Headers(H.`warning`, value)

  final def makeWebSocketLocation(value: CharSequence): Headers = Headers(H.`websocket-location`, value)

  final def makeWebSocketOrigin(value: CharSequence): Headers = Headers(H.`websocket-origin`, value)

  final def makeWebSocketProtocol(value: CharSequence): Headers = Headers(H.`websocket-protocol`, value)

  final def makeWwwAuthenticate(value: CharSequence): Headers = Headers(H.`www-authenticate`, value)

  final def makeXFrameOptions(value: CharSequence): Headers = Headers(H.`x-frame-options`, value)

  final def makeXRequestedWith(value: CharSequence): Headers = Headers(H.`x-requested-with`, value)
}
