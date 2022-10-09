package zio.http.api

import zio.http.model.HeaderNames
import zio.http.model.headers.values.{Accept, Age, Allow, CacheControl, ContentLength, Origin}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] trait HeaderInputs {
  def header[A](name: String, value: TextCodec[A]): In[In.HeaderType, A] =
    In.Header(name, value)

  final val accept: In[In.HeaderType, Accept]         =
    header(HeaderNames.accept.toString(), TextCodec.string)
      .transform(Accept.toAccept, Accept.fromAccept)
  final val acceptEncoding: In[In.HeaderType, String] = header(HeaderNames.acceptEncoding.toString(), TextCodec.string)
  final val acceptLanguage: In[In.HeaderType, String] = header(HeaderNames.acceptLanguage.toString(), TextCodec.string)
  final val acceptRanges: In[In.HeaderType, String]   = header(HeaderNames.acceptRanges.toString(), TextCodec.string)
  final val acceptPatch: In[In.HeaderType, String]    = header(HeaderNames.acceptPatch.toString(), TextCodec.string)
  final val accessControlAllowCredentials: In[In.HeaderType, String] =
    header(HeaderNames.accessControlAllowCredentials.toString(), TextCodec.string)
  final val accessControlAllowHeaders: In[In.HeaderType, String]     =
    header(HeaderNames.accessControlAllowHeaders.toString(), TextCodec.string)
  final val accessControlAllowMethods: In[In.HeaderType, String]     =
    header(HeaderNames.accessControlAllowMethods.toString(), TextCodec.string)
  final val accessControlAllowOrigin: In[In.HeaderType, String]      =
    header(HeaderNames.accessControlAllowOrigin.toString(), TextCodec.string)
  final val accessControlExposeHeaders: In[In.HeaderType, String]    =
    header(HeaderNames.accessControlExposeHeaders.toString(), TextCodec.string)
  final val accessControlMaxAge: In[In.HeaderType, String]           =
    header(HeaderNames.accessControlMaxAge.toString(), TextCodec.string)
  final val accessControlRequestHeaders: In[In.HeaderType, String]   =
    header(HeaderNames.accessControlRequestHeaders.toString(), TextCodec.string)
  final val accessControlRequestMethod: In[In.HeaderType, String]    =
    header(HeaderNames.accessControlRequestMethod.toString(), TextCodec.string)
  final val age: In[In.HeaderType, Age]                              =
    header(HeaderNames.age.toString(), TextCodec.string).transform(Age.toAge, Age.fromAge)
  final val allow: In[In.HeaderType, Allow]                          =
    header(HeaderNames.allow.toString(), TextCodec.string)
      .transform[Allow](Allow.toAllow, Allow.fromAllow)
  final val authorization: In[In.HeaderType, String] = header(HeaderNames.authorization.toString(), TextCodec.string)
  final val cacheControl: In[In.HeaderType, CacheControl] =
    header(HeaderNames.cacheControl.toString(), TextCodec.string)
      .transform[CacheControl](CacheControl.toCacheControl, CacheControl.fromCacheControl)
  final val connection: In[In.HeaderType, String]         = header(HeaderNames.connection.toString(), TextCodec.string)
  final val contentBase: In[In.HeaderType, String]        = header(HeaderNames.contentBase.toString(), TextCodec.string)
  final val contentEncoding: In[In.HeaderType, String]    =
    header(HeaderNames.contentEncoding.toString(), TextCodec.string)
  final val contentLanguage: In[In.HeaderType, String]    =
    header(HeaderNames.contentLanguage.toString(), TextCodec.string)
  final val contentLength: In[In.HeaderType, ContentLength]    =
    header(HeaderNames.contentLength.toString(), TextCodec.string)
      .transform(ContentLength.toContentLength, ContentLength.fromContentLength)
  final val contentLocation: In[In.HeaderType, String]         =
    header(HeaderNames.contentLocation.toString(), TextCodec.string)
  final val contentTransferEncoding: In[In.HeaderType, String] =
    header(HeaderNames.contentTransferEncoding.toString(), TextCodec.string)
  final val contentDisposition: In[In.HeaderType, String]      =
    header(HeaderNames.contentDisposition.toString(), TextCodec.string)
  final val contentMd5: In[In.HeaderType, String]   = header(HeaderNames.contentMd5.toString(), TextCodec.string)
  final val contentRange: In[In.HeaderType, String] = header(HeaderNames.contentRange.toString(), TextCodec.string)
  final val contentSecurityPolicy: In[In.HeaderType, String] =
    header(HeaderNames.contentSecurityPolicy.toString(), TextCodec.string)
  final val contentType: In[In.HeaderType, String]       = header(HeaderNames.contentType.toString(), TextCodec.string)
  final val cookie: In[In.HeaderType, String]            = header(HeaderNames.cookie.toString(), TextCodec.string)
  final val date: In[In.HeaderType, String]              = header(HeaderNames.date.toString(), TextCodec.string)
  final val dnt: In[In.HeaderType, String]               = header(HeaderNames.dnt.toString(), TextCodec.string)
  final val etag: In[In.HeaderType, String]              = header(HeaderNames.etag.toString(), TextCodec.string)
  final val expect: In[In.HeaderType, String]            = header(HeaderNames.expect.toString(), TextCodec.string)
  final val expires: In[In.HeaderType, String]           = header(HeaderNames.expires.toString(), TextCodec.string)
  final val from: In[In.HeaderType, String]              = header(HeaderNames.from.toString(), TextCodec.string)
  final val host: In[In.HeaderType, String]              = header(HeaderNames.host.toString(), TextCodec.string)
  final val ifMatch: In[In.HeaderType, String]           = header(HeaderNames.ifMatch.toString(), TextCodec.string)
  final val ifModifiedSince: In[In.HeaderType, String]   =
    header(HeaderNames.ifModifiedSince.toString(), TextCodec.string)
  final val ifNoneMatch: In[In.HeaderType, String]       = header(HeaderNames.ifNoneMatch.toString(), TextCodec.string)
  final val ifRange: In[In.HeaderType, String]           = header(HeaderNames.ifRange.toString(), TextCodec.string)
  final val ifUnmodifiedSince: In[In.HeaderType, String] =
    header(HeaderNames.ifUnmodifiedSince.toString(), TextCodec.string)
  final val lastModified: In[In.HeaderType, String]      = header(HeaderNames.lastModified.toString(), TextCodec.string)
  final val location: In[In.HeaderType, String]          = header(HeaderNames.location.toString(), TextCodec.string)
  final val maxForwards: In[In.HeaderType, String]       = header(HeaderNames.maxForwards.toString(), TextCodec.string)
  final val origin: In[In.HeaderType, Origin]            =
    header(HeaderNames.origin.toString(), TextCodec.string)
      .transform(Origin.toOrigin, Origin.fromOrigin)
  final val pragma: In[In.HeaderType, String]            = header(HeaderNames.pragma.toString(), TextCodec.string)
  final val proxyAuthenticate: In[In.HeaderType, String] =
    header(HeaderNames.proxyAuthenticate.toString(), TextCodec.string)
  final val proxyAuthorization: In[In.HeaderType, String] =
    header(HeaderNames.proxyAuthorization.toString(), TextCodec.string)
  final val range: In[In.HeaderType, String]              = header(HeaderNames.range.toString(), TextCodec.string)
  final val referer: In[In.HeaderType, String]            = header(HeaderNames.referer.toString(), TextCodec.string)
  final val retryAfter: In[In.HeaderType, String]         = header(HeaderNames.retryAfter.toString(), TextCodec.string)
  final val secWebSocketLocation: In[In.HeaderType, String]   =
    header(HeaderNames.secWebSocketLocation.toString(), TextCodec.string)
  final val secWebSocketOrigin: In[In.HeaderType, String]     =
    header(HeaderNames.secWebSocketOrigin.toString(), TextCodec.string)
  final val secWebSocketProtocol: In[In.HeaderType, String]   =
    header(HeaderNames.secWebSocketProtocol.toString(), TextCodec.string)
  final val secWebSocketVersion: In[In.HeaderType, String]    =
    header(HeaderNames.secWebSocketVersion.toString(), TextCodec.string)
  final val secWebSocketKey: In[In.HeaderType, String]        =
    header(HeaderNames.secWebSocketKey.toString(), TextCodec.string)
  final val secWebSocketAccept: In[In.HeaderType, String]     =
    header(HeaderNames.secWebSocketAccept.toString(), TextCodec.string)
  final val secWebSocketExtensions: In[In.HeaderType, String] =
    header(HeaderNames.secWebSocketExtensions.toString(), TextCodec.string)
  final val server: In[In.HeaderType, String]                 = header(HeaderNames.server.toString(), TextCodec.string)
  final val setCookie: In[In.HeaderType, String]        = header(HeaderNames.setCookie.toString(), TextCodec.string)
  final val te: In[In.HeaderType, String]               = header(HeaderNames.te.toString(), TextCodec.string)
  final val trailer: In[In.HeaderType, String]          = header(HeaderNames.trailer.toString(), TextCodec.string)
  final val transferEncoding: In[In.HeaderType, String] =
    header(HeaderNames.transferEncoding.toString(), TextCodec.string)
  final val upgrade: In[In.HeaderType, String]          = header(HeaderNames.upgrade.toString(), TextCodec.string)
  final val upgradeInsecureRequests: In[In.HeaderType, String] =
    header(HeaderNames.upgradeInsecureRequests.toString(), TextCodec.string)
  final val userAgent: In[In.HeaderType, String]         = header(HeaderNames.userAgent.toString(), TextCodec.string)
  final val vary: In[In.HeaderType, String]              = header(HeaderNames.vary.toString(), TextCodec.string)
  final val via: In[In.HeaderType, String]               = header(HeaderNames.via.toString(), TextCodec.string)
  final val warning: In[In.HeaderType, String]           = header(HeaderNames.warning.toString(), TextCodec.string)
  final val webSocketLocation: In[In.HeaderType, String] =
    header(HeaderNames.webSocketLocation.toString(), TextCodec.string)
  final val webSocketOrigin: In[In.HeaderType, String]   =
    header(HeaderNames.webSocketOrigin.toString(), TextCodec.string)
  final val webSocketProtocol: In[In.HeaderType, String] =
    header(HeaderNames.webSocketProtocol.toString(), TextCodec.string)
  final val wwwAuthenticate: In[In.HeaderType, String]   =
    header(HeaderNames.wwwAuthenticate.toString(), TextCodec.string)
  final val xFrameOptions: In[In.HeaderType, String]  = header(HeaderNames.xFrameOptions.toString(), TextCodec.string)
  final val xRequestedWith: In[In.HeaderType, String] = header(HeaderNames.xRequestedWith.toString(), TextCodec.string)
}
