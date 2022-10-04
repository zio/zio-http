package zio.http.api

import zio.http.model.HeaderNames
import zio.http.model.headers.values.{Accept, Age, Allow, CacheControl, ContentLength, Origin}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] trait HeaderInputs {
  def header[A](name: String, value: TextCodec[A]): In[A] =
    In.Header(name, value)

  final val accept: In[Accept]                        =
    header(HeaderNames.accept.toString(), TextCodec.string)
      .transform(Accept.toAccept, Accept.fromAccept)
  final val acceptEncoding: In[String]                = header(HeaderNames.acceptEncoding.toString(), TextCodec.string)
  final val acceptLanguage: In[String]                = header(HeaderNames.acceptLanguage.toString(), TextCodec.string)
  final val acceptRanges: In[String]                  = header(HeaderNames.acceptRanges.toString(), TextCodec.string)
  final val acceptPatch: In[String]                   = header(HeaderNames.acceptPatch.toString(), TextCodec.string)
  final val accessControlAllowCredentials: In[String] =
    header(HeaderNames.accessControlAllowCredentials.toString(), TextCodec.string)
  final val accessControlAllowHeaders: In[String]     =
    header(HeaderNames.accessControlAllowHeaders.toString(), TextCodec.string)
  final val accessControlAllowMethods: In[String]     =
    header(HeaderNames.accessControlAllowMethods.toString(), TextCodec.string)
  final val accessControlAllowOrigin: In[String]      =
    header(HeaderNames.accessControlAllowOrigin.toString(), TextCodec.string)
  final val accessControlExposeHeaders: In[String]    =
    header(HeaderNames.accessControlExposeHeaders.toString(), TextCodec.string)
  final val accessControlMaxAge: In[String] = header(HeaderNames.accessControlMaxAge.toString(), TextCodec.string)
  final val accessControlRequestHeaders: In[String] =
    header(HeaderNames.accessControlRequestHeaders.toString(), TextCodec.string)
  final val accessControlRequestMethod: In[String]  =
    header(HeaderNames.accessControlRequestMethod.toString(), TextCodec.string)
  final val age: In[Age]     = header(HeaderNames.age.toString(), TextCodec.string).transform(Age.toAge, Age.fromAge)
  final val allow: In[Allow] =
    header(HeaderNames.allow.toString(), TextCodec.string)
      .transform[Allow](Allow.toAllow, Allow.fromAllow)
  final val authorization: In[String]           = header(HeaderNames.authorization.toString(), TextCodec.string)
  final val cacheControl: In[CacheControl]      =
    header(HeaderNames.cacheControl.toString(), TextCodec.string)
      .transform[CacheControl](CacheControl.toCacheControl, CacheControl.fromCacheControl)
  final val connection: In[String]              = header(HeaderNames.connection.toString(), TextCodec.string)
  final val contentBase: In[String]             = header(HeaderNames.contentBase.toString(), TextCodec.string)
  final val contentEncoding: In[String]         = header(HeaderNames.contentEncoding.toString(), TextCodec.string)
  final val contentLanguage: In[String]         = header(HeaderNames.contentLanguage.toString(), TextCodec.string)
  final val contentLength: In[ContentLength]    = header(HeaderNames.contentLength.toString(), TextCodec.string)
    .transform(ContentLength.toContentLength, ContentLength.fromContentLength)
  final val contentLocation: In[String]         = header(HeaderNames.contentLocation.toString(), TextCodec.string)
  final val contentTransferEncoding: In[String] =
    header(HeaderNames.contentTransferEncoding.toString(), TextCodec.string)
  final val contentDisposition: In[String]      = header(HeaderNames.contentDisposition.toString(), TextCodec.string)
  final val contentMd5: In[String]              = header(HeaderNames.contentMd5.toString(), TextCodec.string)
  final val contentRange: In[String]            = header(HeaderNames.contentRange.toString(), TextCodec.string)
  final val contentSecurityPolicy: In[String]   = header(HeaderNames.contentSecurityPolicy.toString(), TextCodec.string)
  final val contentType: In[String]             = header(HeaderNames.contentType.toString(), TextCodec.string)
  final val cookie: In[String]                  = header(HeaderNames.cookie.toString(), TextCodec.string)
  final val date: In[String]                    = header(HeaderNames.date.toString(), TextCodec.string)
  final val dnt: In[String]                     = header(HeaderNames.dnt.toString(), TextCodec.string)
  final val etag: In[String]                    = header(HeaderNames.etag.toString(), TextCodec.string)
  final val expect: In[String]                  = header(HeaderNames.expect.toString(), TextCodec.string)
  final val expires: In[String]                 = header(HeaderNames.expires.toString(), TextCodec.string)
  final val from: In[String]                    = header(HeaderNames.from.toString(), TextCodec.string)
  final val host: In[String]                    = header(HeaderNames.host.toString(), TextCodec.string)
  final val ifMatch: In[String]                 = header(HeaderNames.ifMatch.toString(), TextCodec.string)
  final val ifModifiedSince: In[String]         = header(HeaderNames.ifModifiedSince.toString(), TextCodec.string)
  final val ifNoneMatch: In[String]             = header(HeaderNames.ifNoneMatch.toString(), TextCodec.string)
  final val ifRange: In[String]                 = header(HeaderNames.ifRange.toString(), TextCodec.string)
  final val ifUnmodifiedSince: In[String]       = header(HeaderNames.ifUnmodifiedSince.toString(), TextCodec.string)
  final val lastModified: In[String]            = header(HeaderNames.lastModified.toString(), TextCodec.string)
  final val location: In[String]                = header(HeaderNames.location.toString(), TextCodec.string)
  final val maxForwards: In[String]             = header(HeaderNames.maxForwards.toString(), TextCodec.string)
  final val origin: In[Origin]                  =
    header(HeaderNames.origin.toString(), TextCodec.string)
      .transform(Origin.toOrigin, Origin.fromOrigin)
  final val pragma: In[String]                  = header(HeaderNames.pragma.toString(), TextCodec.string)
  final val proxyAuthenticate: In[String]       = header(HeaderNames.proxyAuthenticate.toString(), TextCodec.string)
  final val proxyAuthorization: In[String]      = header(HeaderNames.proxyAuthorization.toString(), TextCodec.string)
  final val range: In[String]                   = header(HeaderNames.range.toString(), TextCodec.string)
  final val referer: In[String]                 = header(HeaderNames.referer.toString(), TextCodec.string)
  final val retryAfter: In[String]              = header(HeaderNames.retryAfter.toString(), TextCodec.string)
  final val secWebSocketLocation: In[String]    = header(HeaderNames.secWebSocketLocation.toString(), TextCodec.string)
  final val secWebSocketOrigin: In[String]      = header(HeaderNames.secWebSocketOrigin.toString(), TextCodec.string)
  final val secWebSocketProtocol: In[String]    = header(HeaderNames.secWebSocketProtocol.toString(), TextCodec.string)
  final val secWebSocketVersion: In[String]     = header(HeaderNames.secWebSocketVersion.toString(), TextCodec.string)
  final val secWebSocketKey: In[String]         = header(HeaderNames.secWebSocketKey.toString(), TextCodec.string)
  final val secWebSocketAccept: In[String]      = header(HeaderNames.secWebSocketAccept.toString(), TextCodec.string)
  final val secWebSocketExtensions: In[String] = header(HeaderNames.secWebSocketExtensions.toString(), TextCodec.string)
  final val server: In[String]                 = header(HeaderNames.server.toString(), TextCodec.string)
  final val setCookie: In[String]              = header(HeaderNames.setCookie.toString(), TextCodec.string)
  final val te: In[String]                     = header(HeaderNames.te.toString(), TextCodec.string)
  final val trailer: In[String]                = header(HeaderNames.trailer.toString(), TextCodec.string)
  final val transferEncoding: In[String]       = header(HeaderNames.transferEncoding.toString(), TextCodec.string)
  final val upgrade: In[String]                = header(HeaderNames.upgrade.toString(), TextCodec.string)
  final val upgradeInsecureRequests: In[String] =
    header(HeaderNames.upgradeInsecureRequests.toString(), TextCodec.string)
  final val userAgent: In[String]               = header(HeaderNames.userAgent.toString(), TextCodec.string)
  final val vary: In[String]                    = header(HeaderNames.vary.toString(), TextCodec.string)
  final val via: In[String]                     = header(HeaderNames.via.toString(), TextCodec.string)
  final val warning: In[String]                 = header(HeaderNames.warning.toString(), TextCodec.string)
  final val webSocketLocation: In[String]       = header(HeaderNames.webSocketLocation.toString(), TextCodec.string)
  final val webSocketOrigin: In[String]         = header(HeaderNames.webSocketOrigin.toString(), TextCodec.string)
  final val webSocketProtocol: In[String]       = header(HeaderNames.webSocketProtocol.toString(), TextCodec.string)
  final val wwwAuthenticate: In[String]         = header(HeaderNames.wwwAuthenticate.toString(), TextCodec.string)
  final val xFrameOptions: In[String]           = header(HeaderNames.xFrameOptions.toString(), TextCodec.string)
  final val xRequestedWith: In[String]          = header(HeaderNames.xRequestedWith.toString(), TextCodec.string)
}
