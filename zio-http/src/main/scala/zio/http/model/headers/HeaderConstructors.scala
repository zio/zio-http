package zio.http.model.headers

import io.netty.handler.codec.http.HttpHeaderNames
import zio.Duration
import zio.http._
import zio.http.model.Headers.{BasicSchemeName, BearerSchemeName}
import zio.http.model._

import java.util.Base64
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Contains a list of helpful methods that can create `Headers`.
 *
 * NOTE: Add methods here if it provides an alternative succinct way to create
 * `Headers`.
 */
trait HeaderConstructors {
  final def accept(value: CharSequence): Headers =
    Headers(HeaderNames.accept, value)

  final def acceptEncoding(value: CharSequence): Headers =
    Headers(HeaderNames.acceptEncoding, value)

  final def acceptLanguage(value: CharSequence): Headers =
    Headers(HeaderNames.acceptLanguage, value)

  final def acceptPatch(value: CharSequence): Headers =
    Headers(HeaderNames.acceptPatch, value)

  final def acceptRanges(value: CharSequence): Headers =
    Headers(HeaderNames.acceptRanges, value)

  final def accessControlAllowCredentials(value: Boolean): Headers =
    Headers(HeaderNames.accessControlAllowCredentials, value.toString)

  final def accessControlAllowHeaders(value: CharSequence): Headers =
    Headers(HeaderNames.accessControlAllowHeaders, value)

  final def accessControlAllowMethods(methods: Method*): Headers =
    Headers(HeaderNames.accessControlAllowMethods, methods.mkString(", "))

  final def accessControlAllowOrigin(value: CharSequence): Headers =
    Headers(HeaderNames.accessControlAllowOrigin, value)

  final def accessControlExposeHeaders(value: CharSequence): Headers =
    Headers(HeaderNames.accessControlExposeHeaders, value)

  final def accessControlMaxAge(value: CharSequence): Headers =
    Headers(HeaderNames.accessControlMaxAge, value)

  final def accessControlRequestHeaders(value: CharSequence): Headers =
    Headers(HeaderNames.accessControlRequestHeaders, value)

  final def accessControlRequestMethod(method: Method): Headers =
    Headers(HeaderNames.accessControlRequestMethod, method.toJava.name())

  final def age(value: CharSequence): Headers =
    Headers(HeaderNames.age, value)

  final def allow(value: CharSequence): Headers =
    Headers(HeaderNames.allow, value)

  final def authorization(value: CharSequence): Headers =
    Headers(HeaderNames.authorization, value)

  final def basicAuthorizationHeader(username: String, password: String): Headers = {
    val authString    = String.format("%s:%s", username, password)
    val encodedAuthCB = new String(Base64.getEncoder.encode(authString.getBytes(HTTP_CHARSET)), HTTP_CHARSET)
    val value         = String.format("%s %s", BasicSchemeName, encodedAuthCB)
    Headers(HttpHeaderNames.AUTHORIZATION, value)
  }

  final def bearerAuthorizationHeader(token: String): Headers = {
    val value = String.format("%s %s", BearerSchemeName, token)
    Headers(HttpHeaderNames.AUTHORIZATION, value)
  }

  final def cacheControl(value: CharSequence): Headers =
    Headers(HeaderNames.cacheControl, value)

  final def cacheControlMaxAge(value: Duration): Headers =
    Headers(HeaderNames.cacheControl, s"public, max-age=${value.getSeconds}")

  final def connection(value: CharSequence): Headers =
    Headers(HeaderNames.connection, value)

  final def contentBase(value: CharSequence): Headers =
    Headers(HeaderNames.contentBase, value)

  final def contentDisposition(value: CharSequence): Headers =
    Headers(HeaderNames.contentDisposition, value)

  final def contentEncoding(value: CharSequence): Headers =
    Headers(HeaderNames.contentEncoding, value)

  final def contentLanguage(value: CharSequence): Headers =
    Headers(HeaderNames.contentLanguage, value)

  final def contentLength(value: Long): Headers =
    Headers(HeaderNames.contentLength, value.toString)

  final def contentLocation(value: CharSequence): Headers =
    Headers(HeaderNames.contentLocation, value)

  final def contentMd5(value: CharSequence): Headers =
    Headers(HeaderNames.contentMd5, value)

  final def contentRange(value: CharSequence): Headers =
    Headers(HeaderNames.contentRange, value)

  final def contentSecurityPolicy(value: CharSequence): Headers =
    Headers(HeaderNames.contentSecurityPolicy, value)

  final def contentTransferEncoding(value: CharSequence): Headers =
    Headers(HeaderNames.contentTransferEncoding, value)

  final def contentType(value: CharSequence): Headers =
    Headers(HeaderNames.contentType, value)

  final def cookie(value: CharSequence): Headers =
    Headers(HeaderNames.cookie, value)

  final def cookie(value: Cookie[Request]): Headers =
    value.encode.map(Headers(HeaderNames.cookie, _)).getOrElse(Headers.empty)

  final def date(value: CharSequence): Headers =
    Headers(HeaderNames.date, value)

  final def dnt(value: CharSequence): Headers =
    Headers(HeaderNames.dnt, value)

  final def etag(value: CharSequence): Headers =
    Headers(HeaderNames.etag, value)

  final def expect(value: CharSequence): Headers =
    Headers(HeaderNames.expect, value)

  final def expires(value: CharSequence): Headers =
    Headers(HeaderNames.expires, value)

  final def from(value: CharSequence): Headers =
    Headers(HeaderNames.from, value)

  final def host(value: CharSequence): Headers =
    Headers(HeaderNames.host, value)

  final def ifMatch(value: CharSequence): Headers =
    Headers(HeaderNames.ifMatch, value)

  final def ifModifiedSince(value: CharSequence): Headers =
    Headers(HeaderNames.ifModifiedSince, value)

  final def ifNoneMatch(value: CharSequence): Headers =
    Headers(HeaderNames.ifNoneMatch, value)

  final def ifRange(value: CharSequence): Headers =
    Headers(HeaderNames.ifRange, value)

  final def ifUnmodifiedSince(value: CharSequence): Headers =
    Headers(HeaderNames.ifUnmodifiedSince, value)

  final def lastModified(value: CharSequence): Headers =
    Headers(HeaderNames.lastModified, value)

  final def location(value: CharSequence): Headers =
    Headers(HeaderNames.location, value)

  final def maxForwards(value: CharSequence): Headers =
    Headers(HeaderNames.maxForwards, value)

  final def origin(value: CharSequence): Headers =
    Headers(HeaderNames.origin, value)

  final def pragma(value: CharSequence): Headers =
    Headers(HeaderNames.pragma, value)

  final def proxyAuthenticate(value: CharSequence): Headers =
    Headers(HeaderNames.proxyAuthenticate, value)

  final def proxyAuthorization(value: CharSequence): Headers =
    Headers(HeaderNames.proxyAuthorization, value)

  final def range(value: CharSequence): Headers =
    Headers(HeaderNames.range, value)

  final def referer(value: CharSequence): Headers =
    Headers(HeaderNames.referer, value)

  final def retryAfter(value: CharSequence): Headers =
    Headers(HeaderNames.retryAfter, value)

  final def secWebSocketAccept(value: CharSequence): Headers =
    Headers(HeaderNames.secWebSocketAccept, value)

  final def secWebSocketExtensions(value: CharSequence): Headers =
    Headers(HeaderNames.secWebSocketExtensions, value)

  final def secWebSocketKey(value: CharSequence): Headers =
    Headers(HeaderNames.secWebSocketKey, value)

  final def secWebSocketLocation(value: CharSequence): Headers =
    Headers(HeaderNames.secWebSocketLocation, value)

  final def secWebSocketOrigin(value: CharSequence): Headers =
    Headers(HeaderNames.secWebSocketOrigin, value)

  final def secWebSocketProtocol(value: CharSequence): Headers =
    Headers(HeaderNames.secWebSocketProtocol, value)

  final def secWebSocketVersion(value: CharSequence): Headers =
    Headers(HeaderNames.secWebSocketVersion, value)

  final def server(value: CharSequence): Headers =
    Headers(HeaderNames.server, value)

  final def setCookie(value: Cookie[Response]): Headers =
    value.encode.map(Headers(HeaderNames.setCookie, _)).getOrElse(Headers.empty)

  final def te(value: CharSequence): Headers =
    Headers(HeaderNames.te, value)

  final def trailer(value: CharSequence): Headers =
    Headers(HeaderNames.trailer, value)

  final def transferEncoding(value: CharSequence): Headers =
    Headers(HeaderNames.transferEncoding, value)

  final def upgrade(value: CharSequence): Headers =
    Headers(HeaderNames.upgrade, value)

  final def upgradeInsecureRequests(value: CharSequence): Headers =
    Headers(HeaderNames.upgradeInsecureRequests, value)

  final def userAgent(value: CharSequence): Headers =
    Headers(HeaderNames.userAgent, value)

  final def vary(value: CharSequence): Headers =
    Headers(HeaderNames.vary, value)

  final def via(value: CharSequence): Headers =
    Headers(HeaderNames.via, value)

  final def warning(value: CharSequence): Headers =
    Headers(HeaderNames.warning, value)

  final def webSocketLocation(value: CharSequence): Headers =
    Headers(HeaderNames.webSocketLocation, value)

  final def webSocketOrigin(value: CharSequence): Headers =
    Headers(HeaderNames.webSocketOrigin, value)

  final def webSocketProtocol(value: CharSequence): Headers =
    Headers(HeaderNames.webSocketProtocol, value)

  final def wwwAuthenticate(value: CharSequence): Headers =
    Headers(HeaderNames.wwwAuthenticate, value)

  final def xFrameOptions(value: CharSequence): Headers =
    Headers(HeaderNames.xFrameOptions, value)

  final def xRequestedWith(value: CharSequence): Headers =
    Headers(HeaderNames.xRequestedWith, value)
}
