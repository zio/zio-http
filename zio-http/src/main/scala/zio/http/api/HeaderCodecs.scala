package zio.http.api

import zio.http.model.HeaderNames
import zio.http.model.headers.values.{Accept, AcceptEncoding, Age, Allow, CacheControl, ContentLength, Origin}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait HeaderCodecs {
  def header[A](name: String, value: TextCodec[A]): HeaderCodec[A] =
    HttpCodec.Header(name, value)

  final val accept: HeaderCodec[Accept]                        =
    header(HeaderNames.accept.toString(), TextCodec.string)
      .transform(Accept.toAccept, Accept.fromAccept)
  final val acceptEncoding: HeaderCodec[AcceptEncoding]        =
    header(HeaderNames.acceptEncoding.toString(), TextCodec.string)
      .transform(AcceptEncoding.toAcceptEncoding, AcceptEncoding.fromAcceptEncoding)
  final val acceptLanguage: HeaderCodec[String]                =
    header(HeaderNames.acceptLanguage.toString(), TextCodec.string)
  final val acceptRanges: HeaderCodec[String]                  =
    header(HeaderNames.acceptRanges.toString(), TextCodec.string)
  final val acceptPatch: HeaderCodec[String]                   =
    header(HeaderNames.acceptPatch.toString(), TextCodec.string)
  final val accessControlAllowCredentials: HeaderCodec[String] =
    header(HeaderNames.accessControlAllowCredentials.toString(), TextCodec.string)
  final val accessControlAllowHeaders: HeaderCodec[String]     =
    header(HeaderNames.accessControlAllowHeaders.toString(), TextCodec.string)
  final val accessControlAllowMethods: HeaderCodec[String]     =
    header(HeaderNames.accessControlAllowMethods.toString(), TextCodec.string)
  final val accessControlAllowOrigin: HeaderCodec[String]      =
    header(HeaderNames.accessControlAllowOrigin.toString(), TextCodec.string)
  final val accessControlExposeHeaders: HeaderCodec[String]    =
    header(HeaderNames.accessControlExposeHeaders.toString(), TextCodec.string)
  final val accessControlMaxAge: HeaderCodec[String]           =
    header(HeaderNames.accessControlMaxAge.toString(), TextCodec.string)
  final val accessControlRequestHeaders: HeaderCodec[String]   =
    header(HeaderNames.accessControlRequestHeaders.toString(), TextCodec.string)
  final val accessControlRequestMethod: HeaderCodec[String]    =
    header(HeaderNames.accessControlRequestMethod.toString(), TextCodec.string)
  final val age: HeaderCodec[Age]                              =
    header(HeaderNames.age.toString(), TextCodec.string).transform(Age.toAge, Age.fromAge)
  final val allow: HeaderCodec[Allow]                          =
    header(HeaderNames.allow.toString(), TextCodec.string)
      .transform[Allow](Allow.toAllow, Allow.fromAllow)
  final val authorization: HeaderCodec[String]                 =
    header(HeaderNames.authorization.toString(), TextCodec.string)
  final val cacheControl: HeaderCodec[CacheControl]            =
    header(HeaderNames.cacheControl.toString(), TextCodec.string)
      .transform[CacheControl](CacheControl.toCacheControl, CacheControl.fromCacheControl)
  final val connection: HeaderCodec[String]                    =
    header(HeaderNames.connection.toString(), TextCodec.string)
  final val contentBase: HeaderCodec[String]                   =
    header(HeaderNames.contentBase.toString(), TextCodec.string)
  final val contentEncoding: HeaderCodec[String]               =
    header(HeaderNames.contentEncoding.toString(), TextCodec.string)
  final val contentLanguage: HeaderCodec[String]               =
    header(HeaderNames.contentLanguage.toString(), TextCodec.string)
  final val contentLength: HeaderCodec[ContentLength]          =
    header(HeaderNames.contentLength.toString(), TextCodec.string)
      .transform(ContentLength.toContentLength, ContentLength.fromContentLength)
  final val contentLocation: HeaderCodec[String]               =
    header(HeaderNames.contentLocation.toString(), TextCodec.string)
  final val contentTransferEncoding: HeaderCodec[String]       =
    header(HeaderNames.contentTransferEncoding.toString(), TextCodec.string)
  final val contentDisposition: HeaderCodec[String]            =
    header(HeaderNames.contentDisposition.toString(), TextCodec.string)
  final val contentMd5: HeaderCodec[String]                    =
    header(HeaderNames.contentMd5.toString(), TextCodec.string)
  final val contentRange: HeaderCodec[String]                  =
    header(HeaderNames.contentRange.toString(), TextCodec.string)
  final val contentSecurityPolicy: HeaderCodec[String]         =
    header(HeaderNames.contentSecurityPolicy.toString(), TextCodec.string)
  final val contentType: HeaderCodec[String]                   =
    header(HeaderNames.contentType.toString(), TextCodec.string)
  final val cookie: HeaderCodec[String]                        = header(HeaderNames.cookie.toString(), TextCodec.string)
  final val date: HeaderCodec[String]                          = header(HeaderNames.date.toString(), TextCodec.string)
  final val dnt: HeaderCodec[String]                           = header(HeaderNames.dnt.toString(), TextCodec.string)
  final val etag: HeaderCodec[String]                          = header(HeaderNames.etag.toString(), TextCodec.string)
  final val expect: HeaderCodec[String]                        = header(HeaderNames.expect.toString(), TextCodec.string)
  final val expires: HeaderCodec[String]                 = header(HeaderNames.expires.toString(), TextCodec.string)
  final val from: HeaderCodec[String]                    = header(HeaderNames.from.toString(), TextCodec.string)
  final val host: HeaderCodec[String]                    = header(HeaderNames.host.toString(), TextCodec.string)
  final val ifMatch: HeaderCodec[String]                 = header(HeaderNames.ifMatch.toString(), TextCodec.string)
  final val ifModifiedSince: HeaderCodec[String]         =
    header(HeaderNames.ifModifiedSince.toString(), TextCodec.string)
  final val ifNoneMatch: HeaderCodec[String]             =
    header(HeaderNames.ifNoneMatch.toString(), TextCodec.string)
  final val ifRange: HeaderCodec[String]                 = header(HeaderNames.ifRange.toString(), TextCodec.string)
  final val ifUnmodifiedSince: HeaderCodec[String]       =
    header(HeaderNames.ifUnmodifiedSince.toString(), TextCodec.string)
  final val lastModified: HeaderCodec[String]            =
    header(HeaderNames.lastModified.toString(), TextCodec.string)
  final val location: HeaderCodec[String]                = header(HeaderNames.location.toString(), TextCodec.string)
  final val maxForwards: HeaderCodec[String]             =
    header(HeaderNames.maxForwards.toString(), TextCodec.string)
  final val origin: HeaderCodec[Origin]                  =
    header(HeaderNames.origin.toString(), TextCodec.string)
      .transform(Origin.toOrigin, Origin.fromOrigin)
  final val pragma: HeaderCodec[String]                  = header(HeaderNames.pragma.toString(), TextCodec.string)
  final val proxyAuthenticate: HeaderCodec[String]       =
    header(HeaderNames.proxyAuthenticate.toString(), TextCodec.string)
  final val proxyAuthorization: HeaderCodec[String]      =
    header(HeaderNames.proxyAuthorization.toString(), TextCodec.string)
  final val range: HeaderCodec[String]                   = header(HeaderNames.range.toString(), TextCodec.string)
  final val referer: HeaderCodec[String]                 = header(HeaderNames.referer.toString(), TextCodec.string)
  final val retryAfter: HeaderCodec[String]              =
    header(HeaderNames.retryAfter.toString(), TextCodec.string)
  final val secWebSocketLocation: HeaderCodec[String]    =
    header(HeaderNames.secWebSocketLocation.toString(), TextCodec.string)
  final val secWebSocketOrigin: HeaderCodec[String]      =
    header(HeaderNames.secWebSocketOrigin.toString(), TextCodec.string)
  final val secWebSocketProtocol: HeaderCodec[String]    =
    header(HeaderNames.secWebSocketProtocol.toString(), TextCodec.string)
  final val secWebSocketVersion: HeaderCodec[String]     =
    header(HeaderNames.secWebSocketVersion.toString(), TextCodec.string)
  final val secWebSocketKey: HeaderCodec[String]         =
    header(HeaderNames.secWebSocketKey.toString(), TextCodec.string)
  final val secWebSocketAccept: HeaderCodec[String]      =
    header(HeaderNames.secWebSocketAccept.toString(), TextCodec.string)
  final val secWebSocketExtensions: HeaderCodec[String]  =
    header(HeaderNames.secWebSocketExtensions.toString(), TextCodec.string)
  final val server: HeaderCodec[String]                  = header(HeaderNames.server.toString(), TextCodec.string)
  final val setCookie: HeaderCodec[String]               = header(HeaderNames.setCookie.toString(), TextCodec.string)
  final val te: HeaderCodec[String]                      = header(HeaderNames.te.toString(), TextCodec.string)
  final val trailer: HeaderCodec[String]                 = header(HeaderNames.trailer.toString(), TextCodec.string)
  final val transferEncoding: HeaderCodec[String]        =
    header(HeaderNames.transferEncoding.toString(), TextCodec.string)
  final val upgrade: HeaderCodec[String]                 = header(HeaderNames.upgrade.toString(), TextCodec.string)
  final val upgradeInsecureRequests: HeaderCodec[String] =
    header(HeaderNames.upgradeInsecureRequests.toString(), TextCodec.string)
  final val userAgent: HeaderCodec[String]               = header(HeaderNames.userAgent.toString(), TextCodec.string)
  final val vary: HeaderCodec[String]                    = header(HeaderNames.vary.toString(), TextCodec.string)
  final val via: HeaderCodec[String]                     = header(HeaderNames.via.toString(), TextCodec.string)
  final val warning: HeaderCodec[String]                 = header(HeaderNames.warning.toString(), TextCodec.string)
  final val webSocketLocation: HeaderCodec[String]       =
    header(HeaderNames.webSocketLocation.toString(), TextCodec.string)
  final val webSocketOrigin: HeaderCodec[String]         =
    header(HeaderNames.webSocketOrigin.toString(), TextCodec.string)
  final val webSocketProtocol: HeaderCodec[String]       =
    header(HeaderNames.webSocketProtocol.toString(), TextCodec.string)
  final val wwwAuthenticate: HeaderCodec[String]         =
    header(HeaderNames.wwwAuthenticate.toString(), TextCodec.string)
  final val xFrameOptions: HeaderCodec[String]           =
    header(HeaderNames.xFrameOptions.toString(), TextCodec.string)
  final val xRequestedWith: HeaderCodec[String]          =
    header(HeaderNames.xRequestedWith.toString(), TextCodec.string)
}
