package zhttp.http.headers

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http.Headers.BasicSchemeName
import zhttp.http.Headers.Literals.Name
import zhttp.http.{Cookie, HTTP_CHARSET, Headers, Method}
import zio.duration.Duration

import java.util.Base64

trait HeaderConstructors {
  final def accept(value: CharSequence): Headers =
    Headers(Name.Accept, value)

  final def acceptCharset(value: CharSequence): Headers =
    Headers(Name.AcceptCharset, value)

  final def acceptEncoding(value: CharSequence): Headers =
    Headers(Name.AcceptEncoding, value)

  final def acceptLanguage(value: CharSequence): Headers =
    Headers(Name.AcceptLanguage, value)

  final def acceptPatch(value: CharSequence): Headers =
    Headers(Name.AcceptPatch, value)

  final def acceptRanges(value: CharSequence): Headers =
    Headers(Name.AcceptRanges, value)

  final def accessControlAllowCredentials(value: Boolean): Headers =
    Headers(Name.AccessControlAllowCredentials, value.toString)

  final def accessControlAllowHeaders(value: CharSequence): Headers =
    Headers(Name.AccessControlAllowHeaders, value)

  final def accessControlAllowMethods(methods: Method*): Headers =
    Headers(Name.AccessControlAllowMethods, methods.mkString(", "))

  final def accessControlAllowOrigin(value: CharSequence): Headers =
    Headers(Name.AccessControlAllowOrigin, value)

  final def accessControlExposeHeaders(value: CharSequence): Headers =
    Headers(Name.AccessControlExposeHeaders, value)

  final def accessControlMaxAge(value: CharSequence): Headers =
    Headers(Name.AccessControlMaxAge, value)

  final def accessControlRequestHeaders(value: CharSequence): Headers =
    Headers(Name.AccessControlRequestHeaders, value)

  final def accessControlRequestMethod(method: Method): Headers =
    Headers(Name.AccessControlRequestMethod, method.asHttpMethod.name())

  final def age(value: CharSequence): Headers =
    Headers(Name.Age, value)

  final def allow(value: CharSequence): Headers =
    Headers(Name.Allow, value)

  final def authorization(value: CharSequence): Headers =
    Headers(Name.Authorization, value)

  final def basicAuthorizationHeader(username: String, password: String): Headers = {
    val authString    = String.format("%s:%s", username, password)
    val encodedAuthCB = new String(Base64.getEncoder.encode(authString.getBytes(HTTP_CHARSET)), HTTP_CHARSET)
    val value         = String.format("%s %s", BasicSchemeName, encodedAuthCB)
    Headers(HttpHeaderNames.AUTHORIZATION, value)
  }

  final def cacheControl(value: CharSequence): Headers =
    Headers(Name.CacheControl, value)

  final def cacheControlMaxAge(value: Duration): Headers =
    Headers(Name.CacheControl, s"public, max-age=${value.toSeconds}")

  final def connection(value: CharSequence): Headers =
    Headers(Name.Connection, value)

  final def contentBase(value: CharSequence): Headers =
    Headers(Name.ContentBase, value)

  final def contentDisposition(value: CharSequence): Headers =
    Headers(Name.ContentDisposition, value)

  final def contentEncoding(value: CharSequence): Headers =
    Headers(Name.ContentEncoding, value)

  final def contentLanguage(value: CharSequence): Headers =
    Headers(Name.ContentLanguage, value)

  final def contentLength(value: Long): Headers =
    Headers(Name.ContentLength, value.toString)

  final def contentLocation(value: CharSequence): Headers =
    Headers(Name.ContentLocation, value)

  final def contentMd5(value: CharSequence): Headers =
    Headers(Name.ContentMd5, value)

  final def contentRange(value: CharSequence): Headers =
    Headers(Name.ContentRange, value)

  final def contentSecurityPolicy(value: CharSequence): Headers =
    Headers(Name.ContentSecurityPolicy, value)

  final def contentTransferEncoding(value: CharSequence): Headers =
    Headers(Name.ContentTransferEncoding, value)

  final def contentType(value: CharSequence): Headers =
    Headers(Name.ContentType, value)

  final def cookie(value: CharSequence): Headers =
    Headers(Name.Cookie, value)

  final def date(value: CharSequence): Headers =
    Headers(Name.Date, value)

  final def dnt(value: CharSequence): Headers =
    Headers(Name.Dnt, value)

  final def etag(value: CharSequence): Headers =
    Headers(Name.Etag, value)

  final def expect(value: CharSequence): Headers =
    Headers(Name.Expect, value)

  final def expires(value: CharSequence): Headers =
    Headers(Name.Expires, value)

  final def from(value: CharSequence): Headers =
    Headers(Name.From, value)

  final def host(value: CharSequence): Headers =
    Headers(Name.Host, value)

  final def ifMatch(value: CharSequence): Headers =
    Headers(Name.IfMatch, value)

  final def ifModifiedSince(value: CharSequence): Headers =
    Headers(Name.IfModifiedSince, value)

  final def ifNoneMatch(value: CharSequence): Headers =
    Headers(Name.IfNoneMatch, value)

  final def ifRange(value: CharSequence): Headers =
    Headers(Name.IfRange, value)

  final def ifUnmodifiedSince(value: CharSequence): Headers =
    Headers(Name.IfUnmodifiedSince, value)

  final def lastModified(value: CharSequence): Headers =
    Headers(Name.LastModified, value)

  final def location(value: CharSequence): Headers =
    Headers(Name.Location, value)

  final def maxForwards(value: CharSequence): Headers =
    Headers(Name.MaxForwards, value)

  final def origin(value: CharSequence): Headers =
    Headers(Name.Origin, value)

  final def pragma(value: CharSequence): Headers =
    Headers(Name.Pragma, value)

  final def proxyAuthenticate(value: CharSequence): Headers =
    Headers(Name.ProxyAuthenticate, value)

  final def proxyAuthorization(value: CharSequence): Headers =
    Headers(Name.ProxyAuthorization, value)

  final def range(value: CharSequence): Headers =
    Headers(Name.Range, value)

  final def referer(value: CharSequence): Headers =
    Headers(Name.Referer, value)

  final def retryAfter(value: CharSequence): Headers =
    Headers(Name.RetryAfter, value)

  final def secWebSocketAccept(value: CharSequence): Headers =
    Headers(Name.SecWebSocketAccept, value)

  final def secWebSocketExtensions(value: CharSequence): Headers =
    Headers(Name.SecWebSocketExtensions, value)

  final def secWebSocketKey(value: CharSequence): Headers =
    Headers(Name.SecWebSocketKey, value)

  final def secWebSocketLocation(value: CharSequence): Headers =
    Headers(Name.SecWebSocketLocation, value)

  final def secWebSocketOrigin(value: CharSequence): Headers =
    Headers(Name.SecWebSocketOrigin, value)

  final def secWebSocketProtocol(value: CharSequence): Headers =
    Headers(Name.SecWebSocketProtocol, value)

  final def secWebSocketVersion(value: CharSequence): Headers =
    Headers(Name.SecWebSocketVersion, value)

  final def server(value: CharSequence): Headers =
    Headers(Name.Server, value)

  final def setCookie(value: Cookie): Headers =
    Headers(Name.SetCookie, value.encode)

  final def te(value: CharSequence): Headers =
    Headers(Name.Te, value)

  final def trailer(value: CharSequence): Headers =
    Headers(Name.Trailer, value)

  final def transferEncoding(value: CharSequence): Headers =
    Headers(Name.TransferEncoding, value)

  final def upgrade(value: CharSequence): Headers =
    Headers(Name.Upgrade, value)

  final def upgradeInsecureRequests(value: CharSequence): Headers =
    Headers(Name.UpgradeInsecureRequests, value)

  final def userAgent(value: CharSequence): Headers =
    Headers(Name.UserAgent, value)

  final def vary(value: CharSequence): Headers =
    Headers(Name.Vary, value)

  final def via(value: CharSequence): Headers =
    Headers(Name.Via, value)

  final def warning(value: CharSequence): Headers =
    Headers(Name.Warning, value)

  final def webSocketLocation(value: CharSequence): Headers =
    Headers(Name.WebSocketLocation, value)

  final def webSocketOrigin(value: CharSequence): Headers =
    Headers(Name.WebSocketOrigin, value)

  final def webSocketProtocol(value: CharSequence): Headers =
    Headers(Name.WebSocketProtocol, value)

  final def wwwAuthenticate(value: CharSequence): Headers =
    Headers(Name.WwwAuthenticate, value)

  final def xFrameOptions(value: CharSequence): Headers =
    Headers(Name.XFrameOptions, value)

  final def xRequestedWith(value: CharSequence): Headers =
    Headers(Name.XRequestedWith, value)
}
