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
      .transformOrFailLeft(Accept.parse, Accept.render)

  final val acceptEncoding: HeaderCodec[AcceptEncoding] =
    header(HeaderNames.acceptEncoding.toString, TextCodec.string)
      .transformOrFailLeft(AcceptEncoding.parse, AcceptEncoding.render)

  final val acceptLanguage: HeaderCodec[AcceptLanguage] =
    header(HeaderNames.acceptLanguage.toString, TextCodec.string)
      .transformOrFailLeft(AcceptLanguage.parse, AcceptLanguage.render)

  final val acceptRanges: HeaderCodec[AcceptRanges] =
    header(HeaderNames.acceptRanges.toString, TextCodec.string)
      .transformOrFailLeft(AcceptRanges.parse, AcceptRanges.render)

  final val acceptPatch: HeaderCodec[AcceptPatch] =
    header(HeaderNames.acceptPatch.toString, TextCodec.string)
      .transformOrFailLeft(AcceptPatch.parse, AcceptPatch.render)

  final val accessControlAllowCredentials: HeaderCodec[AccessControlAllowCredentials] =
    header(HeaderNames.accessControlAllowCredentials.toString, TextCodec.string)
      .transformOrFailLeft(
        AccessControlAllowCredentials.parse,
        AccessControlAllowCredentials.render,
      )

  final val accessControlAllowHeaders: HeaderCodec[AccessControlAllowHeaders] =
    header(HeaderNames.accessControlAllowHeaders.toString, TextCodec.string)
      .transformOrFailLeft(
        AccessControlAllowHeaders.parse,
        AccessControlAllowHeaders.render,
      )

  final val accessControlAllowMethods: HeaderCodec[AccessControlAllowMethods] =
    header(HeaderNames.accessControlAllowMethods.toString, TextCodec.string)
      .transformOrFailLeft(
        AccessControlAllowMethods.parse,
        AccessControlAllowMethods.render,
      )

  final val accessControlAllowOrigin: HeaderCodec[AccessControlAllowOrigin] =
    header(HeaderNames.accessControlAllowOrigin.toString, TextCodec.string)
      .transformOrFailLeft(
        AccessControlAllowOrigin.parse,
        AccessControlAllowOrigin.render,
      )

  final val accessControlExposeHeaders: HeaderCodec[AccessControlExposeHeaders] =
    header(HeaderNames.accessControlExposeHeaders.toString, TextCodec.string)
      .transformOrFailLeft(
        AccessControlExposeHeaders.parse,
        AccessControlExposeHeaders.render,
      )

  final val accessControlMaxAge: HeaderCodec[AccessControlMaxAge] =
    header(HeaderNames.accessControlMaxAge.toString, TextCodec.string)
      .transformOrFailLeft[AccessControlMaxAge](
        AccessControlMaxAge.parse,
        AccessControlMaxAge.render,
      )

  final val accessControlRequestHeaders: HeaderCodec[AccessControlRequestHeaders] =
    header(HeaderNames.accessControlRequestHeaders.toString, TextCodec.string)
      .transformOrFailLeft(
        AccessControlRequestHeaders.parse,
        AccessControlRequestHeaders.render,
      )

  final val accessControlRequestMethod: HeaderCodec[AccessControlRequestMethod] =
    header(HeaderNames.accessControlRequestMethod.toString, TextCodec.string)
      .transformOrFailLeft(
        AccessControlRequestMethod.parse,
        AccessControlRequestMethod.render,
      )

  final val age: HeaderCodec[Age] =
    header(HeaderNames.age.toString, TextCodec.string).transformOrFailLeft(Age.parse, Age.render)

  final val allow: HeaderCodec[Allow] =
    header(HeaderNames.allow.toString, TextCodec.string)
      .transformOrFailLeft[Allow](Allow.parse, Allow.render)

  final val authorization: HeaderCodec[Authorization] =
    header(HeaderNames.authorization.toString, TextCodec.string)
      .transformOrFailLeft(Authorization.parse, Authorization.render)

  final val cacheControl: HeaderCodec[CacheControl] =
    header(HeaderNames.cacheControl.toString, TextCodec.string)
      .transformOrFailLeft[CacheControl](CacheControl.parse, CacheControl.render)

  final val connection: HeaderCodec[Connection] = header(HeaderNames.connection.toString, TextCodec.string)
    .transformOrFailLeft[Connection](Connection.parse, Connection.render)

  final val contentBase: HeaderCodec[ContentBase] =
    header(HeaderNames.contentBase.toString, TextCodec.string)
      .transformOrFailLeft(ContentBase.parse, ContentBase.render)

  final val contentEncoding: HeaderCodec[ContentEncoding] =
    header(HeaderNames.contentEncoding.toString, TextCodec.string)
      .transformOrFailLeft[ContentEncoding](ContentEncoding.parse, ContentEncoding.render)

  final val contentLanguage: HeaderCodec[ContentLanguage] =
    header(HeaderNames.contentLanguage.toString, TextCodec.string)
      .transformOrFailLeft[ContentLanguage](ContentLanguage.parse, ContentLanguage.render)

  final val contentLength: HeaderCodec[ContentLength] =
    header(HeaderNames.contentLength.toString, TextCodec.string)
      .transformOrFailLeft(ContentLength.parse, ContentLength.render)

  final val contentLocation: HeaderCodec[ContentLocation] =
    header(HeaderNames.contentLocation.toString, TextCodec.string)
      .transformOrFailLeft(ContentLocation.parse, ContentLocation.render)

  final val contentTransferEncoding: HeaderCodec[ContentTransferEncoding] =
    header(HeaderNames.contentTransferEncoding.toString, TextCodec.string)
      .transformOrFailLeft[ContentTransferEncoding](
        ContentTransferEncoding.parse,
        ContentTransferEncoding.render,
      )

  final val contentDisposition: HeaderCodec[ContentDisposition] =
    header(HeaderNames.contentDisposition.toString, TextCodec.string)
      .transformOrFailLeft[ContentDisposition](
        ContentDisposition.parse,
        ContentDisposition.render,
      )

  final val contentMd5: HeaderCodec[ContentMd5] =
    header(HeaderNames.contentMd5.toString, TextCodec.string)
      .transformOrFailLeft[ContentMd5](ContentMd5.parse, ContentMd5.render)

  final val contentRange: HeaderCodec[ContentRange] =
    header(HeaderNames.contentRange.toString, TextCodec.string)
      .transformOrFailLeft[ContentRange](ContentRange.parse, ContentRange.render)

  final val contentSecurityPolicy: HeaderCodec[ContentSecurityPolicy] =
    header(HeaderNames.contentSecurityPolicy.toString, TextCodec.string)
      .transformOrFailLeft[ContentSecurityPolicy](
        ContentSecurityPolicy.parse,
        ContentSecurityPolicy.render,
      )

  final val contentType: HeaderCodec[ContentType] =
    header(HeaderNames.contentType.toString, TextCodec.string)
      .transformOrFailLeft(ContentType.parse, ContentType.render)

  final val cookie: HeaderCodec[RequestCookie] =
    header(HeaderNames.cookie.toString, TextCodec.string).transformOrFailLeft(
      RequestCookie.parse,
      RequestCookie.render,
    )

  final val date: HeaderCodec[Date] = header(HeaderNames.date.toString, TextCodec.string)
    .transformOrFailLeft(Date.parse, Date.render)

  final val dnt: HeaderCodec[DNT] = header(HeaderNames.dnt.toString, TextCodec.string)
    .transformOrFailLeft(DNT.parse, DNT.render)

  final val etag: HeaderCodec[ETag] = header(HeaderNames.etag.toString, TextCodec.string)
    .transformOrFailLeft(ETag.parse, ETag.render)

  final val expect: HeaderCodec[Expect] =
    header(HeaderNames.expect.toString, TextCodec.string)
      .transformOrFailLeft(Expect.parse, Expect.render)

  final val expires: HeaderCodec[Expires] =
    header(HeaderNames.expires.toString, TextCodec.string)
      .transformOrFailLeft[Expires](Expires.parse, Expires.render)

  final val from: HeaderCodec[From] = header(HeaderNames.from.toString, TextCodec.string)
    .transformOrFailLeft(From.parse, From.render)

  final val host: HeaderCodec[Host] = header(HeaderNames.host.toString, TextCodec.string)
    .transformOrFailLeft(Host.parse, Host.render)

  final val ifMatch: HeaderCodec[IfMatch] = header(HeaderNames.ifMatch.toString, TextCodec.string)
    .transformOrFailLeft(IfMatch.parse, IfMatch.render)

  final val ifModifiedSince: HeaderCodec[IfModifiedSince] =
    header(HeaderNames.ifModifiedSince.toString, TextCodec.string)
      .transformOrFailLeft[IfModifiedSince](
        IfModifiedSince.parse,
        IfModifiedSince.render,
      )

  final val ifNoneMatch: HeaderCodec[IfNoneMatch] =
    header(HeaderNames.ifNoneMatch.toString, TextCodec.string).transformOrFailLeft(
      IfNoneMatch.parse,
      IfNoneMatch.render,
    )

  final val ifRange: HeaderCodec[IfRange] =
    header(HeaderNames.ifRange.toString, TextCodec.string)
      .transformOrFailLeft(IfRange.parse, IfRange.render)

  final val ifUnmodifiedSince: HeaderCodec[IfUnmodifiedSince] =
    header(HeaderNames.ifUnmodifiedSince.toString, TextCodec.string)
      .transformOrFailLeft(
        IfUnmodifiedSince.parse,
        IfUnmodifiedSince.render,
      )

  final val lastModified: HeaderCodec[LastModified] =
    header(HeaderNames.lastModified.toString, TextCodec.string)
      .transformOrFailLeft(LastModified.parse, LastModified.render)

  final val location: HeaderCodec[Location] =
    header(HeaderNames.location.toString, TextCodec.string)
      .transformOrFailLeft(Location.parse, Location.render)

  final val maxForwards: HeaderCodec[MaxForwards] =
    header(HeaderNames.maxForwards.toString, TextCodec.string)
      .transformOrFailLeft(MaxForwards.parse, MaxForwards.render)

  final val origin: HeaderCodec[Origin] =
    header(HeaderNames.origin.toString, TextCodec.string)
      .transformOrFailLeft(Origin.parse, Origin.render)

  final val pragma: HeaderCodec[Pragma] = header(HeaderNames.pragma.toString, TextCodec.string)
    .transformOrFailLeft(Pragma.parse, Pragma.render)

  final val proxyAuthenticate: HeaderCodec[ProxyAuthenticate] =
    header(HeaderNames.proxyAuthenticate.toString, TextCodec.string)
      .transformOrFailLeft(ProxyAuthenticate.parse, ProxyAuthenticate.render)

  final val proxyAuthorization: HeaderCodec[ProxyAuthorization] =
    header(HeaderNames.proxyAuthorization.toString, TextCodec.string)
      .transformOrFailLeft(ProxyAuthorization.parse, ProxyAuthorization.render)

  final val range: HeaderCodec[Range] = header(HeaderNames.range.toString, TextCodec.string).transformOrFailLeft(
    Range.parse,
    Range.render,
  )

  final val referer: HeaderCodec[Referer] = header(HeaderNames.referer.toString, TextCodec.string)
    .transformOrFailLeft(Referer.parse, Referer.render)

  final val retryAfter: HeaderCodec[RetryAfter] =
    header(HeaderNames.retryAfter.toString, TextCodec.string)
      .transformOrFailLeft(RetryAfter.parse, RetryAfter.render)

  final val secWebSocketLocation: HeaderCodec[SecWebSocketLocation] =
    header(HeaderNames.secWebSocketLocation.toString, TextCodec.string)
      .transformOrFailLeft(SecWebSocketLocation.parse, SecWebSocketLocation.render)

  final val secWebSocketOrigin: HeaderCodec[SecWebSocketOrigin] =
    header(HeaderNames.secWebSocketOrigin.toString, TextCodec.string)
      .transformOrFailLeft(SecWebSocketOrigin.parse, SecWebSocketOrigin.render)

  final val secWebSocketProtocol: HeaderCodec[SecWebSocketProtocol] =
    header(HeaderNames.secWebSocketProtocol.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketProtocol.parse,
      SecWebSocketProtocol.render,
    )

  final val secWebSocketVersion: HeaderCodec[SecWebSocketVersion] =
    header(HeaderNames.secWebSocketVersion.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketVersion.parse,
      SecWebSocketVersion.render,
    )

  final val secWebSocketKey: HeaderCodec[SecWebSocketKey] =
    header(HeaderNames.secWebSocketKey.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketKey.parse,
      SecWebSocketKey.render,
    )

  final val secWebSocketAccept: HeaderCodec[SecWebSocketAccept] =
    header(HeaderNames.secWebSocketAccept.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketAccept.parse,
      SecWebSocketAccept.render,
    )

  final val secWebSocketExtensions: HeaderCodec[SecWebSocketExtensions] =
    header(HeaderNames.secWebSocketExtensions.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketExtensions.parse,
      SecWebSocketExtensions.render,
    )

  final val server: HeaderCodec[Server] =
    header(HeaderNames.server.toString, TextCodec.string).transformOrFailLeft(Server.parse, Server.render)

  final val setCookie: HeaderCodec[ResponseCookie] = header(HeaderNames.setCookie.toString, TextCodec.string)
    .transformOrFailLeft(ResponseCookie.parse, ResponseCookie.render)

  final val te: HeaderCodec[Te] = header(HeaderNames.te.toString, TextCodec.string).transformOrFailLeft(
    Te.parse,
    Te.render,
  )

  final val trailer: HeaderCodec[Trailer] = header(HeaderNames.trailer.toString, TextCodec.string)
    .transformOrFailLeft(Trailer.parse, Trailer.render)

  final val transferEncoding: HeaderCodec[TransferEncoding] = header(
    HeaderNames.transferEncoding.toString,
    TextCodec.string,
  ).transformOrFailLeft(TransferEncoding.parse, TransferEncoding.render)

  final val upgrade: HeaderCodec[Upgrade] = header(HeaderNames.upgrade.toString, TextCodec.string)
    .transformOrFailLeft(Upgrade.parse, Upgrade.render)

  final val upgradeInsecureRequests: HeaderCodec[UpgradeInsecureRequests] =
    header(HeaderNames.upgradeInsecureRequests.toString, TextCodec.string)
      .transformOrFailLeft(
        UpgradeInsecureRequests.parse,
        UpgradeInsecureRequests.render,
      )

  final val userAgent: HeaderCodec[UserAgent] =
    header(HeaderNames.userAgent.toString, TextCodec.string)
      .transformOrFailLeft(UserAgent.parse, UserAgent.render)

  final val vary: HeaderCodec[Vary] = header(HeaderNames.vary.toString, TextCodec.string)
    .transformOrFailLeft(Vary.parse, Vary.render)

  final val via: HeaderCodec[Via] = header(HeaderNames.via.toString, TextCodec.string).transformOrFailLeft(
    Via.parse,
    Via.render,
  )

  final val warning: HeaderCodec[Warning] =
    header(HeaderNames.warning.toString, TextCodec.string)
      .transformOrFailLeft[Warning](Warning.parse, Warning.render)

  final val webSocketLocation: HeaderCodec[SecWebSocketLocation] =
    header(HeaderNames.webSocketLocation.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketLocation.parse,
      SecWebSocketLocation.render,
    )

  final val webSocketOrigin: HeaderCodec[SecWebSocketOrigin] =
    header(HeaderNames.webSocketOrigin.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketOrigin.parse,
      SecWebSocketOrigin.render,
    )

  final val webSocketProtocol: HeaderCodec[SecWebSocketProtocol] =
    header(HeaderNames.webSocketProtocol.toString, TextCodec.string).transformOrFailLeft(
      SecWebSocketProtocol.parse,
      SecWebSocketProtocol.render,
    )

  final val wwwAuthenticate: HeaderCodec[WWWAuthenticate] =
    header(HeaderNames.wwwAuthenticate.toString, TextCodec.string).transformOrFailLeft(
      WWWAuthenticate.parse,
      WWWAuthenticate.render,
    )

  final val xFrameOptions: HeaderCodec[XFrameOptions] =
    header(HeaderNames.xFrameOptions.toString, TextCodec.string).transformOrFailLeft(
      XFrameOptions.parse,
      XFrameOptions.render,
    )

  final val xRequestedWith: HeaderCodec[XRequestedWith] =
    header(HeaderNames.xRequestedWith.toString, TextCodec.string).transformOrFailLeft(
      XRequestedWith.parse,
      XRequestedWith.render,
    )
}
