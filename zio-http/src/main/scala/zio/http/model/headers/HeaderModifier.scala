package zio.http.model.headers

import zio.{Duration, Trace}
import zio.http._
import zio.http.model._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Maintains a list of operators that modify the current Headers. Once modified,
 * a new instance of the same type is returned. So or eg:
 * `request.addHeader("A", "B")` should return a new `Request` and similarly
 * `headers.add("A", "B")` should return a new `Headers` instance.
 *
 * NOTE: Add methods here that modify the current headers and returns an
 * instance of the same type.
 */
trait HeaderModifier[+A] { self =>

  final def addHeader(header: Header): A = addHeaders(header)

  final def addHeader(name: CharSequence, value: CharSequence): A = addHeaders(Headers(name, value))

  final def addHeaders(headers: Headers): A = updateHeaders(_ ++ headers)

  final def removeHeader(name: String): A = removeHeaders(List(name))

  final def removeHeaders(headers: List[String]): A =
    updateHeaders(orig => Headers(orig.toList.filterNot(h => headers.contains(h._1))))

  final def setHeaders(headers: Headers): A = self.updateHeaders(_ => headers)

  /**
   * Updates the current Headers with new one, using the provided update
   * function passed.
   */
  def updateHeaders(update: Headers => Headers): A

  final def withAccept(value: CharSequence): A =
    addHeaders(Headers.accept(value))

  final def withAcceptEncoding(value: CharSequence): A =
    addHeaders(Headers.acceptEncoding(value))

  final def withAcceptLanguage(value: CharSequence): A =
    addHeaders(Headers.acceptLanguage(value))

  final def withAcceptPatch(value: CharSequence): A =
    addHeaders(Headers.acceptPatch(value))

  final def withAcceptRanges(value: CharSequence): A =
    addHeaders(Headers.acceptRanges(value))

  final def withAccessControlAllowCredentials(value: Boolean): A =
    addHeaders(Headers.accessControlAllowCredentials(value))

  final def withAccessControlAllowHeaders(value: CharSequence): A =
    addHeaders(Headers.accessControlAllowHeaders(value))

  final def withAccessControlAllowMethods(value: Method*): A =
    addHeaders(Headers.accessControlAllowMethods(value: _*))

  final def withAccessControlAllowOrigin(value: CharSequence): A =
    addHeaders(Headers.accessControlAllowOrigin(value))

  final def withAccessControlExposeHeaders(value: CharSequence): A =
    addHeaders(Headers.accessControlExposeHeaders(value))

  final def withAccessControlMaxAge(value: CharSequence): A =
    addHeaders(Headers.accessControlMaxAge(value))

  final def withAccessControlRequestHeaders(value: CharSequence): A =
    addHeaders(Headers.accessControlRequestHeaders(value))

  final def withAccessControlRequestMethod(value: Method): A =
    addHeaders(Headers.accessControlRequestMethod(value))

  final def withAge(value: CharSequence): A =
    addHeaders(Headers.age(value))

  final def withAllow(value: CharSequence): A =
    addHeaders(Headers.allow(value))

  final def withAuthorization(value: CharSequence): A =
    addHeaders(Headers.authorization(value))

  final def withBasicAuthorization(username: String, password: String): A =
    addHeaders(Headers.basicAuthorizationHeader(username, password))

  final def withCacheControl(value: CharSequence): A =
    addHeaders(Headers.cacheControl(value))

  final def withCacheControlMaxAge(value: Duration): A =
    addHeaders(Headers.cacheControlMaxAge(value))

  final def withConnection(value: CharSequence): A =
    addHeaders(Headers.connection(value))

  final def withContentBase(value: CharSequence): A =
    addHeaders(Headers.contentBase(value))

  final def withContentDisposition(value: CharSequence): A =
    addHeaders(Headers.contentDisposition(value))

  final def withContentEncoding(value: CharSequence): A =
    addHeaders(Headers.contentEncoding(value))

  final def withContentLanguage(value: CharSequence): A =
    addHeaders(Headers.contentLanguage(value))

  final def withContentLength(value: Long): A =
    addHeaders(Headers.contentLength(value))

  final def withContentLocation(value: CharSequence): A =
    addHeaders(Headers.contentLocation(value))

  final def withContentMd5(value: CharSequence): A =
    addHeaders(Headers.contentMd5(value))

  final def withContentRange(value: CharSequence): A =
    addHeaders(Headers.contentRange(value))

  final def withContentSecurityPolicy(value: CharSequence): A =
    addHeaders(Headers.contentSecurityPolicy(value))

  final def withContentTransferEncoding(value: CharSequence): A =
    addHeaders(Headers.contentTransferEncoding(value))

  final def withContentType(value: CharSequence): A =
    setHeaders(Headers.contentType(value))

  final def withCookie(value: CharSequence): A =
    addHeaders(Headers.cookie(value))

  final def withDate(value: CharSequence): A =
    addHeaders(Headers.date(value))

  final def withDnt(value: CharSequence): A =
    addHeaders(Headers.dnt(value))

  final def withEtag(value: CharSequence): A =
    addHeaders(Headers.etag(value))

  final def withExpect(value: CharSequence): A =
    addHeaders(Headers.expect(value))

  final def withExpires(value: CharSequence): A =
    addHeaders(Headers.expires(value))

  final def withFrom(value: CharSequence): A =
    addHeaders(Headers.from(value))

  final def withHost(value: CharSequence): A =
    addHeaders(Headers.host(value))

  final def withIfMatch(value: CharSequence): A =
    addHeaders(Headers.ifMatch(value))

  final def withIfModifiedSince(value: CharSequence): A =
    addHeaders(Headers.ifModifiedSince(value))

  final def withIfNoneMatch(value: CharSequence): A =
    addHeaders(Headers.ifNoneMatch(value))

  final def withIfRange(value: CharSequence): A =
    addHeaders(Headers.ifRange(value))

  final def withIfUnmodifiedSince(value: CharSequence): A =
    addHeaders(Headers.ifUnmodifiedSince(value))

  final def withLastModified(value: CharSequence): A =
    addHeaders(Headers.lastModified(value))

  final def withLocation(value: CharSequence): A =
    addHeaders(Headers.location(value))

  final def withMaxForwards(value: CharSequence): A =
    addHeaders(Headers.maxForwards(value))

  def withMediaType(mediaType: MediaType): A = self.withContentType(mediaType.fullType)

  final def withOrigin(value: CharSequence): A =
    addHeaders(Headers.origin(value))

  final def withPragma(value: CharSequence): A =
    addHeaders(Headers.pragma(value))

  final def withProxyAuthenticate(value: CharSequence): A =
    addHeaders(Headers.proxyAuthenticate(value))

  final def withProxyAuthorization(value: CharSequence): A =
    addHeaders(Headers.proxyAuthorization(value))

  final def withRange(value: CharSequence): A =
    addHeaders(Headers.range(value))

  final def withReferer(value: CharSequence): A =
    addHeaders(Headers.referer(value))

  final def withRetryAfter(value: CharSequence): A =
    addHeaders(Headers.retryAfter(value))

  final def withSecWebSocketAccept(value: CharSequence): A =
    addHeaders(Headers.secWebSocketAccept(value))

  final def withSecWebSocketExtensions(value: CharSequence): A =
    addHeaders(Headers.secWebSocketExtensions(value))

  final def withSecWebSocketKey(value: CharSequence): A =
    addHeaders(Headers.secWebSocketKey(value))

  final def withSecWebSocketLocation(value: CharSequence): A =
    addHeaders(Headers.secWebSocketLocation(value))

  final def withSecWebSocketOrigin(value: CharSequence): A =
    addHeaders(Headers.secWebSocketOrigin(value))

  final def withSecWebSocketProtocol(value: CharSequence): A =
    addHeaders(Headers.secWebSocketProtocol(value))

  final def withSecWebSocketVersion(value: CharSequence): A =
    addHeaders(Headers.secWebSocketVersion(value))

  final def withServer(value: CharSequence): A =
    addHeaders(Headers.server(value))

  final def withSetCookie(value: Cookie[Response]): A =
    addHeaders(Headers.setCookie(value))

  final def withTe(value: CharSequence): A =
    addHeaders(Headers.te(value))

  final def withTrailer(value: CharSequence): A =
    addHeaders(Headers.trailer(value))

  final def withTransferEncoding(value: CharSequence): A =
    addHeaders(Headers.transferEncoding(value))

  final def withUpgrade(value: CharSequence): A =
    addHeaders(Headers.upgrade(value))

  final def withUpgradeInsecureRequests(value: CharSequence): A =
    addHeaders(Headers.upgradeInsecureRequests(value))

  final def withUserAgent(value: CharSequence): A =
    addHeaders(Headers.userAgent(value))

  final def withVary(value: CharSequence): A =
    addHeaders(Headers.vary(value))

  final def withVia(value: CharSequence): A =
    addHeaders(Headers.via(value))

  final def withWarning(value: CharSequence): A =
    addHeaders(Headers.warning(value))

  final def withWebSocketLocation(value: CharSequence): A =
    addHeaders(Headers.webSocketLocation(value))

  final def withWebSocketOrigin(value: CharSequence): A =
    addHeaders(Headers.webSocketOrigin(value))

  final def withWebSocketProtocol(value: CharSequence): A =
    addHeaders(Headers.webSocketProtocol(value))

  final def withWwwAuthenticate(value: CharSequence): A =
    addHeaders(Headers.wwwAuthenticate(value))

  final def withXFrameOptions(value: CharSequence): A =
    addHeaders(Headers.xFrameOptions(value))

  final def withXRequestedWith(value: CharSequence): A =
    addHeaders(Headers.xRequestedWith(value))
}
