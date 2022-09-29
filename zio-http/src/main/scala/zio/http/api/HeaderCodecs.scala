package zio.http.api

import zio.http.model.HeaderNames
import zio.http.model.headers.values._
import zio.http.api.internal.TextCodec
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait HeaderCodecs {
  private[api] def header[A](name: String, value: TextCodec[A]): HeaderCodec[A] =
    HttpCodec.Header(name, value, optional = false)

  final val accept: HeaderCodec[Accept]                                               =
    header(HeaderNames.accept.toString(), TextCodec.string)
      .transform(Accept.toAccept, Accept.fromAccept)
  final val acceptEncoding: HeaderCodec[AcceptEncoding]                               =
    header(HeaderNames.acceptEncoding.toString(), TextCodec.string)
      .transform(AcceptEncoding.toAcceptEncoding, AcceptEncoding.fromAcceptEncoding)
  final val acceptLanguage: HeaderCodec[AcceptLanguage]                               =
    header(HeaderNames.acceptLanguage.toString(), TextCodec.string)
      .transform(AcceptLanguage.toAcceptLanguage, AcceptLanguage.fromAcceptLanguage)
  final val acceptRanges: HeaderCodec[AcceptRanges]                                   =
    header(HeaderNames.acceptRanges.toString(), TextCodec.string)
      .transform(AcceptRanges.to, AcceptRanges.from)
  final val acceptPatch: HeaderCodec[AcceptPatch]                                     =
    header(HeaderNames.acceptPatch.toString(), TextCodec.string)
      .transform(AcceptPatch.toAcceptPatch, AcceptPatch.fromAcceptPatch)
  final val accessControlAllowCredentials: HeaderCodec[AccessControlAllowCredentials] =
    header(HeaderNames.accessControlAllowCredentials.toString(), TextCodec.string)
      .transform(
        AccessControlAllowCredentials.toAccessControlAllowCredentials,
        AccessControlAllowCredentials.fromAccessControlAllowCredentials,
      )
  final val accessControlAllowHeaders: HeaderCodec[AccessControlAllowHeaders]         =
    header(HeaderNames.accessControlAllowHeaders.toString(), TextCodec.string)
      .transform(
        AccessControlAllowHeaders.toAccessControlAllowHeaders,
        AccessControlAllowHeaders.fromAccessControlAllowHeaders,
      )
  final val accessControlAllowMethods: HeaderCodec[AccessControlAllowMethods]         =
    header(HeaderNames.accessControlAllowMethods.toString(), TextCodec.string)
      .transform(
        AccessControlAllowMethods.toAccessControlAllowMethods,
        AccessControlAllowMethods.fromAccessControlAllowMethods,
      )
  final val accessControlAllowOrigin: HeaderCodec[AccessControlAllowOrigin]           =
    header(HeaderNames.accessControlAllowOrigin.toString(), TextCodec.string)
      .transform(
        AccessControlAllowOrigin.toAccessControlAllowOrigin,
        AccessControlAllowOrigin.fromAccessControlAllowOrigin,
      )
  final val accessControlExposeHeaders: HeaderCodec[AccessControlExposeHeaders]       =
    header(HeaderNames.accessControlExposeHeaders.toString(), TextCodec.string)
      .transform(
        AccessControlExposeHeaders.toAccessControlExposeHeaders,
        AccessControlExposeHeaders.fromAccessControlExposeHeaders,
      )
  final val accessControlMaxAge: HeaderCodec[AccessControlMaxAge]                     =
    header(HeaderNames.accessControlMaxAge.toString(), TextCodec.string)
      .transform[AccessControlMaxAge](
        AccessControlMaxAge.toAccessControlMaxAge,
        AccessControlMaxAge.fromAccessControlMaxAge,
      )
  final val accessControlRequestHeaders: HeaderCodec[AccessControlRequestHeaders]     =
    header(HeaderNames.accessControlRequestHeaders.toString(), TextCodec.string)
      .transform(
        AccessControlRequestHeaders.toAccessControlRequestHeaders,
        AccessControlRequestHeaders.fromAccessControlRequestHeaders,
      )
  final val accessControlRequestMethod: HeaderCodec[AccessControlRequestMethod]       =
    header(HeaderNames.accessControlRequestMethod.toString(), TextCodec.string)
      .transform(
        AccessControlRequestMethod.toAccessControlRequestMethod,
        AccessControlRequestMethod.fromAccessControlRequestMethod,
      )
  final val age: HeaderCodec[Age]                                                     =
    header(HeaderNames.age.toString(), TextCodec.string).transform(Age.toAge, Age.fromAge)
  final val allow: HeaderCodec[Allow]                                                 =
    header(HeaderNames.allow.toString(), TextCodec.string)
      .transform[Allow](Allow.toAllow, Allow.fromAllow)
  final val authorization: HeaderCodec[Authorization]                                 =
    header(HeaderNames.authorization.toString(), TextCodec.string)
      .transform(Authorization.toAuthorization, Authorization.fromAuthorization)
  final val cacheControl: HeaderCodec[CacheControl]                                   =
    header(HeaderNames.cacheControl.toString(), TextCodec.string)
      .transform[CacheControl](CacheControl.toCacheControl, CacheControl.fromCacheControl)
  final val connection: HeaderCodec[Connection]          = header(HeaderNames.connection.toString(), TextCodec.string)
    .transform[Connection](Connection.toConnection, Connection.fromConnection)
  final val contentBase: HeaderCodec[String]             =
    header(HeaderNames.contentBase.toString(), TextCodec.string)
  final val contentEncoding: HeaderCodec[String]         =
    header(HeaderNames.contentEncoding.toString(), TextCodec.string)
  final val contentLanguage: HeaderCodec[String]         =
    header(HeaderNames.contentLanguage.toString(), TextCodec.string)
  final val contentLength: HeaderCodec[ContentLength]    =
    header(HeaderNames.contentLength.toString(), TextCodec.string)
      .transform(ContentLength.toContentLength, ContentLength.fromContentLength)
  final val contentLocation: HeaderCodec[String]         =
    header(HeaderNames.contentLocation.toString(), TextCodec.string)
  final val contentTransferEncoding: HeaderCodec[String] =
    header(HeaderNames.contentTransferEncoding.toString(), TextCodec.string)
  final val contentDisposition: HeaderCodec[String]      =
    header(HeaderNames.contentDisposition.toString(), TextCodec.string)
  final val contentMd5: HeaderCodec[String]              =
    header(HeaderNames.contentMd5.toString(), TextCodec.string)
  final val contentRange: HeaderCodec[String]            =
    header(HeaderNames.contentRange.toString(), TextCodec.string)
  final val contentSecurityPolicy: HeaderCodec[String]   =
    header(HeaderNames.contentSecurityPolicy.toString(), TextCodec.string)
  final val contentType: HeaderCodec[String]             =
    header(HeaderNames.contentType.toString(), TextCodec.string)
  final val date: HeaderCodec[Date]                      = header(HeaderNames.date.toString(), TextCodec.string)
    .transform(Date.toDate, Date.fromDate)
  final val cookie: HeaderCodec[RequestCookie]      = header(HeaderNames.cookie.toString(), TextCodec.string).transform(
    RequestCookie.toCookie,
    RequestCookie.fromCookie,
  )
  final val dnt: HeaderCodec[DNT]                   = header(HeaderNames.dnt.toString(), TextCodec.string)
    .transform(DNT.toDNT(_), DNT.fromDNT(_))
  final val etag: HeaderCodec[ETag]                 = header(HeaderNames.etag.toString(), TextCodec.string)
    .transform(ETag.toETag(_), ETag.fromETag(_))
  final val expect: HeaderCodec[Expect]             =
    header(HeaderNames.expect.toString(), TextCodec.string)
      .transform(Expect.toExpect, Expect.fromExpect)
  final val expires: HeaderCodec[Expires]           =
    header(HeaderNames.expires.toString(), TextCodec.string).transform[Expires](Expires.toExpires, Expires.fromExpires)
  final val from: HeaderCodec[From]                 = header(HeaderNames.from.toString(), TextCodec.string)
    .transform(From.toFrom, From.fromFrom)
  final val host: HeaderCodec[Host]                 = header(HeaderNames.host.toString(), TextCodec.string)
    .transform(Host.toHost(_), Host.fromHost(_))
  final val ifMatch: HeaderCodec[IfMatch]           = header(HeaderNames.ifMatch.toString(), TextCodec.string)
    .transform(IfMatch.toIfMatch, IfMatch.fromIfMatch)
  final val ifModifiedSince: HeaderCodec[String]    =
    header(HeaderNames.ifModifiedSince.toString(), TextCodec.string)
  final val ifNoneMatch: HeaderCodec[String]        =
    header(HeaderNames.ifNoneMatch.toString(), TextCodec.string)
  final val ifRange: HeaderCodec[IfRange]           =
    header(HeaderNames.ifRange.toString(), TextCodec.string)
      .transform(IfRange.toIfRange, IfRange.fromIfRange)
  final val ifUnmodifiedSince: HeaderCodec[String]  =
    header(HeaderNames.ifUnmodifiedSince.toString(), TextCodec.string)
  final val lastModified: HeaderCodec[String]       =
    header(HeaderNames.lastModified.toString(), TextCodec.string)
  final val location: HeaderCodec[Location]         =
    header(HeaderNames.location.toString(), TextCodec.string).transform(Location.toLocation, Location.fromLocation)
  final val maxForwards: HeaderCodec[MaxForwards]   =
    header(HeaderNames.maxForwards.toString(), TextCodec.string)
      .transform(MaxForwards.toMaxForwards(_), MaxForwards.fromMaxForwards(_))
  final val origin: HeaderCodec[Origin]             =
    header(HeaderNames.origin.toString(), TextCodec.string)
      .transform(Origin.toOrigin, Origin.fromOrigin)
  final val pragma: HeaderCodec[Pragma]             = header(HeaderNames.pragma.toString(), TextCodec.string)
    .transform(Pragma.toPragma, Pragma.fromPragma)
  final val proxyAuthenticate: HeaderCodec[String]  =
    header(HeaderNames.proxyAuthenticate.toString(), TextCodec.string)
  final val proxyAuthorization: HeaderCodec[String] =
    header(HeaderNames.proxyAuthorization.toString(), TextCodec.string)
  final val range: HeaderCodec[String]              = header(HeaderNames.range.toString(), TextCodec.string)
  final val referer: HeaderCodec[Referer]           = header(HeaderNames.referer.toString(), TextCodec.string)
    .transform(Referer.toReferer, Referer.fromReferer)
  final val retryAfter: HeaderCodec[String]         =
    header(HeaderNames.retryAfter.toString(), TextCodec.string)
  final val secWebSocketLocation: HeaderCodec[String]       =
    header(HeaderNames.secWebSocketLocation.toString(), TextCodec.string)
  final val secWebSocketOrigin: HeaderCodec[String]         =
    header(HeaderNames.secWebSocketOrigin.toString(), TextCodec.string)
  final val secWebSocketProtocol: HeaderCodec[String]       =
    header(HeaderNames.secWebSocketProtocol.toString(), TextCodec.string)
  final val secWebSocketVersion: HeaderCodec[String]        =
    header(HeaderNames.secWebSocketVersion.toString(), TextCodec.string)
  final val secWebSocketKey: HeaderCodec[String]            =
    header(HeaderNames.secWebSocketKey.toString(), TextCodec.string)
  final val secWebSocketAccept: HeaderCodec[String]         =
    header(HeaderNames.secWebSocketAccept.toString(), TextCodec.string)
  final val secWebSocketExtensions: HeaderCodec[String]     =
    header(HeaderNames.secWebSocketExtensions.toString(), TextCodec.string)
  final val server: HeaderCodec[Server]                     =
    header(HeaderNames.server.toString(), TextCodec.string).transform(Server.toServer, Server.fromServer)
  final val setCookie: HeaderCodec[ResponseCookie]          = header(HeaderNames.setCookie.toString(), TextCodec.string)
    .transform(ResponseCookie.toCookie, ResponseCookie.fromCookie)
  final val te: HeaderCodec[String]                         = header(HeaderNames.te.toString(), TextCodec.string)
  final val trailer: HeaderCodec[Trailer]                   = header(HeaderNames.trailer.toString(), TextCodec.string)
    .transform(Trailer.toTrailer, Trailer.fromTrailer)
  final val transferEncoding: HeaderCodec[TransferEncoding] = header(
    HeaderNames.transferEncoding.toString(),
    TextCodec.string,
  ).transform(TransferEncoding.toTransferEncoding, TransferEncoding.fromTransferEncoding)
  final val upgrade: HeaderCodec[String]                    = header(HeaderNames.upgrade.toString(), TextCodec.string)
  final val upgradeInsecureRequests: HeaderCodec[String]    =
    header(HeaderNames.upgradeInsecureRequests.toString(), TextCodec.string)
  final val userAgent: HeaderCodec[String]                  = header(HeaderNames.userAgent.toString(), TextCodec.string)
  final val vary: HeaderCodec[Vary]                         = header(HeaderNames.vary.toString(), TextCodec.string)
    .transform(Vary.toVary, Vary.fromVary)
  final val via: HeaderCodec[String]                        = header(HeaderNames.via.toString(), TextCodec.string)
  final val warning: HeaderCodec[String]                    = header(HeaderNames.warning.toString(), TextCodec.string)
  final val webSocketLocation: HeaderCodec[String]          =
    header(HeaderNames.webSocketLocation.toString(), TextCodec.string)
  final val webSocketOrigin: HeaderCodec[String]            =
    header(HeaderNames.webSocketOrigin.toString(), TextCodec.string)
  final val webSocketProtocol: HeaderCodec[String]          =
    header(HeaderNames.webSocketProtocol.toString(), TextCodec.string)
  final val wwwAuthenticate: HeaderCodec[String]            =
    header(HeaderNames.wwwAuthenticate.toString(), TextCodec.string)
  final val xFrameOptions: HeaderCodec[String]              =
    header(HeaderNames.xFrameOptions.toString(), TextCodec.string)
  final val xRequestedWith: HeaderCodec[String]             =
    header(HeaderNames.xRequestedWith.toString(), TextCodec.string)
}
