package zio.http.api

import zio.http.model.HeaderNames
import zio.http.model.headers.values.{Accept, Age, Allow, CacheControl, ContentLength, Origin}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] trait HeaderInputs {
  def header[A](name: String, value: TextCodec[A]): HttpCodec[CodecType.Header, A] =
    In.Header(name, value)

  final val accept: HttpCodec[CodecType.Header, Accept]                        =
    header(HeaderNames.accept.toString(), TextCodec.string)
      .transform(Accept.toAccept, Accept.fromAccept)
  final val acceptEncoding: HttpCodec[CodecType.Header, String]                =
    header(HeaderNames.acceptEncoding.toString(), TextCodec.string)
  final val acceptLanguage: HttpCodec[CodecType.Header, String]                =
    header(HeaderNames.acceptLanguage.toString(), TextCodec.string)
  final val acceptRanges: HttpCodec[CodecType.Header, String]                  =
    header(HeaderNames.acceptRanges.toString(), TextCodec.string)
  final val acceptPatch: HttpCodec[CodecType.Header, String]                   =
    header(HeaderNames.acceptPatch.toString(), TextCodec.string)
  final val accessControlAllowCredentials: HttpCodec[CodecType.Header, String] =
    header(HeaderNames.accessControlAllowCredentials.toString(), TextCodec.string)
  final val accessControlAllowHeaders: HttpCodec[CodecType.Header, String]     =
    header(HeaderNames.accessControlAllowHeaders.toString(), TextCodec.string)
  final val accessControlAllowMethods: HttpCodec[CodecType.Header, String]     =
    header(HeaderNames.accessControlAllowMethods.toString(), TextCodec.string)
  final val accessControlAllowOrigin: HttpCodec[CodecType.Header, String]      =
    header(HeaderNames.accessControlAllowOrigin.toString(), TextCodec.string)
  final val accessControlExposeHeaders: HttpCodec[CodecType.Header, String]    =
    header(HeaderNames.accessControlExposeHeaders.toString(), TextCodec.string)
  final val accessControlMaxAge: HttpCodec[CodecType.Header, String]           =
    header(HeaderNames.accessControlMaxAge.toString(), TextCodec.string)
  final val accessControlRequestHeaders: HttpCodec[CodecType.Header, String]   =
    header(HeaderNames.accessControlRequestHeaders.toString(), TextCodec.string)
  final val accessControlRequestMethod: HttpCodec[CodecType.Header, String]    =
    header(HeaderNames.accessControlRequestMethod.toString(), TextCodec.string)
  final val age: HttpCodec[CodecType.Header, Age]                              =
    header(HeaderNames.age.toString(), TextCodec.string).transform(Age.toAge, Age.fromAge)
  final val allow: HttpCodec[CodecType.Header, Allow]                          =
    header(HeaderNames.allow.toString(), TextCodec.string)
      .transform[Allow](Allow.toAllow, Allow.fromAllow)
  final val authorization: HttpCodec[CodecType.Header, String]                 =
    header(HeaderNames.authorization.toString(), TextCodec.string)
  final val cacheControl: HttpCodec[CodecType.Header, CacheControl]            =
    header(HeaderNames.cacheControl.toString(), TextCodec.string)
      .transform[CacheControl](CacheControl.toCacheControl, CacheControl.fromCacheControl)
  final val connection: HttpCodec[CodecType.Header, String]                    =
    header(HeaderNames.connection.toString(), TextCodec.string)
  final val contentBase: HttpCodec[CodecType.Header, String]                   =
    header(HeaderNames.contentBase.toString(), TextCodec.string)
  final val contentEncoding: HttpCodec[CodecType.Header, String]               =
    header(HeaderNames.contentEncoding.toString(), TextCodec.string)
  final val contentLanguage: HttpCodec[CodecType.Header, String]               =
    header(HeaderNames.contentLanguage.toString(), TextCodec.string)
  final val contentLength: HttpCodec[CodecType.Header, ContentLength]          =
    header(HeaderNames.contentLength.toString(), TextCodec.string)
      .transform(ContentLength.toContentLength, ContentLength.fromContentLength)
  final val contentLocation: HttpCodec[CodecType.Header, String]               =
    header(HeaderNames.contentLocation.toString(), TextCodec.string)
  final val contentTransferEncoding: HttpCodec[CodecType.Header, String]       =
    header(HeaderNames.contentTransferEncoding.toString(), TextCodec.string)
  final val contentDisposition: HttpCodec[CodecType.Header, String]            =
    header(HeaderNames.contentDisposition.toString(), TextCodec.string)
  final val contentMd5: HttpCodec[CodecType.Header, String]                    =
    header(HeaderNames.contentMd5.toString(), TextCodec.string)
  final val contentRange: HttpCodec[CodecType.Header, String]                  =
    header(HeaderNames.contentRange.toString(), TextCodec.string)
  final val contentSecurityPolicy: HttpCodec[CodecType.Header, String]         =
    header(HeaderNames.contentSecurityPolicy.toString(), TextCodec.string)
  final val contentType: HttpCodec[CodecType.Header, String]                   =
    header(HeaderNames.contentType.toString(), TextCodec.string)
  final val cookie: HttpCodec[CodecType.Header, String]  = header(HeaderNames.cookie.toString(), TextCodec.string)
  final val date: HttpCodec[CodecType.Header, String]    = header(HeaderNames.date.toString(), TextCodec.string)
  final val dnt: HttpCodec[CodecType.Header, String]     = header(HeaderNames.dnt.toString(), TextCodec.string)
  final val etag: HttpCodec[CodecType.Header, String]    = header(HeaderNames.etag.toString(), TextCodec.string)
  final val expect: HttpCodec[CodecType.Header, String]  = header(HeaderNames.expect.toString(), TextCodec.string)
  final val expires: HttpCodec[CodecType.Header, String] = header(HeaderNames.expires.toString(), TextCodec.string)
  final val from: HttpCodec[CodecType.Header, String]    = header(HeaderNames.from.toString(), TextCodec.string)
  final val host: HttpCodec[CodecType.Header, String]    = header(HeaderNames.host.toString(), TextCodec.string)
  final val ifMatch: HttpCodec[CodecType.Header, String] = header(HeaderNames.ifMatch.toString(), TextCodec.string)
  final val ifModifiedSince: HttpCodec[CodecType.Header, String] =
    header(HeaderNames.ifModifiedSince.toString(), TextCodec.string)
  final val ifNoneMatch: HttpCodec[CodecType.Header, String]     =
    header(HeaderNames.ifNoneMatch.toString(), TextCodec.string)
  final val ifRange: HttpCodec[CodecType.Header, String] = header(HeaderNames.ifRange.toString(), TextCodec.string)
  final val ifUnmodifiedSince: HttpCodec[CodecType.Header, String] =
    header(HeaderNames.ifUnmodifiedSince.toString(), TextCodec.string)
  final val lastModified: HttpCodec[CodecType.Header, String]      =
    header(HeaderNames.lastModified.toString(), TextCodec.string)
  final val location: HttpCodec[CodecType.Header, String]    = header(HeaderNames.location.toString(), TextCodec.string)
  final val maxForwards: HttpCodec[CodecType.Header, String] =
    header(HeaderNames.maxForwards.toString(), TextCodec.string)
  final val origin: HttpCodec[CodecType.Header, Origin]      =
    header(HeaderNames.origin.toString(), TextCodec.string)
      .transform(Origin.toOrigin, Origin.fromOrigin)
  final val pragma: HttpCodec[CodecType.Header, String]      = header(HeaderNames.pragma.toString(), TextCodec.string)
  final val proxyAuthenticate: HttpCodec[CodecType.Header, String]  =
    header(HeaderNames.proxyAuthenticate.toString(), TextCodec.string)
  final val proxyAuthorization: HttpCodec[CodecType.Header, String] =
    header(HeaderNames.proxyAuthorization.toString(), TextCodec.string)
  final val range: HttpCodec[CodecType.Header, String]      = header(HeaderNames.range.toString(), TextCodec.string)
  final val referer: HttpCodec[CodecType.Header, String]    = header(HeaderNames.referer.toString(), TextCodec.string)
  final val retryAfter: HttpCodec[CodecType.Header, String] =
    header(HeaderNames.retryAfter.toString(), TextCodec.string)
  final val secWebSocketLocation: HttpCodec[CodecType.Header, String]   =
    header(HeaderNames.secWebSocketLocation.toString(), TextCodec.string)
  final val secWebSocketOrigin: HttpCodec[CodecType.Header, String]     =
    header(HeaderNames.secWebSocketOrigin.toString(), TextCodec.string)
  final val secWebSocketProtocol: HttpCodec[CodecType.Header, String]   =
    header(HeaderNames.secWebSocketProtocol.toString(), TextCodec.string)
  final val secWebSocketVersion: HttpCodec[CodecType.Header, String]    =
    header(HeaderNames.secWebSocketVersion.toString(), TextCodec.string)
  final val secWebSocketKey: HttpCodec[CodecType.Header, String]        =
    header(HeaderNames.secWebSocketKey.toString(), TextCodec.string)
  final val secWebSocketAccept: HttpCodec[CodecType.Header, String]     =
    header(HeaderNames.secWebSocketAccept.toString(), TextCodec.string)
  final val secWebSocketExtensions: HttpCodec[CodecType.Header, String] =
    header(HeaderNames.secWebSocketExtensions.toString(), TextCodec.string)
  final val server: HttpCodec[CodecType.Header, String]    = header(HeaderNames.server.toString(), TextCodec.string)
  final val setCookie: HttpCodec[CodecType.Header, String] = header(HeaderNames.setCookie.toString(), TextCodec.string)
  final val te: HttpCodec[CodecType.Header, String]        = header(HeaderNames.te.toString(), TextCodec.string)
  final val trailer: HttpCodec[CodecType.Header, String]   = header(HeaderNames.trailer.toString(), TextCodec.string)
  final val transferEncoding: HttpCodec[CodecType.Header, String] =
    header(HeaderNames.transferEncoding.toString(), TextCodec.string)
  final val upgrade: HttpCodec[CodecType.Header, String] = header(HeaderNames.upgrade.toString(), TextCodec.string)
  final val upgradeInsecureRequests: HttpCodec[CodecType.Header, String] =
    header(HeaderNames.upgradeInsecureRequests.toString(), TextCodec.string)
  final val userAgent: HttpCodec[CodecType.Header, String] = header(HeaderNames.userAgent.toString(), TextCodec.string)
  final val vary: HttpCodec[CodecType.Header, String]      = header(HeaderNames.vary.toString(), TextCodec.string)
  final val via: HttpCodec[CodecType.Header, String]       = header(HeaderNames.via.toString(), TextCodec.string)
  final val warning: HttpCodec[CodecType.Header, String]   = header(HeaderNames.warning.toString(), TextCodec.string)
  final val webSocketLocation: HttpCodec[CodecType.Header, String] =
    header(HeaderNames.webSocketLocation.toString(), TextCodec.string)
  final val webSocketOrigin: HttpCodec[CodecType.Header, String]   =
    header(HeaderNames.webSocketOrigin.toString(), TextCodec.string)
  final val webSocketProtocol: HttpCodec[CodecType.Header, String] =
    header(HeaderNames.webSocketProtocol.toString(), TextCodec.string)
  final val wwwAuthenticate: HttpCodec[CodecType.Header, String]   =
    header(HeaderNames.wwwAuthenticate.toString(), TextCodec.string)
  final val xFrameOptions: HttpCodec[CodecType.Header, String]     =
    header(HeaderNames.xFrameOptions.toString(), TextCodec.string)
  final val xRequestedWith: HttpCodec[CodecType.Header, String]    =
    header(HeaderNames.xRequestedWith.toString(), TextCodec.string)
}
