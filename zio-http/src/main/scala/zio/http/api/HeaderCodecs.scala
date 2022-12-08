package zio.http.api

import zio.Chunk
import zio.http.api.internal.RichTextCodec.comma
import zio.http.model.HeaderNames
import zio.http.model.headers.values._
import zio.http.api.internal.{HeaderValueCodecs, RichTextCodec, TextCodec}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait HeaderCodecs {
  private[api] def header[A](name: String, value: Either[TextCodec[A], RichTextCodec[A]]): HeaderCodec[A] =
    HttpCodec.Header(name, value, optional = false)

  val mediaTypeCodec: RichTextCodec[Chunk[Char]] =
    RichTextCodec.filter(c => c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '/' || c == '*').repeat

  val mediaTypeCodecWithQualifier =
    mediaTypeCodec ~ RichTextCodec.semicolon ~ RichTextCodec
      .literal("q=")
      .unit("") ~ RichTextCodec.double

  val mediaTypeCodecAlternative = mediaTypeCodecWithQualifier | mediaTypeCodec

  val temp = mediaTypeCodecAlternative.repeat

  val acceptCodec: RichTextCodec[Chunk[(String, Option[Double])]] =
    RichTextCodec
      .commaSeparatedMultiValues(
        mediaTypeCodec | mediaTypeCodecWithQualifier,
      )
      .transform(
        _.map {
          case Left(a)          => (a.mkString, None)
          case Right((a, _, b)) => (a.mkString, Some(b))
        },
        _.map {
          case (mediaType, Some(params)) =>
            Right((Chunk.from(mediaType.toCharArray), ' ', params))
          case (mediaType, None)         =>
            Left(Chunk.from(mediaType.toCharArray))
        },
      )

  final val accept: HeaderCodec[Accept] =
    header(HeaderNames.accept.toString(), Right(acceptCodec))
      .transform(Accept.toAccept, Accept.fromAccept)

  final val acceptEncoding: HeaderCodec[AcceptEncoding] =
    header(HeaderNames.acceptEncoding.toString(), Right(HeaderValueCodecs.acceptEncodingCodec))
      .transform(AcceptEncoding.toAcceptEncoding, AcceptEncoding.fromAcceptEncoding)

  final val acceptLanguage: HeaderCodec[AcceptLanguage] =
    header(HeaderNames.acceptLanguage.toString(), Right(HeaderValueCodecs.acceptLanguageCodec))
  // .transform(AcceptLanguage.toAcceptLanguage, AcceptLanguage.fromAcceptLanguage)

  final val acceptRanges: HeaderCodec[AcceptRanges] =
    header(HeaderNames.acceptRanges.toString(), Left(TextCodec.string))
      .transform(AcceptRanges.to, AcceptRanges.from)

  final val acceptPatch: HeaderCodec[AcceptPatch] =
    header(HeaderNames.acceptPatch.toString(), Left(TextCodec.string))
      .transform(AcceptPatch.toAcceptPatch, AcceptPatch.fromAcceptPatch)

  final val accessControlAllowCredentials: HeaderCodec[AccessControlAllowCredentials] =
    header(HeaderNames.accessControlAllowCredentials.toString, Left(TextCodec.string))
      .transform(
        AccessControlAllowCredentials.toAccessControlAllowCredentials,
        AccessControlAllowCredentials.fromAccessControlAllowCredentials,
      )

  final val accessControlAllowHeaders: HeaderCodec[AccessControlAllowHeaders] =
    header(HeaderNames.accessControlAllowHeaders.toString, Left(TextCodec.string))
      .transform(
        AccessControlAllowHeaders.toAccessControlAllowHeaders,
        AccessControlAllowHeaders.fromAccessControlAllowHeaders,
      )

  final val accessControlAllowMethods: HeaderCodec[AccessControlAllowMethods] =
    header(HeaderNames.accessControlAllowMethods.toString, Left(TextCodec.string))
      .transform(
        AccessControlAllowMethods.toAccessControlAllowMethods,
        AccessControlAllowMethods.fromAccessControlAllowMethods,
      )

  final val accessControlAllowOrigin: HeaderCodec[AccessControlAllowOrigin] =
    header(HeaderNames.accessControlAllowOrigin.toString, Left(TextCodec.string))
      .transform(
        AccessControlAllowOrigin.toAccessControlAllowOrigin,
        AccessControlAllowOrigin.fromAccessControlAllowOrigin,
      )

  final val accessControlExposeHeaders: HeaderCodec[AccessControlExposeHeaders] =
    header(HeaderNames.accessControlExposeHeaders.toString(), Left(TextCodec.string))
      .transform(
        AccessControlExposeHeaders.toAccessControlExposeHeaders,
        AccessControlExposeHeaders.fromAccessControlExposeHeaders,
      )

  final val accessControlMaxAge: HeaderCodec[AccessControlMaxAge] =
    header(HeaderNames.accessControlMaxAge.toString, Left(TextCodec.string))
      .transform[AccessControlMaxAge](
        AccessControlMaxAge.toAccessControlMaxAge,
        AccessControlMaxAge.fromAccessControlMaxAge,
      )

  final val accessControlRequestHeaders: HeaderCodec[AccessControlRequestHeaders] =
    header(HeaderNames.accessControlRequestHeaders.toString(), Left(TextCodec.string))
      .transform(
        AccessControlRequestHeaders.toAccessControlRequestHeaders,
        AccessControlRequestHeaders.fromAccessControlRequestHeaders,
      )

  final val accessControlRequestMethod: HeaderCodec[AccessControlRequestMethod] =
    header(HeaderNames.accessControlRequestMethod.toString(), Left(TextCodec.string))
      .transform(
        AccessControlRequestMethod.toAccessControlRequestMethod,
        AccessControlRequestMethod.fromAccessControlRequestMethod,
      )

  final val age: HeaderCodec[Age] =
    header(HeaderNames.age.toString(), Right(RichTextCodec.digit)).transform(Age.toAge, Age.fromAge)

  final val allow: HeaderCodec[Allow] =
    header(HeaderNames.allow.toString(), Left(TextCodec.string))
      .transform[Allow](Allow.toAllow, Allow.fromAllow)

  final val authorization: HeaderCodec[Authorization] =
    header(HeaderNames.authorization.toString(), Left(TextCodec.string))
      .transform(Authorization.toAuthorization, Authorization.fromAuthorization)

  final val cacheControl: HeaderCodec[CacheControl] =
    header(HeaderNames.cacheControl.toString(), Left(TextCodec.string))
      .transform[CacheControl](CacheControl.toCacheControl, CacheControl.fromCacheControl)

  final val connection: HeaderCodec[Connection] = header(HeaderNames.connection.toString(), Left(TextCodec.string))
    .transform[Connection](Connection.toConnection, Connection.fromConnection)

  final val contentBase: HeaderCodec[ContentBase] =
    header(HeaderNames.contentBase.toString, Left(TextCodec.string))
      .transform(ContentBase.toContentBase, ContentBase.fromContentBase)

  final val contentEncoding: HeaderCodec[ContentEncoding] =
    header(HeaderNames.contentEncoding.toString, Left(TextCodec.string))
      .transform[ContentEncoding](ContentEncoding.toContentEncoding, ContentEncoding.fromContentEncoding)

  final val contentLanguage: HeaderCodec[ContentLanguage] =
    header(HeaderNames.contentLanguage.toString, Left(TextCodec.string))
      .transform[ContentLanguage](ContentLanguage.toContentLanguage, ContentLanguage.fromContentLanguage)

  final val contentLength: HeaderCodec[ContentLength] =
    header(HeaderNames.contentLength.toString, Left(TextCodec.string))
      .transform(ContentLength.toContentLength, ContentLength.fromContentLength)

  final val contentLocation: HeaderCodec[ContentLocation] =
    header(HeaderNames.contentLocation.toString, Left(TextCodec.string))
      .transform(ContentLocation.toContentLocation, ContentLocation.fromContentLocation)

  final val contentTransferEncoding: HeaderCodec[ContentTransferEncoding] =
    header(HeaderNames.contentTransferEncoding.toString, Left(TextCodec.string))
      .transform[ContentTransferEncoding](
        ContentTransferEncoding.toContentTransferEncoding,
        ContentTransferEncoding.fromContentTransferEncoding,
      )

  final val contentDisposition: HeaderCodec[ContentDisposition] =
    header(HeaderNames.contentDisposition.toString, Left(TextCodec.string))
      .transform[ContentDisposition](
        ContentDisposition.toContentDisposition,
        ContentDisposition.fromContentDisposition,
      )

  final val contentMd5: HeaderCodec[ContentMd5] =
    header(HeaderNames.contentMd5.toString, Left(TextCodec.string))
      .transform[ContentMd5](ContentMd5.toContentMd5, ContentMd5.fromContentMd5)

  final val contentRange: HeaderCodec[ContentRange] =
    header(HeaderNames.contentRange.toString, Left(TextCodec.string))
      .transform[ContentRange](ContentRange.toContentRange, ContentRange.fromContentRange)

  final val contentSecurityPolicy: HeaderCodec[ContentSecurityPolicy] =
    header(HeaderNames.contentSecurityPolicy.toString, Left(TextCodec.string))
      .transform[ContentSecurityPolicy](
        ContentSecurityPolicy.toContentSecurityPolicy,
        ContentSecurityPolicy.fromContentSecurityPolicy,
      )

  final val contentType: HeaderCodec[ContentType] =
    header(HeaderNames.contentType.toString, Left(TextCodec.string))
      .transform(ContentType.toContentType, ContentType.fromContentType)

  final val cookie: HeaderCodec[RequestCookie] =
    header(HeaderNames.cookie.toString(), Left(TextCodec.string)).transform(
      RequestCookie.toCookie,
      RequestCookie.fromCookie,
    )

  final val date: HeaderCodec[Date] = header(HeaderNames.date.toString(), Left(TextCodec.string))
    .transform(Date.toDate, Date.fromDate)

  final val dntCodec = RichTextCodec.digit | RichTextCodec.literal("null")

  final val dnt: HeaderCodec[DNT] =
    header(HeaderNames.dnt.toString(), Right(dntCodec))
      .transform(DNT.toDNT, DNT.fromDNT)

  final val etag: HeaderCodec[ETag] = header(HeaderNames.etag.toString, Left(TextCodec.string))
    .transform(ETag.toETag, ETag.fromETag)

  final val expectCodec: RichTextCodec[String] = RichTextCodec.literalCI("100-continue")
  final val expect: HeaderCodec[Expect]        =
    header(HeaderNames.expect.toString, Right(expectCodec))
      .transform(Expect.toExpect, Expect.fromExpect)

  final val expires: HeaderCodec[Expires] =
    header(HeaderNames.expires.toString, Left(TextCodec.string))
      .transform[Expires](Expires.toExpires, Expires.fromExpires)

  final val from: HeaderCodec[From] = header(HeaderNames.from.toString, Left(TextCodec.string))
    .transform(From.toFrom, From.fromFrom)

  final val host: HeaderCodec[Host] = header(HeaderNames.host.toString, Left(TextCodec.string))
    .transform(Host.toHost, Host.fromHost)

  final val ifMatch: HeaderCodec[IfMatch] = header(HeaderNames.ifMatch.toString, Left(TextCodec.string))
    .transform(IfMatch.toIfMatch, IfMatch.fromIfMatch)

  final val ifModifiedSince: HeaderCodec[IfModifiedSince] =
    header(HeaderNames.ifModifiedSince.toString, Left(TextCodec.string))
      .transform[IfModifiedSince](
        IfModifiedSince.toIfModifiedSince,
        IfModifiedSince.fromIfModifiedSince,
      )

  final val ifNoneMatch: HeaderCodec[IfNoneMatch] =
    header(HeaderNames.ifNoneMatch.toString(), Left(TextCodec.string)).transform(
      IfNoneMatch.toIfNoneMatch,
      IfNoneMatch.fromIfNoneMatch,
    )

  final val ifRange: HeaderCodec[IfRange] =
    header(HeaderNames.ifRange.toString, Left(TextCodec.string))
      .transform(IfRange.toIfRange, IfRange.fromIfRange)

  final val ifUnmodifiedSince: HeaderCodec[IfUnmodifiedSince] =
    header(HeaderNames.ifUnmodifiedSince.toString(), Left(TextCodec.string))
      .transform(
        IfUnmodifiedSince.toIfUnmodifiedSince,
        IfUnmodifiedSince.fromIfUnmodifiedSince,
      )

  final val lastModified: HeaderCodec[LastModified] =
    header(HeaderNames.lastModified.toString(), Left(TextCodec.string))
      .transform(LastModified.toLastModified, LastModified.fromLastModified)

  final val location: HeaderCodec[Location] =
    header(HeaderNames.location.toString(), Left(TextCodec.string))
      .transform(Location.toLocation, Location.fromLocation)

  final val maxForwards: HeaderCodec[MaxForwards] =
    header(HeaderNames.maxForwards.toString(), Left(TextCodec.string))
      .transform(MaxForwards.toMaxForwards(_), MaxForwards.fromMaxForwards(_))

  final val origin: HeaderCodec[Origin] =
    header(HeaderNames.origin.toString(), Left(TextCodec.string))
      .transform(Origin.toOrigin, Origin.fromOrigin)

  final val pragma: HeaderCodec[Pragma] = header(HeaderNames.pragma.toString(), Left(TextCodec.string))
    .transform(Pragma.toPragma, Pragma.fromPragma)

  final val proxyAuthenticate: HeaderCodec[ProxyAuthenticate] =
    header(HeaderNames.proxyAuthenticate.toString(), Left(TextCodec.string))
      .transform(ProxyAuthenticate.toProxyAuthenticate, ProxyAuthenticate.fromProxyAuthenticate)

  final val proxyAuthorization: HeaderCodec[ProxyAuthorization] =
    header(HeaderNames.proxyAuthorization.toString(), Left(TextCodec.string))
      .transform(ProxyAuthorization.toProxyAuthorization, ProxyAuthorization.fromProxyAuthorization)

  final val range: HeaderCodec[Range] = header(HeaderNames.range.toString(), Left(TextCodec.string)).transform(
    Range.toRange,
    Range.fromRange,
  )

  final val referer: HeaderCodec[Referer] = header(HeaderNames.referer.toString(), Left(TextCodec.string))
    .transform(Referer.toReferer, Referer.fromReferer)

  final val retryAfter: HeaderCodec[RetryAfter] =
    header(HeaderNames.retryAfter.toString(), Left(TextCodec.string))
      .transform(RetryAfter.toRetryAfter, RetryAfter.fromRetryAfter)

  final val secWebSocketLocation: HeaderCodec[SecWebSocketLocation] =
    header(HeaderNames.secWebSocketLocation.toString(), Left(TextCodec.string))
      .transform(SecWebSocketLocation.toSecWebSocketLocation, SecWebSocketLocation.fromSecWebSocketLocation)

  final val secWebSocketOrigin: HeaderCodec[SecWebSocketOrigin] =
    header(HeaderNames.secWebSocketOrigin.toString(), Left(TextCodec.string))
      .transform(SecWebSocketOrigin.toSecWebSocketOrigin, SecWebSocketOrigin.fromSecWebSocketOrigin)

  final val secWebSocketProtocol: HeaderCodec[SecWebSocketProtocol] =
    header(HeaderNames.secWebSocketProtocol.toString(), Left(TextCodec.string)).transform(
      SecWebSocketProtocol.toSecWebSocketProtocol,
      SecWebSocketProtocol.fromSecWebSocketProtocol,
    )

  final val secWebSocketVersion: HeaderCodec[SecWebSocketVersion] =
    header(HeaderNames.secWebSocketVersion.toString(), Left(TextCodec.string)).transform(
      SecWebSocketVersion.toSecWebSocketVersion,
      SecWebSocketVersion.fromSecWebSocketVersion,
    )

  final val secWebSocketKey: HeaderCodec[SecWebSocketKey] =
    header(HeaderNames.secWebSocketKey.toString(), Left(TextCodec.string)).transform(
      SecWebSocketKey.toSecWebSocketKey,
      SecWebSocketKey.fromSecWebSocketKey,
    )

  final val secWebSocketAccept: HeaderCodec[SecWebSocketAccept] =
    header(HeaderNames.secWebSocketAccept.toString(), Left(TextCodec.string)).transform(
      SecWebSocketAccept.toSecWebSocketAccept,
      SecWebSocketAccept.fromSecWebSocketAccept,
    )

  final val secWebSocketExtensions: HeaderCodec[SecWebSocketExtensions] =
    header(HeaderNames.secWebSocketExtensions.toString(), Left(TextCodec.string)).transform(
      SecWebSocketExtensions.toSecWebSocketExtensions,
      SecWebSocketExtensions.fromSecWebSocketExtensions,
    )

  final val server: HeaderCodec[Server] =
    header(HeaderNames.server.toString(), Left(TextCodec.string)).transform(Server.toServer, Server.fromServer)

  final val setCookie: HeaderCodec[ResponseCookie] = header(HeaderNames.setCookie.toString(), Left(TextCodec.string))
    .transform(ResponseCookie.toCookie, ResponseCookie.fromCookie)

  final val te: HeaderCodec[Te] = header(HeaderNames.te.toString(), Left(TextCodec.string)).transform(
    Te.toTe,
    Te.fromTe,
  )

  final val trailer: HeaderCodec[Trailer] = header(HeaderNames.trailer.toString(), Left(TextCodec.string))
    .transform(Trailer.toTrailer, Trailer.fromTrailer)

  final val transferEncoding: HeaderCodec[TransferEncoding] = header(
    HeaderNames.transferEncoding.toString(),
    Left(TextCodec.string),
  ).transform(TransferEncoding.toTransferEncoding, TransferEncoding.fromTransferEncoding)

  final val upgrade: HeaderCodec[Upgrade] = header(HeaderNames.upgrade.toString(), Left(TextCodec.string))
    .transform(Upgrade.toUpgrade, Upgrade.fromUpgrade)

  final val upgradeInsecureRequests: HeaderCodec[UpgradeInsecureRequests] =
    header(HeaderNames.upgradeInsecureRequests.toString(), Left(TextCodec.string))
      .transform(UpgradeInsecureRequests.toUpgradeInsecureRequests, UpgradeInsecureRequests.fromUpgradeInsecureRequests)

  final val userAgent: HeaderCodec[UserAgent] =
    header(HeaderNames.userAgent.toString(), Left(TextCodec.string))
      .transform(UserAgent.toUserAgent, UserAgent.fromUserAgent)

  final val vary: HeaderCodec[Vary] = header(HeaderNames.vary.toString(), Left(TextCodec.string))
    .transform(Vary.toVary, Vary.fromVary)

  final val via: HeaderCodec[Via] = header(HeaderNames.via.toString(), Left(TextCodec.string)).transform(
    Via.toVia,
    Via.fromVia,
  )

  final val warning: HeaderCodec[Warning] =
    header(HeaderNames.warning.toString(), Left(TextCodec.string))
      .transform[Warning](Warning.toWarning, Warning.fromWarning)

  final val webSocketLocation: HeaderCodec[SecWebSocketLocation] =
    header(HeaderNames.webSocketLocation.toString(), Left(TextCodec.string)).transform(
      SecWebSocketLocation.toSecWebSocketLocation,
      SecWebSocketLocation.fromSecWebSocketLocation,
    )

  final val webSocketOrigin: HeaderCodec[SecWebSocketOrigin] =
    header(HeaderNames.webSocketOrigin.toString(), Left(TextCodec.string)).transform(
      SecWebSocketOrigin.toSecWebSocketOrigin,
      SecWebSocketOrigin.fromSecWebSocketOrigin,
    )

  final val webSocketProtocol: HeaderCodec[SecWebSocketProtocol] =
    header(HeaderNames.webSocketProtocol.toString(), Left(TextCodec.string)).transform(
      SecWebSocketProtocol.toSecWebSocketProtocol,
      SecWebSocketProtocol.fromSecWebSocketProtocol,
    )

  final val wwwAuthenticate: HeaderCodec[WWWAuthenticate] =
    header(HeaderNames.wwwAuthenticate.toString(), Left(TextCodec.string)).transform(
      WWWAuthenticate.toWWWAuthenticate,
      WWWAuthenticate.fromWWWAuthenticate,
    )

  final val xFrameOptions: HeaderCodec[XFrameOptions] =
    header(HeaderNames.xFrameOptions.toString(), Left(TextCodec.string)).transform(
      XFrameOptions.toXFrameOptions,
      XFrameOptions.fromXFrameOptions,
    )

  final val xRequestedWith: HeaderCodec[XRequestedWith] =
    header(HeaderNames.xRequestedWith.toString(), Left(TextCodec.string)).transform(
      XRequestedWith.toXRequestedWith,
      XRequestedWith.fromXRequestedWith,
    )
}
