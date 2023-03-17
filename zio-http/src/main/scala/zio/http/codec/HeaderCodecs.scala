/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.codec

import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.model.HeaderNames
import zio.http.model.headers.values._

private[codec] trait HeaderCodecs {
  private[http] def header[A](name: String, value: TextCodec[A]): HeaderCodec[A] =
    HttpCodec.Header(name, value)

  final val accept: HeaderCodec[Accept] =
    header(HeaderNames.accept.toString, TextCodec.string)
      .transformOrFailLeft(Accept.toAccept, Accept.fromAccept)

  final val acceptEncoding: HeaderCodec[AcceptEncoding] =
    header(HeaderNames.acceptEncoding.toString, TextCodec.string)
      .transformOrFailLeft(AcceptEncoding.toAcceptEncoding, AcceptEncoding.fromAcceptEncoding)

  final val acceptLanguage: HeaderCodec[AcceptLanguage] =
    header(HeaderNames.acceptLanguage.toString, TextCodec.string)
      .transformOrFailLeft(AcceptLanguage.toAcceptLanguage, AcceptLanguage.fromAcceptLanguage)

  final val acceptRanges: HeaderCodec[AcceptRanges] =
    header(HeaderNames.acceptRanges.toString, TextCodec.string)
      .transformOrFailLeft(AcceptRanges.to, AcceptRanges.from)

  final val acceptPatch: HeaderCodec[AcceptPatch] =
    header(HeaderNames.acceptPatch.toString, TextCodec.string)
      .transformOrFailLeft(AcceptPatch.toAcceptPatch, AcceptPatch.fromAcceptPatch)

  final val accessControlAllowCredentials: HeaderCodec[AccessControlAllowCredentials] =
    header(HeaderNames.accessControlAllowCredentials.toString, TextCodec.string)
      .transformOrFailLeft(
        AccessControlAllowCredentials.toAccessControlAllowCredentials,
        AccessControlAllowCredentials.fromAccessControlAllowCredentials,
      )

  final val accessControlAllowHeaders: HeaderCodec[AccessControlAllowHeaders] =
    header(HeaderNames.accessControlAllowHeaders.toString, TextCodec.string)
      .transformOrFailLeft(
        AccessControlAllowHeaders.toAccessControlAllowHeaders,
        AccessControlAllowHeaders.fromAccessControlAllowHeaders,
      )

  final val accessControlAllowMethods: HeaderCodec[AccessControlAllowMethods] =
    header(HeaderNames.accessControlAllowMethods.toString, TextCodec.string)
      .transformOrFailLeft(
        AccessControlAllowMethods.toAccessControlAllowMethods,
        AccessControlAllowMethods.fromAccessControlAllowMethods,
      )

  final val accessControlAllowOrigin: HeaderCodec[AccessControlAllowOrigin] =
    header(HeaderNames.accessControlAllowOrigin.toString, TextCodec.string)
      .transformOrFailLeft(
        AccessControlAllowOrigin.toAccessControlAllowOrigin,
        AccessControlAllowOrigin.fromAccessControlAllowOrigin,
      )

  final val accessControlExposeHeaders: HeaderCodec[AccessControlExposeHeaders] =
    header(HeaderNames.accessControlExposeHeaders.toString, TextCodec.string)
      .transformOrFailLeft(
        AccessControlExposeHeaders.toAccessControlExposeHeaders,
        AccessControlExposeHeaders.fromAccessControlExposeHeaders,
      )

  final val accessControlMaxAge: HeaderCodec[AccessControlMaxAge] =
    header(HeaderNames.accessControlMaxAge.toString, TextCodec.string)
      .transformOrFailLeft[AccessControlMaxAge](
        AccessControlMaxAge.toAccessControlMaxAge,
        AccessControlMaxAge.fromAccessControlMaxAge,
      )

  final val accessControlRequestHeaders: HeaderCodec[AccessControlRequestHeaders] =
    header(HeaderNames.accessControlRequestHeaders.toString, TextCodec.string)
      .transformOrFailLeft(
        AccessControlRequestHeaders.toAccessControlRequestHeaders,
        AccessControlRequestHeaders.fromAccessControlRequestHeaders,
      )

  final val accessControlRequestMethod: HeaderCodec[AccessControlRequestMethod] =
    header(HeaderNames.accessControlRequestMethod.toString, TextCodec.string)
      .transformOrFailLeft(
        AccessControlRequestMethod.toAccessControlRequestMethod,
        AccessControlRequestMethod.fromAccessControlRequestMethod,
      )

  final val age: HeaderCodec[Age] =
    header(HeaderNames.age.toString, TextCodec.string).transformOrFailLeft(Age.toAge, Age.fromAge)

  final val allow: HeaderCodec[Allow] =
    header(HeaderNames.allow.toString, TextCodec.string)
      .transformOrFailLeft[Allow](Allow.toAllow, Allow.fromAllow)

  final val authorization: HeaderCodec[Authorization] =
    header(HeaderNames.authorization.toString, TextCodec.string)
      .transformOrFailLeft(Authorization.toAuthorization, Authorization.fromAuthorization)

  final val cacheControl: HeaderCodec[CacheControl] =
    header(HeaderNames.cacheControl.toString, TextCodec.string)
      .transformOrFailLeft[CacheControl](CacheControl.toCacheControl, CacheControl.fromCacheControl)

  final val connection: HeaderCodec[Connection] = header(HeaderNames.connection.toString, TextCodec.string)
    .transformOrFailLeft[Connection](Connection.toConnection, Connection.fromConnection)

  final val contentBase: HeaderCodec[ContentBase] =
    header(HeaderNames.contentBase.toString, TextCodec.string)
      .transformOrFailLeft(ContentBase.toContentBase, ContentBase.fromContentBase)

  final val contentEncoding: HeaderCodec[ContentEncoding] =
    header(HeaderNames.contentEncoding.toString, TextCodec.string)
      .transformOrFailLeft[ContentEncoding](ContentEncoding.toContentEncoding, ContentEncoding.fromContentEncoding)

  final val contentLanguage: HeaderCodec[ContentLanguage] =
    header(HeaderNames.contentLanguage.toString, TextCodec.string)
      .transformOrFailLeft[ContentLanguage](ContentLanguage.toContentLanguage, ContentLanguage.fromContentLanguage)

  final val contentLength: HeaderCodec[ContentLength] =
    header(HeaderNames.contentLength.toString, TextCodec.string)
      .transformOrFailLeft(ContentLength.toContentLength, ContentLength.fromContentLength)

  final val contentLocation: HeaderCodec[ContentLocation] =
    header(HeaderNames.contentLocation.toString, TextCodec.string)
      .transformOrFailLeft(ContentLocation.toContentLocation, ContentLocation.fromContentLocation)

  final val contentTransferEncoding: HeaderCodec[ContentTransferEncoding] =
    header(HeaderNames.contentTransferEncoding.toString, TextCodec.string)
      .transformOrFailLeft[ContentTransferEncoding](
        ContentTransferEncoding.toContentTransferEncoding,
        ContentTransferEncoding.fromContentTransferEncoding,
      )

  final val contentDisposition: HeaderCodec[ContentDisposition] =
    header(HeaderNames.contentDisposition.toString, TextCodec.string)
      .transformOrFailLeft[ContentDisposition](
        ContentDisposition.toContentDisposition,
        ContentDisposition.fromContentDisposition,
      )

  final val contentMd5: HeaderCodec[ContentMd5] =
    header(HeaderNames.contentMd5.toString, TextCodec.string)
      .transformOrFailLeft[ContentMd5](ContentMd5.toContentMd5, ContentMd5.fromContentMd5)

  final val contentRange: HeaderCodec[ContentRange] =
    header(HeaderNames.contentRange.toString, TextCodec.string)
      .transformOrFailLeft[ContentRange](ContentRange.toContentRange, ContentRange.fromContentRange)

  final val contentSecurityPolicy: HeaderCodec[ContentSecurityPolicy] =
    header(HeaderNames.contentSecurityPolicy.toString, TextCodec.string)
      .transformOrFailLeft[ContentSecurityPolicy](
        ContentSecurityPolicy.toContentSecurityPolicy,
        ContentSecurityPolicy.fromContentSecurityPolicy,
      )

  final val contentType: HeaderCodec[ContentType] =
    header(HeaderNames.contentType.toString, TextCodec.string)
      .transformOrFailLeft(ContentType.toContentType, ContentType.fromContentType)

  final val cookie: HeaderCodec[RequestCookie] =
    header(HeaderNames.cookie.toString, TextCodec.string).transformOrFailLeft(
      RequestCookie.toCookie,
      RequestCookie.fromCookie,
    )

  final val date: HeaderCodec[Date] = header(HeaderNames.date.toString, TextCodec.string)
    .transformOrFailLeft(Date.toDate, Date.fromDate)

  final val dnt: HeaderCodec[DNT] = header(HeaderNames.dnt.toString, TextCodec.string)
    .transformOrFailLeft(DNT.toDNT, DNT.fromDNT)

  final val etag: HeaderCodec[ETag] = header(HeaderNames.etag.toString, TextCodec.string)
    .transformOrFailLeft(ETag.toETag, ETag.fromETag)

  final val expect: HeaderCodec[Expect] =
    header(HeaderNames.expect.toString, TextCodec.string)
      .transformOrFailLeft(Expect.toExpect, Expect.fromExpect)

  final val expires: HeaderCodec[Expires] =
    header(HeaderNames.expires.toString, TextCodec.string)
      .transformOrFailLeft[Expires](Expires.toExpires, Expires.fromExpires)

  final val from: HeaderCodec[From] = header(HeaderNames.from.toString, TextCodec.string)
    .transformOrFailLeft(From.toFrom, From.fromFrom)

  final val host: HeaderCodec[Host] = header(HeaderNames.host.toString, TextCodec.string)
    .transformOrFailLeft(Host.toHost, Host.fromHost)

  final val ifMatch: HeaderCodec[IfMatch] = header(HeaderNames.ifMatch.toString, TextCodec.string)
    .transformOrFailLeft(IfMatch.toIfMatch, IfMatch.fromIfMatch)

  final val ifModifiedSince: HeaderCodec[IfModifiedSince] =
    header(HeaderNames.ifModifiedSince.toString, TextCodec.string)
      .transformOrFailLeft[IfModifiedSince](
        IfModifiedSince.toIfModifiedSince,
        IfModifiedSince.fromIfModifiedSince,
      )

  final val ifNoneMatch: HeaderCodec[IfNoneMatch] =
    header(HeaderNames.ifNoneMatch.toString, TextCodec.string).transformOrFailLeft(
      IfNoneMatch.toIfNoneMatch,
      IfNoneMatch.fromIfNoneMatch,
    )

  final val ifRange: HeaderCodec[IfRange] =
    header(HeaderNames.ifRange.toString, TextCodec.string)
      .transformOrFailLeft(IfRange.toIfRange, IfRange.fromIfRange)

  final val ifUnmodifiedSince: HeaderCodec[IfUnmodifiedSince] =
    header(HeaderNames.ifUnmodifiedSince.toString, TextCodec.string)
      .transformOrFailLeft(
        IfUnmodifiedSince.toIfUnmodifiedSince,
        IfUnmodifiedSince.fromIfUnmodifiedSince,
      )

  final val lastModified: HeaderCodec[LastModified] =
    header(HeaderNames.lastModified.toString, TextCodec.string)
      .transformOrFailLeft(LastModified.toLastModified, LastModified.fromLastModified)

  final val location: HeaderCodec[Location] =
    header(HeaderNames.location.toString, TextCodec.string)
      .transformOrFailLeft(Location.toLocation, Location.fromLocation)

  final val maxForwards: HeaderCodec[MaxForwards] =
    header(HeaderNames.maxForwards.toString, TextCodec.string)
      .transformOrFailLeft(MaxForwards.toMaxForwards, MaxForwards.fromMaxForwards)

  final val origin: HeaderCodec[Origin] =
    header(HeaderNames.origin.toString, TextCodec.string)
      .transformOrFailLeft(Origin.toOrigin, Origin.fromOrigin)

  final val pragma: HeaderCodec[Pragma] = header(HeaderNames.pragma.toString, TextCodec.string)
    .transformOrFailLeft(Pragma.toPragma, Pragma.fromPragma)

  final val proxyAuthenticate: HeaderCodec[ProxyAuthenticate] =
    header(HeaderNames.proxyAuthenticate.toString, TextCodec.string)
      .transformOrFailLeft(ProxyAuthenticate.toProxyAuthenticate, ProxyAuthenticate.fromProxyAuthenticate)

  final val proxyAuthorization: HeaderCodec[ProxyAuthorization] =
    header(HeaderNames.proxyAuthorization.toString, TextCodec.string)
      .transformOrFailLeft(ProxyAuthorization.toProxyAuthorization, ProxyAuthorization.fromProxyAuthorization)

  final val range: HeaderCodec[Range] = header(HeaderNames.range.toString, TextCodec.string).transformOrFailLeft(
    Range.toRange,
    Range.fromRange,
  )

  final val referer: HeaderCodec[Referer] = header(HeaderNames.referer.toString, TextCodec.string)
    .transformOrFailLeft(Referer.toReferer, Referer.fromReferer)

  final val retryAfter: HeaderCodec[RetryAfter] =
    header(HeaderNames.retryAfter.toString, TextCodec.string)
      .transformOrFailLeft(RetryAfter.toRetryAfter, RetryAfter.fromRetryAfter)

  final val secWebSocketLocation: HeaderCodec[SecWebSocketLocation] =
    header(HeaderNames.secWebSocketLocation.toString, TextCodec.string)
      .transformOrFailLeft(SecWebSocketLocation.toSecWebSocketLocation, SecWebSocketLocation.fromSecWebSocketLocation)

  final val secWebSocketOrigin: HeaderCodec[SecWebSocketOrigin] =
    header(HeaderNames.secWebSocketOrigin.toString, TextCodec.string)
      .transformOrFailLeft(SecWebSocketOrigin.toSecWebSocketOrigin, SecWebSocketOrigin.fromSecWebSocketOrigin)

  final val secWebSocketProtocol: HeaderCodec[SecWebSocketProtocol] =
    header(HeaderNames.secWebSocketProtocol.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketProtocol.toSecWebSocketProtocol,
      SecWebSocketProtocol.fromSecWebSocketProtocol,
    )

  final val secWebSocketVersion: HeaderCodec[SecWebSocketVersion] =
    header(HeaderNames.secWebSocketVersion.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketVersion.toSecWebSocketVersion,
      SecWebSocketVersion.fromSecWebSocketVersion,
    )

  final val secWebSocketKey: HeaderCodec[SecWebSocketKey] =
    header(HeaderNames.secWebSocketKey.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketKey.toSecWebSocketKey,
      SecWebSocketKey.fromSecWebSocketKey,
    )

  final val secWebSocketAccept: HeaderCodec[SecWebSocketAccept] =
    header(HeaderNames.secWebSocketAccept.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketAccept.toSecWebSocketAccept,
      SecWebSocketAccept.fromSecWebSocketAccept,
    )

  final val secWebSocketExtensions: HeaderCodec[SecWebSocketExtensions] =
    header(HeaderNames.secWebSocketExtensions.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketExtensions.toSecWebSocketExtensions,
      SecWebSocketExtensions.fromSecWebSocketExtensions,
    )

  final val server: HeaderCodec[Server] =
    header(HeaderNames.server.toString, TextCodec.string).transformOrFailLeft(Server.toServer, Server.fromServer)

  final val setCookie: HeaderCodec[ResponseCookie] = header(HeaderNames.setCookie.toString, TextCodec.string)
    .transformOrFailLeft(ResponseCookie.toCookie, ResponseCookie.fromCookie)

  final val te: HeaderCodec[Te] = header(HeaderNames.te.toString, TextCodec.string).transformOrFailLeft(
    Te.toTe,
    Te.fromTe,
  )

  final val trailer: HeaderCodec[Trailer] = header(HeaderNames.trailer.toString, TextCodec.string)
    .transformOrFailLeft(Trailer.toTrailer, Trailer.fromTrailer)

  final val transferEncoding: HeaderCodec[TransferEncoding] = header(
    HeaderNames.transferEncoding.toString,
    TextCodec.string,
  ).transformOrFailLeft(TransferEncoding.toTransferEncoding, TransferEncoding.fromTransferEncoding)

  final val upgrade: HeaderCodec[Upgrade] = header(HeaderNames.upgrade.toString, TextCodec.string)
    .transformOrFailLeft(Upgrade.toUpgrade, Upgrade.fromUpgrade)

  final val upgradeInsecureRequests: HeaderCodec[UpgradeInsecureRequests] =
    header(HeaderNames.upgradeInsecureRequests.toString, TextCodec.string)
      .transformOrFailLeft(
        UpgradeInsecureRequests.toUpgradeInsecureRequests,
        UpgradeInsecureRequests.fromUpgradeInsecureRequests,
      )

  final val userAgent: HeaderCodec[UserAgent] =
    header(HeaderNames.userAgent.toString, TextCodec.string)
      .transformOrFailLeft(UserAgent.toUserAgent, UserAgent.fromUserAgent)

  final val vary: HeaderCodec[Vary] = header(HeaderNames.vary.toString, TextCodec.string)
    .transformOrFailLeft(Vary.toVary, Vary.fromVary)

  final val via: HeaderCodec[Via] = header(HeaderNames.via.toString, TextCodec.string).transformOrFailLeft(
    Via.toVia,
    Via.fromVia,
  )

  final val warning: HeaderCodec[Warning] =
    header(HeaderNames.warning.toString, TextCodec.string)
      .transformOrFailLeft[Warning](Warning.toWarning, Warning.fromWarning)

  final val webSocketLocation: HeaderCodec[SecWebSocketLocation] =
    header(HeaderNames.webSocketLocation.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketLocation.toSecWebSocketLocation,
      SecWebSocketLocation.fromSecWebSocketLocation,
    )

  final val webSocketOrigin: HeaderCodec[SecWebSocketOrigin] =
    header(HeaderNames.webSocketOrigin.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketOrigin.toSecWebSocketOrigin,
      SecWebSocketOrigin.fromSecWebSocketOrigin,
    )

  final val webSocketProtocol: HeaderCodec[SecWebSocketProtocol] =
    header(HeaderNames.webSocketProtocol.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketProtocol.toSecWebSocketProtocol,
      SecWebSocketProtocol.fromSecWebSocketProtocol,
    )

  final val wwwAuthenticate: HeaderCodec[WWWAuthenticate] =
    header(HeaderNames.wwwAuthenticate.toString, TextCodec.string).transformOrFailLeft(
      WWWAuthenticate.toWWWAuthenticate,
      WWWAuthenticate.fromWWWAuthenticate,
    )

  final val xFrameOptions: HeaderCodec[XFrameOptions] =
    header(HeaderNames.xFrameOptions.toString, TextCodec.string).transformOrFailLeft(
      XFrameOptions.toXFrameOptions,
      XFrameOptions.fromXFrameOptions,
    )

  final val xRequestedWith: HeaderCodec[XRequestedWith] =
    header(HeaderNames.xRequestedWith.toString, TextCodec.string).transformOrFailLeft(
      XRequestedWith.toXRequestedWith,
      XRequestedWith.fromXRequestedWith,
    )
}
