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
trait HeaderModifierZIO[+A] { self =>

  final def addHeader(header: Header)(implicit trace: Trace): A =
    addHeaders(header)

  final def addHeader(name: CharSequence, value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers(name, value))

  final def addHeaders(headers: Headers)(implicit trace: Trace): A =
    updateHeaders(_ ++ headers)

  final def removeHeader(name: String)(implicit trace: Trace): A =
    removeHeaders(List(name))

  final def removeHeaders(headers: List[String])(implicit trace: Trace): A =
    updateHeaders(orig => Headers(orig.toList.filterNot(h => headers.contains(h._1))))

  final def setHeaders(headers: Headers)(implicit trace: Trace): A =
    self.updateHeaders(_ => headers)

  /**
   * Updates the current Headers with new one, using the provided update
   * function passed.
   */
  def updateHeaders(update: Headers => Headers)(implicit trace: Trace): A

  final def withAccept(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.accept(value))

  final def withAcceptEncoding(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.acceptEncoding(value))

  final def withAcceptLanguage(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.acceptLanguage(value))

  final def withAcceptPatch(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.acceptPatch(value))

  final def withAcceptRanges(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.acceptRanges(value))

  final def withAccessControlAllowCredentials(value: Boolean)(implicit trace: Trace): A =
    addHeaders(Headers.accessControlAllowCredentials(value))

  final def withAccessControlAllowHeaders(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.accessControlAllowHeaders(value))

  final def withAccessControlAllowMethods(value: Method*)(implicit trace: Trace): A =
    addHeaders(Headers.accessControlAllowMethods(value: _*))

  final def withAccessControlAllowOrigin(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.accessControlAllowOrigin(value))

  final def withAccessControlExposeHeaders(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.accessControlExposeHeaders(value))

  final def withAccessControlMaxAge(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.accessControlMaxAge(value))

  final def withAccessControlRequestHeaders(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.accessControlRequestHeaders(value))

  final def withAccessControlRequestMethod(value: Method)(implicit trace: Trace): A =
    addHeaders(Headers.accessControlRequestMethod(value))

  final def withAge(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.age(value))

  final def withAllow(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.allow(value))

  final def withAuthorization(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.authorization(value))

  final def withBasicAuthorization(username: String, password: String)(implicit trace: Trace): A =
    addHeaders(Headers.basicAuthorizationHeader(username, password))

  final def withCacheControl(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.cacheControl(value))

  final def withCacheControlMaxAge(value: Duration)(implicit trace: Trace): A =
    addHeaders(Headers.cacheControlMaxAge(value))

  final def withConnection(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.connection(value))

  final def withContentBase(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.contentBase(value))

  final def withContentDisposition(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.contentDisposition(value))

  final def withContentEncoding(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.contentEncoding(value))

  final def withContentLanguage(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.contentLanguage(value))

  final def withContentLength(value: Long)(implicit trace: Trace): A =
    addHeaders(Headers.contentLength(value))

  final def withContentLocation(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.contentLocation(value))

  final def withContentMd5(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.contentMd5(value))

  final def withContentRange(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.contentRange(value))

  final def withContentSecurityPolicy(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.contentSecurityPolicy(value))

  final def withContentTransferEncoding(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.contentTransferEncoding(value))

  final def withContentType(value: CharSequence)(implicit trace: Trace): A =
    setHeaders(Headers.contentType(value))

  final def withCookie(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.cookie(value))

  final def withDate(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.date(value))

  final def withDnt(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.dnt(value))

  final def withEtag(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.etag(value))

  final def withExpect(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.expect(value))

  final def withExpires(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.expires(value))

  final def withFrom(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.from(value))

  final def withHost(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.host(value))

  final def withIfMatch(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.ifMatch(value))

  final def withIfModifiedSince(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.ifModifiedSince(value))

  final def withIfNoneMatch(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.ifNoneMatch(value))

  final def withIfRange(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.ifRange(value))

  final def withIfUnmodifiedSince(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.ifUnmodifiedSince(value))

  final def withLastModified(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.lastModified(value))

  final def withLocation(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.location(value))

  final def withMaxForwards(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.maxForwards(value))

  def withMediaType(mediaType: MediaType)(implicit trace: Trace): A =
    self.withContentType(mediaType.fullType)

  final def withOrigin(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.origin(value))

  final def withPragma(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.pragma(value))

  final def withProxyAuthenticate(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.proxyAuthenticate(value))

  final def withProxyAuthorization(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.proxyAuthorization(value))

  final def withRange(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.range(value))

  final def withReferer(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.referer(value))

  final def withRetryAfter(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.retryAfter(value))

  final def withSecWebSocketAccept(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.secWebSocketAccept(value))

  final def withSecWebSocketExtensions(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.secWebSocketExtensions(value))

  final def withSecWebSocketKey(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.secWebSocketKey(value))

  final def withSecWebSocketLocation(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.secWebSocketLocation(value))

  final def withSecWebSocketOrigin(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.secWebSocketOrigin(value))

  final def withSecWebSocketProtocol(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.secWebSocketProtocol(value))

  final def withSecWebSocketVersion(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.secWebSocketVersion(value))

  final def withServer(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.server(value))

  final def withSetCookie(value: Cookie[Response])(implicit trace: Trace): A =
    addHeaders(Headers.setCookie(value))

  final def withTe(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.te(value))

  final def withTrailer(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.trailer(value))

  final def withTransferEncoding(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.transferEncoding(value))

  final def withUpgrade(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.upgrade(value))

  final def withUpgradeInsecureRequests(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.upgradeInsecureRequests(value))

  final def withUserAgent(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.userAgent(value))

  final def withVary(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.vary(value))

  final def withVia(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.via(value))

  final def withWarning(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.warning(value))

  final def withWebSocketLocation(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.webSocketLocation(value))

  final def withWebSocketOrigin(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.webSocketOrigin(value))

  final def withWebSocketProtocol(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.webSocketProtocol(value))

  final def withWwwAuthenticate(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.wwwAuthenticate(value))

  final def withXFrameOptions(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.xFrameOptions(value))

  final def withXRequestedWith(value: CharSequence)(implicit trace: Trace): A =
    addHeaders(Headers.xRequestedWith(value))
}
