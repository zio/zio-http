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

import scala.util.Try

import zio.http.Header.HeaderType
import zio.http.{Header, MediaType}

private[codec] trait HeaderCodecs {
  private[http] def headerCodec[A](name: String, value: TextCodec[A]): HeaderCodec[A] =
    HttpCodec.Header(name, value)

  def header(headerType: HeaderType): HeaderCodec[headerType.HeaderValue] =
    headerCodec(headerType.name, TextCodec.string)
      .transformOrFailLeft(headerType.parse(_), headerType.render(_))

  def name[A](name: String)(implicit codec: TextCodec[A]): HeaderCodec[A] =
    headerCodec(name, codec)

  def nameTransform[A, B](
    name: String,
    parse: B => A,
    render: A => B,
  )(implicit codec: TextCodec[B]): HeaderCodec[A] =
    headerCodec(name, codec).transformOrFailLeft(
      s => Try(parse(s)).toEither.left.map(e => s"Failed to parse header $name: ${e.getMessage}"),
      render,
    )

  def nameTransformOption[A, B](name: String, parse: B => Option[A], render: A => B)(implicit
    codec: TextCodec[B],
  ): HeaderCodec[A] =
    headerCodec(name, codec).transformOrFailLeft(parse(_).toRight(s"Failed to parse header $name"), render)

  def nameTransformOrFail[A, B](name: String, parse: B => Either[String, A], render: A => B)(implicit
    codec: TextCodec[B],
  ): HeaderCodec[A] =
    headerCodec(name, codec).transformOrFailLeft(parse, render)

  final val accept: HeaderCodec[Header.Accept]                 = header(Header.Accept)
  final val acceptEncoding: HeaderCodec[Header.AcceptEncoding] = header(Header.AcceptEncoding)
  final val acceptLanguage: HeaderCodec[Header.AcceptLanguage] = header(Header.AcceptLanguage)
  final val acceptRanges: HeaderCodec[Header.AcceptRanges]     = header(Header.AcceptRanges)
  final val acceptPatch: HeaderCodec[Header.AcceptPatch]       = header(Header.AcceptPatch)
  final val accessControlAllowCredentials: HeaderCodec[Header.AccessControlAllowCredentials] = header(
    Header.AccessControlAllowCredentials,
  )
  final val accessControlAllowHeaders: HeaderCodec[Header.AccessControlAllowHeaders]         = header(
    Header.AccessControlAllowHeaders,
  )
  final val accessControlAllowMethods: HeaderCodec[Header.AccessControlAllowMethods]         = header(
    Header.AccessControlAllowMethods,
  )
  final val accessControlAllowOrigin: HeaderCodec[Header.AccessControlAllowOrigin]           = header(
    Header.AccessControlAllowOrigin,
  )
  final val accessControlExposeHeaders: HeaderCodec[Header.AccessControlExposeHeaders]       = header(
    Header.AccessControlExposeHeaders,
  )
  final val accessControlMaxAge: HeaderCodec[Header.AccessControlMaxAge] = header(Header.AccessControlMaxAge)
  final val accessControlRequestHeaders: HeaderCodec[Header.AccessControlRequestHeaders] = header(
    Header.AccessControlRequestHeaders,
  )
  final val accessControlRequestMethod: HeaderCodec[Header.AccessControlRequestMethod]   = header(
    Header.AccessControlRequestMethod,
  )
  final val age: HeaderCodec[Header.Age]                                                 = header(Header.Age)
  final val allow: HeaderCodec[Header.Allow]                                             = header(Header.Allow)
  final val authorization: HeaderCodec[Header.Authorization]                             = header(Header.Authorization)
  final val cacheControl: HeaderCodec[Header.CacheControl]                               = header(Header.CacheControl)
  final val connection: HeaderCodec[Header.Connection]                                   = header(Header.Connection)
  final val contentBase: HeaderCodec[Header.ContentBase]                                 = header(Header.ContentBase)
  final val contentEncoding: HeaderCodec[Header.ContentEncoding]                 = header(Header.ContentEncoding)
  final val contentLanguage: HeaderCodec[Header.ContentLanguage]                 = header(Header.ContentLanguage)
  final val contentLength: HeaderCodec[Header.ContentLength]                     = header(Header.ContentLength)
  final val contentLocation: HeaderCodec[Header.ContentLocation]                 = header(Header.ContentLocation)
  final val contentTransferEncoding: HeaderCodec[Header.ContentTransferEncoding] = header(
    Header.ContentTransferEncoding,
  )
  final val contentDisposition: HeaderCodec[Header.ContentDisposition]           = header(Header.ContentDisposition)
  final val contentMd5: HeaderCodec[Header.ContentMd5]                           = header(Header.ContentMd5)
  final val contentRange: HeaderCodec[Header.ContentRange]                       = header(Header.ContentRange)
  final val contentSecurityPolicy: HeaderCodec[Header.ContentSecurityPolicy]     = header(Header.ContentSecurityPolicy)
  final val contentType: HeaderCodec[Header.ContentType]                         = header(Header.ContentType)
  final val cookie: HeaderCodec[Header.Cookie]                                   = header(Header.Cookie)
  final val date: HeaderCodec[Header.Date]                                       = header(Header.Date)
  final val dnt: HeaderCodec[Header.DNT]                                         = header(Header.DNT)
  final val etag: HeaderCodec[Header.ETag]                                       = header(Header.ETag)
  final val expect: HeaderCodec[Header.Expect]                                   = header(Header.Expect)
  final val expires: HeaderCodec[Header.Expires]                                 = header(Header.Expires)
  final val from: HeaderCodec[Header.From]                                       = header(Header.From)
  final val host: HeaderCodec[Header.Host]                                       = header(Header.Host)
  final val ifMatch: HeaderCodec[Header.IfMatch]                                 = header(Header.IfMatch)
  final val ifModifiedSince: HeaderCodec[Header.IfModifiedSince]                 = header(Header.IfModifiedSince)
  final val ifNoneMatch: HeaderCodec[Header.IfNoneMatch]                         = header(Header.IfNoneMatch)
  final val ifRange: HeaderCodec[Header.IfRange]                                 = header(Header.IfRange)
  final val ifUnmodifiedSince: HeaderCodec[Header.IfUnmodifiedSince]             = header(Header.IfUnmodifiedSince)
  final val lastModified: HeaderCodec[Header.LastModified]                       = header(Header.LastModified)
  final val location: HeaderCodec[Header.Location]                               = header(Header.Location)
  final val maxForwards: HeaderCodec[Header.MaxForwards]                         = header(Header.MaxForwards)
  final val origin: HeaderCodec[Header.Origin]                                   = header(Header.Origin)
  final val pragma: HeaderCodec[Header.Pragma]                                   = header(Header.Pragma)
  final val proxyAuthenticate: HeaderCodec[Header.ProxyAuthenticate]             = header(Header.ProxyAuthenticate)
  final val proxyAuthorization: HeaderCodec[Header.ProxyAuthorization]           = header(Header.ProxyAuthorization)
  final val range: HeaderCodec[Header.Range]                                     = header(Header.Range)
  final val referer: HeaderCodec[Header.Referer]                                 = header(Header.Referer)
  final val retryAfter: HeaderCodec[Header.RetryAfter]                           = header(Header.RetryAfter)
  final val secWebSocketLocation: HeaderCodec[Header.SecWebSocketLocation]       = header(Header.SecWebSocketLocation)
  final val secWebSocketOrigin: HeaderCodec[Header.SecWebSocketOrigin]           = header(Header.SecWebSocketOrigin)
  final val secWebSocketProtocol: HeaderCodec[Header.SecWebSocketProtocol]       = header(Header.SecWebSocketProtocol)
  final val secWebSocketVersion: HeaderCodec[Header.SecWebSocketVersion]         = header(Header.SecWebSocketVersion)
  final val secWebSocketKey: HeaderCodec[Header.SecWebSocketKey]                 = header(Header.SecWebSocketKey)
  final val secWebSocketAccept: HeaderCodec[Header.SecWebSocketAccept]           = header(Header.SecWebSocketAccept)
  final val secWebSocketExtensions: HeaderCodec[Header.SecWebSocketExtensions]   = header(Header.SecWebSocketExtensions)
  final val server: HeaderCodec[Header.Server]                                   = header(Header.Server)
  final val setCookie: HeaderCodec[Header.SetCookie]                             = header(Header.SetCookie)
  final val te: HeaderCodec[Header.Te]                                           = header(Header.Te)
  final val trailer: HeaderCodec[Header.Trailer]                                 = header(Header.Trailer)
  final val transferEncoding: HeaderCodec[Header.TransferEncoding]               = header(Header.TransferEncoding)
  final val upgrade: HeaderCodec[Header.Upgrade]                                 = header(Header.Upgrade)
  final val upgradeInsecureRequests: HeaderCodec[Header.UpgradeInsecureRequests] = header(
    Header.UpgradeInsecureRequests,
  )
  final val userAgent: HeaderCodec[Header.UserAgent]                             = header(Header.UserAgent)
  final val vary: HeaderCodec[Header.Vary]                                       = header(Header.Vary)
  final val via: HeaderCodec[Header.Via]                                         = header(Header.Via)
  final val warning: HeaderCodec[Header.Warning]                                 = header(Header.Warning)
  final val webSocketLocation: HeaderCodec[Header.SecWebSocketLocation]          = header(Header.SecWebSocketLocation)
  final val webSocketOrigin: HeaderCodec[Header.SecWebSocketOrigin]              = header(Header.SecWebSocketOrigin)
  final val webSocketProtocol: HeaderCodec[Header.SecWebSocketProtocol]          = header(Header.SecWebSocketProtocol)
  final val wwwAuthenticate: HeaderCodec[Header.WWWAuthenticate]                 = header(Header.WWWAuthenticate)
  final val xFrameOptions: HeaderCodec[Header.XFrameOptions]                     = header(Header.XFrameOptions)
  final val xRequestedWith: HeaderCodec[Header.XRequestedWith]                   = header(Header.XRequestedWith)
}
