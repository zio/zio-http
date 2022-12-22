package zio.http.api

import zio.http.api.internal.RichTextCodec.comma
import zio.http.model.HeaderNames
import zio.http.api.internal.RichTextCodec
import zio.http.model.headers.HeaderTypedValues._
import zio.http.model.headers.values._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait HeaderCodecs {
  private[api] def header[A](name: String, value: RichTextCodec[A]): HeaderCodec[A] =
    HttpCodec.Header(name, value)

  final val accept: HeaderCodec[Accept] =
    header(HeaderNames.accept.toString(), HeaderValueCodecs.acceptCodec)

  final val acceptEncoding: HeaderCodec[AcceptEncoding] =
    header(HeaderNames.acceptEncoding.toString(), HeaderValueCodecs.acceptEncodingCodec)

  final val acceptLanguage: HeaderCodec[AcceptLanguage] =
    header(HeaderNames.acceptLanguage.toString(), HeaderValueCodecs.acceptLanguageCodec)

  final val acceptRanges: HeaderCodec[AcceptRanges] =
    header(HeaderNames.acceptRanges.toString(), HeaderValueCodecs.acceptRangesCodec)

  final val acceptPatch: HeaderCodec[AcceptPatch] =
    header(HeaderNames.acceptPatch.toString(), HeaderValueCodecs.acceptPatchCodec)

  final val accessControlAllowCredentials: HeaderCodec[AccessControlAllowCredentials] =
    header(
      HeaderNames.accessControlAllowCredentials.toString,
      HeaderValueCodecs.accessControlAllowCredentialsCodec,
    )

  final val accessControlAllowHeaders: HeaderCodec[AccessControlAllowHeaders] =
    header(HeaderNames.accessControlAllowHeaders.toString, HeaderValueCodecs.accessControlAllowHeadersCodec)

  final val accessControlAllowMethods: HeaderCodec[AccessControlAllowMethods] =
    header(HeaderNames.accessControlAllowMethods.toString, HeaderValueCodecs.accessControlAllowMethodsCodec)

  final val accessControlAllowOrigin: HeaderCodec[AccessControlAllowOrigin] =
    header(HeaderNames.accessControlAllowOrigin.toString, HeaderValueCodecs.accessControlAllowOriginCodec)

  final val accessControlExposeHeaders: HeaderCodec[AccessControlExposeHeaders] =
    header(HeaderNames.accessControlExposeHeaders.toString(), HeaderValueCodecs.accessControlExposeHeadersCodec)

  final val accessControlMaxAge: HeaderCodec[AccessControlMaxAge] =
    header(HeaderNames.accessControlMaxAge.toString, HeaderValueCodecs.accessControlMaxAgeCodec)

  final val accessControlRequestHeaders: HeaderCodec[AccessControlRequestHeaders] =
    header(
      HeaderNames.accessControlRequestHeaders.toString(),
      HeaderValueCodecs.accessControlRequestHeadersCodec,
    )

  final val accessControlRequestMethod: HeaderCodec[AccessControlRequestMethod] =
    header(HeaderNames.accessControlRequestMethod.toString(), HeaderValueCodecs.accessControlRequestMethodCodec)

  final val age: HeaderCodec[Age] =
    header(HeaderNames.age.toString(), HeaderValueCodecs.ageCodec)

  final val allow: HeaderCodec[Allow] =
    header(HeaderNames.allow.toString(), HeaderValueCodecs.allowCodec)

  final val authorization: HeaderCodec[Authorization] =
    header(HeaderNames.authorization.toString(), HeaderValueCodecs.authorizationCodec)

  final val cacheControl: HeaderCodec[CacheControl] =
    header(HeaderNames.cacheControl.toString(), HeaderValueCodecs.cacheControlCodec)

  final val connection: HeaderCodec[Connection] =
    header(HeaderNames.connection.toString(), HeaderValueCodecs.connectionCodec)

  final val contentBase: HeaderCodec[ContentBase] =
    header(HeaderNames.contentBase.toString, HeaderValueCodecs.contentBaseCodec)

  final val contentEncoding: HeaderCodec[ContentEncoding] =
    header(HeaderNames.contentEncoding.toString, HeaderValueCodecs.contentEncodingCodec)

  final val contentLanguage: HeaderCodec[ContentLanguage] =
    header(HeaderNames.contentLanguage.toString, HeaderValueCodecs.contentLanguageCodec)

  final val contentLength: HeaderCodec[ContentLength] =
    header(HeaderNames.contentLength.toString, HeaderValueCodecs.contentLengthCodec)

  final val contentLocation: HeaderCodec[ContentLocation] =
    header(HeaderNames.contentLocation.toString, HeaderValueCodecs.contentLocationCodec)

  final val contentTransferEncoding: HeaderCodec[ContentTransferEncoding] =
    header(HeaderNames.contentTransferEncoding.toString, HeaderValueCodecs.contentTransferEncodingCodec)

  final val contentDisposition: HeaderCodec[ContentDisposition] =
    header(HeaderNames.contentDisposition.toString, HeaderValueCodecs.contentDispositionCodec)

  final val contentMd5: HeaderCodec[ContentMd5] =
    header(HeaderNames.contentMd5.toString, HeaderValueCodecs.contentMd5Codec)

  final val contentRange: HeaderCodec[ContentRange] =
    header(HeaderNames.contentRange.toString, HeaderValueCodecs.contentRangeCodec)

  final val contentSecurityPolicy: HeaderCodec[ContentSecurityPolicy] =
    header(HeaderNames.contentSecurityPolicy.toString, HeaderValueCodecs.contentSecurityPolicyCodec)

  final val contentType: HeaderCodec[ContentType] =
    header(HeaderNames.contentType.toString, HeaderValueCodecs.contentTypeCodec)

  final val cookie: HeaderCodec[RequestCookie] =
    header(HeaderNames.cookie.toString(), HeaderValueCodecs.requestCookieCodec)

  final val date: HeaderCodec[Date] = header(HeaderNames.date.toString(), HeaderValueCodecs.dateCodec)

  final val dnt: HeaderCodec[DNT] =
    header(HeaderNames.dnt.toString(), HeaderValueCodecs.dntCodec)

  final val etag: HeaderCodec[ETag] = header(HeaderNames.etag.toString, HeaderValueCodecs.etagCodec)

  final val expect: HeaderCodec[Expect] =
    header(HeaderNames.expect.toString, HeaderValueCodecs.expectCodec)

  final val expires: HeaderCodec[Expires] =
    header(HeaderNames.expires.toString, HeaderValueCodecs.expiresCodec)

  final val from: HeaderCodec[From] = header(HeaderNames.from.toString, HeaderValueCodecs.fromCodec)

  final val host: HeaderCodec[Host] = header(HeaderNames.host.toString, HeaderValueCodecs.hostCodec)

  final val ifMatch: HeaderCodec[IfMatch] = header(HeaderNames.ifMatch.toString, HeaderValueCodecs.ifMatchCodec)

  final val ifModifiedSince: HeaderCodec[IfModifiedSince] =
    header(HeaderNames.ifModifiedSince.toString, HeaderValueCodecs.ifModifiedSinceCodec)

  final val ifNoneMatch: HeaderCodec[IfNoneMatch] =
    header(HeaderNames.ifNoneMatch.toString(), HeaderValueCodecs.ifNoneMatchCodec)

  final val ifRange: HeaderCodec[IfRange] =
    header(HeaderNames.ifRange.toString, HeaderValueCodecs.ifRangeCodec)

  final val ifUnmodifiedSince: HeaderCodec[IfUnmodifiedSince] =
    header(HeaderNames.ifUnmodifiedSince.toString(), HeaderValueCodecs.ifUnmodifiedSinceCodec)

  final val lastModified: HeaderCodec[LastModified] =
    header(HeaderNames.lastModified.toString(), HeaderValueCodecs.lastModifiedCodec)

  final val location: HeaderCodec[Location] =
    header(HeaderNames.location.toString(), HeaderValueCodecs.locationCodec)

  final val maxForwards: HeaderCodec[MaxForwards] =
    header(HeaderNames.maxForwards.toString(), HeaderValueCodecs.maxForwardsCodec)

  final val origin: HeaderCodec[Origin] =
    header(HeaderNames.origin.toString(), HeaderValueCodecs.originCodec)

  final val pragma: HeaderCodec[Pragma] = header(HeaderNames.pragma.toString(), HeaderValueCodecs.pragmaCodec)

  final val proxyAuthenticate: HeaderCodec[ProxyAuthenticate] =
    header(HeaderNames.proxyAuthenticate.toString(), HeaderValueCodecs.proxyAuthenticateCodec)

  final val proxyAuthorization: HeaderCodec[ProxyAuthorization] =
    header(HeaderNames.proxyAuthorization.toString(), HeaderValueCodecs.proxyAuthorizationCodec)

  final val range: HeaderCodec[Range] = header(HeaderNames.range.toString(), HeaderValueCodecs.rangeCodec)

  final val referer: HeaderCodec[Referer] =
    header(HeaderNames.referer.toString(), HeaderValueCodecs.refererCodec)

  final val retryAfter: HeaderCodec[RetryAfter] =
    header(HeaderNames.retryAfter.toString(), HeaderValueCodecs.retryAfterCodec)

  final val secWebSocketLocation: HeaderCodec[SecWebSocketLocation] =
    header(HeaderNames.secWebSocketLocation.toString(), HeaderValueCodecs.secWebSocketLocationCodec)

  final val secWebSocketOrigin: HeaderCodec[SecWebSocketOrigin] =
    header(HeaderNames.secWebSocketOrigin.toString(), HeaderValueCodecs.secWebSocketOriginCodec)

  final val secWebSocketProtocol: HeaderCodec[SecWebSocketProtocol] =
    header(HeaderNames.secWebSocketProtocol.toString(), HeaderValueCodecs.secWebSocketProtocolCodec)

  final val secWebSocketVersion: HeaderCodec[SecWebSocketVersion] =
    header(HeaderNames.secWebSocketVersion.toString(), HeaderValueCodecs.secWebSocketVersionCodec)

  final val secWebSocketKey: HeaderCodec[SecWebSocketKey] =
    header(HeaderNames.secWebSocketKey.toString(), HeaderValueCodecs.secWebSocketKeyCodec)

  final val secWebSocketAccept: HeaderCodec[SecWebSocketAccept] =
    header(HeaderNames.secWebSocketAccept.toString(), HeaderValueCodecs.secWebSocketAcceptCodec)

  final val secWebSocketExtensions: HeaderCodec[SecWebSocketExtensions] =
    header(HeaderNames.secWebSocketExtensions.toString(), HeaderValueCodecs.secWebSocketExtensionsCodec)

  final val server: HeaderCodec[Server] =
    header(HeaderNames.server.toString(), HeaderValueCodecs.serverCodec)

  final val setCookie: HeaderCodec[ResponseCookie] =
    header(HeaderNames.setCookie.toString(), HeaderValueCodecs.responseCookieCodec)

  final val te: HeaderCodec[Te] = header(HeaderNames.te.toString(), HeaderValueCodecs.teCodec)

  final val trailer: HeaderCodec[Trailer] =
    header(HeaderNames.trailer.toString(), HeaderValueCodecs.trailerCodec)

  final val transferEncoding: HeaderCodec[TransferEncoding] =
    header(HeaderNames.transferEncoding.toString(), HeaderValueCodecs.transferEncodingCodec)

  final val upgrade: HeaderCodec[Upgrade] =
    header(HeaderNames.upgrade.toString(), HeaderValueCodecs.upgradeCodec)

  final val upgradeInsecureRequests: HeaderCodec[UpgradeInsecureRequests] =
    header(HeaderNames.upgradeInsecureRequests.toString(), HeaderValueCodecs.upgradeInsecureRequestsCodec)

  final val userAgent: HeaderCodec[UserAgent] =
    header(HeaderNames.userAgent.toString(), HeaderValueCodecs.userAgentCodec)

  final val vary: HeaderCodec[Vary] = header(HeaderNames.vary.toString(), HeaderValueCodecs.varyCodec)

  final val via: HeaderCodec[Via] = header(HeaderNames.via.toString(), HeaderValueCodecs.viaCodec)

  final val warning: HeaderCodec[Warning] =
    header(HeaderNames.warning.toString(), HeaderValueCodecs.warningCodec)

  final val webSocketLocation: HeaderCodec[SecWebSocketLocation] =
    header(HeaderNames.webSocketLocation.toString(), HeaderValueCodecs.secWebSocketLocationCodec)

  final val webSocketOrigin: HeaderCodec[SecWebSocketOrigin] =
    header(HeaderNames.webSocketOrigin.toString(), HeaderValueCodecs.secWebSocketOriginCodec)

  final val webSocketProtocol: HeaderCodec[SecWebSocketProtocol] =
    header(HeaderNames.webSocketProtocol.toString(), HeaderValueCodecs.secWebSocketProtocolCodec)

  final val wwwAuthenticate: HeaderCodec[WWWAuthenticate] =
    header(HeaderNames.wwwAuthenticate.toString(), HeaderValueCodecs.wwwAuthenticateCodec)

  final val xFrameOptions: HeaderCodec[XFrameOptions] =
    header(HeaderNames.xFrameOptions.toString(), HeaderValueCodecs.xFrameOptionsCodec)

  final val xRequestedWith: HeaderCodec[XRequestedWith] =
    header(HeaderNames.xRequestedWith.toString(), HeaderValueCodecs.xRequestedWithCodec)
}
