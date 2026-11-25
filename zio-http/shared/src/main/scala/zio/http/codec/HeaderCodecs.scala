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

import java.util.UUID

import scala.util.Try

import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.schema._

import zio.http.Header.{HeaderType, SchemaHeaderType}
import zio.http._

/**
 * The header codecs that `HeaderCodec` and `HttpCodec` provide: a codec for
 * each well-known header and constructors for custom headers.
 */
private[codec] trait HeaderCodecs {
  private[http] def headerCodec[A](name: String, value: TextCodec[A]): HeaderCodec[A] = {
    val schema = value match {
      case TextCodec.Constant(string) =>
        Schema[String].transformOrFail[Unit](
          s => if (s == string) Right(()) else Left(s"Header $name was not $string"),
          (_: Unit) => Right(string),
        )
      case TextCodec.StringCodec      => Schema[String]
      case TextCodec.IntCodec         => Schema[Int]
      case TextCodec.LongCodec        => Schema[Long]
      case TextCodec.BooleanCodec     => Schema[Boolean]
      case TextCodec.UUIDCodec        => Schema[UUID]
    }
    HttpCodec.Header(SchemaHeaderType(name)(schema.asInstanceOf[Schema[A]]))
  }

  /**
   * Creates a codec for the header that `headerType` describes, such as
   * `Header.ContentType`, which parses the header into its typed value and
   * renders it back. Decoding a request without the header gives an
   * `HttpCodecError.MissingHeader` error.
   */
  def header(headerType: HeaderType): HeaderCodec[headerType.HeaderValue] =
    HttpCodec.Header(headerType)

  /**
   * Creates a codec for the header `name`, whose value is decoded and encoded
   * with the ZIO Schema of `A`: for example a primitive such as `Int` or
   * `java.util.UUID`, an `Option` for a header that may be missing, or a
   * collection such as `List[Int]`, which reads every header named `name`.
   * Decoding a request without the header gives an
   * `HttpCodecError.MissingHeaders` error, and a value that does not decode
   * gives an `HttpCodecError.DecodingErrorHeader` error.
   *
   * {{{
   * val count: HeaderCodec[Int] = HeaderCodec.headerAs[Int]("x-count")
   *
   * count.encodeRequest(5).headers.toList // List((x-count,5))
   * }}}
   */
  def headerAs[A](name: String)(implicit schema: Schema[A]): HeaderCodec[A] =
    HttpCodec.Header(SchemaHeaderType(name))

  /**
   * Creates a codec for several headers at once, held in a case class `A` with
   * two or more fields and a ZIO Schema. Each field is a header whose name is
   * the field name in kebab case: a field `requestId` is the header
   * `request-id`. For a primitive schema such as `Int`, it reads and writes the
   * header named `headers`.
   */
  def headers[A](implicit schema: Schema[A]): HeaderCodec[A] =
    HttpCodec.Header(SchemaHeaderType("headers"))

  /**
   * Creates a codec for the header `name`, decoded and encoded with the
   * `TextCodec` of `A`. Use `headerAs` instead.
   */
  @deprecated("Use Schema based headerAs instead", "3.1.0")
  def name[A](name: String)(implicit codec: TextCodec[A]): HeaderCodec[A] =
    headerCodec(name, codec)

  /**
   * Creates a codec for the header `name` that decodes the text with the
   * `TextCodec` of `B`, then converts it with `parse`, and converts values back
   * with `render`. An exception thrown by `parse` gives a decoding error whose
   * message, for a header `x-id`, is `Failed to parse header x-id: ` followed
   * by the exception's message. Use `headerAs` instead.
   */
  @deprecated("Use Schema based API instead", "3.1.0")
  def nameTransform[A, B](
    name: String,
    parse: B => A,
    render: A => B,
  )(implicit codec: TextCodec[B]): HeaderCodec[A] =
    headerCodec(name, codec).transformOrFailLeft(s =>
      Try(parse(s)).toEither.left.map(e => s"Failed to parse header $name: ${e.getMessage}"),
    )(render)

  /**
   * Creates a codec for the header `name` that decodes the text with the
   * `TextCodec` of `B`, then converts it with `parse`, and converts values back
   * with `render`. A `None` from `parse` gives a decoding error whose message,
   * for a header `x-id`, is `Failed to parse header x-id`. Use `headerAs`
   * instead.
   */
  @deprecated("Use Schema based API instead", "3.1.0")
  def nameTransformOption[A, B](name: String, parse: B => Option[A], render: A => B)(implicit
    codec: TextCodec[B],
  ): HeaderCodec[A] =
    headerCodec(name, codec).transformOrFailLeft(parse(_).toRight(s"Failed to parse header $name"))(render)

  /**
   * Creates a codec for the header `name` that decodes the text with the
   * `TextCodec` of `B`, then converts it with `parse`, and converts values back
   * with `render`. A `Left` from `parse` gives a decoding error with its
   * message. Use `headerAs` instead.
   */
  @deprecated("Use Schema based API instead", "3.1.0")
  def nameTransformOrFail[A, B](name: String, parse: B => Either[String, A], render: A => B)(implicit
    codec: TextCodec[B],
  ): HeaderCodec[A] =
    headerCodec(name, codec).transformOrFailLeft(parse)(render)

  /** The codec for the `Accept` header, as a `Header.Accept`. */
  final val accept: HeaderCodec[Header.Accept] = header(Header.Accept)

  /**
   * The codec for the `Accept-Encoding` header, as a `Header.AcceptEncoding`.
   */
  final val acceptEncoding: HeaderCodec[Header.AcceptEncoding] = header(Header.AcceptEncoding)

  /**
   * The codec for the `Accept-Language` header, as a `Header.AcceptLanguage`.
   */
  final val acceptLanguage: HeaderCodec[Header.AcceptLanguage] = header(Header.AcceptLanguage)

  /** The codec for the `Accept-Ranges` header, as a `Header.AcceptRanges`. */
  final val acceptRanges: HeaderCodec[Header.AcceptRanges] = header(Header.AcceptRanges)

  /** The codec for the `Accept-Patch` header, as a `Header.AcceptPatch`. */
  final val acceptPatch: HeaderCodec[Header.AcceptPatch] = header(Header.AcceptPatch)

  /**
   * The codec for the `Access-Control-Allow-Credentials` header, as a
   * `Header.AccessControlAllowCredentials`.
   */
  final val accessControlAllowCredentials: HeaderCodec[Header.AccessControlAllowCredentials] = header(
    Header.AccessControlAllowCredentials,
  )

  /**
   * The codec for the `Access-Control-Allow-Headers` header, as a
   * `Header.AccessControlAllowHeaders`.
   */
  final val accessControlAllowHeaders: HeaderCodec[Header.AccessControlAllowHeaders] = header(
    Header.AccessControlAllowHeaders,
  )

  /**
   * The codec for the `Access-Control-Allow-Methods` header, as a
   * `Header.AccessControlAllowMethods`.
   */
  final val accessControlAllowMethods: HeaderCodec[Header.AccessControlAllowMethods] = header(
    Header.AccessControlAllowMethods,
  )

  /**
   * The codec for the `Access-Control-Allow-Origin` header, as a
   * `Header.AccessControlAllowOrigin`.
   */
  final val accessControlAllowOrigin: HeaderCodec[Header.AccessControlAllowOrigin] = header(
    Header.AccessControlAllowOrigin,
  )

  /**
   * The codec for the `Access-Control-Expose-Headers` header, as a
   * `Header.AccessControlExposeHeaders`.
   */
  final val accessControlExposeHeaders: HeaderCodec[Header.AccessControlExposeHeaders] = header(
    Header.AccessControlExposeHeaders,
  )

  /**
   * The codec for the `Access-Control-Max-Age` header, as a
   * `Header.AccessControlMaxAge`.
   */
  final val accessControlMaxAge: HeaderCodec[Header.AccessControlMaxAge] = header(Header.AccessControlMaxAge)

  /**
   * The codec for the `Access-Control-Request-Headers` header, as a
   * `Header.AccessControlRequestHeaders`.
   */
  final val accessControlRequestHeaders: HeaderCodec[Header.AccessControlRequestHeaders] = header(
    Header.AccessControlRequestHeaders,
  )

  /**
   * The codec for the `Access-Control-Request-Method` header, as a
   * `Header.AccessControlRequestMethod`.
   */
  final val accessControlRequestMethod: HeaderCodec[Header.AccessControlRequestMethod] = header(
    Header.AccessControlRequestMethod,
  )

  /** The codec for the `Age` header, as a `Header.Age`. */
  final val age: HeaderCodec[Header.Age] = header(Header.Age)

  /** The codec for the `Allow` header, as a `Header.Allow`. */
  final val allow: HeaderCodec[Header.Allow] = header(Header.Allow)

  /** The codec for the `Authorization` header, as a `Header.Authorization`. */
  final val authorization: HeaderCodec[Header.Authorization] = header(Header.Authorization)

  /**
   * The codec for the `Authorization` header, as a
   * `Header.Authorization.Basic`.
   */
  final val basicAuth: HeaderCodec[Header.Authorization.Basic] = header(Header.Authorization.Basic)

  /**
   * The codec for the `Authorization` header, as a
   * `Header.Authorization.Bearer`.
   */
  final val bearerAuth: HeaderCodec[Header.Authorization.Bearer] = header(Header.Authorization.Bearer)

  /**
   * The codec for the `Authorization` header, as a
   * `Header.Authorization.Digest`.
   */
  final val digestAuth: HeaderCodec[Header.Authorization.Digest] = header(Header.Authorization.Digest)

  /** The codec for the `Cache-Control` header, as a `Header.CacheControl`. */
  final val cacheControl: HeaderCodec[Header.CacheControl] = header(Header.CacheControl)

  /**
   * The codec for the `Clear-Site-Data` header, as a `Header.ClearSiteData`.
   */
  final val clearSiteData: HeaderCodec[Header.ClearSiteData] = header(Header.ClearSiteData)

  /** The codec for the `Connection` header, as a `Header.Connection`. */
  final val connection: HeaderCodec[Header.Connection] = header(Header.Connection)

  /** The codec for the `Content-Base` header, as a `Header.ContentBase`. */
  final val contentBase: HeaderCodec[Header.ContentBase] = header(Header.ContentBase)

  /**
   * The codec for the `Content-Encoding` header, as a `Header.ContentEncoding`.
   */
  final val contentEncoding: HeaderCodec[Header.ContentEncoding] = header(Header.ContentEncoding)

  /**
   * The codec for the `Content-Language` header, as a `Header.ContentLanguage`.
   */
  final val contentLanguage: HeaderCodec[Header.ContentLanguage] = header(Header.ContentLanguage)

  /** The codec for the `Content-Length` header, as a `Header.ContentLength`. */
  final val contentLength: HeaderCodec[Header.ContentLength] = header(Header.ContentLength)

  /**
   * The codec for the `Content-Location` header, as a `Header.ContentLocation`.
   */
  final val contentLocation: HeaderCodec[Header.ContentLocation] = header(Header.ContentLocation)

  /**
   * The codec for the `Content-Transfer-Encoding` header, as a
   * `Header.ContentTransferEncoding`.
   */
  final val contentTransferEncoding: HeaderCodec[Header.ContentTransferEncoding] = header(
    Header.ContentTransferEncoding,
  )

  /**
   * The codec for the `Content-Disposition` header, as a
   * `Header.ContentDisposition`.
   */
  final val contentDisposition: HeaderCodec[Header.ContentDisposition] = header(Header.ContentDisposition)

  /** The codec for the `Content-MD5` header, as a `Header.ContentMd5`. */
  final val contentMd5: HeaderCodec[Header.ContentMd5] = header(Header.ContentMd5)

  /** The codec for the `Content-Range` header, as a `Header.ContentRange`. */
  final val contentRange: HeaderCodec[Header.ContentRange] = header(Header.ContentRange)

  /**
   * The codec for the `Content-Security-Policy` header, as a
   * `Header.ContentSecurityPolicy`.
   */
  final val contentSecurityPolicy: HeaderCodec[Header.ContentSecurityPolicy] = header(Header.ContentSecurityPolicy)

  /** The codec for the `Content-Type` header, as a `Header.ContentType`. */
  final val contentType: HeaderCodec[Header.ContentType] = header(Header.ContentType)

  /** The codec for the `Cookie` header, as a `Header.Cookie`. */
  final val cookie: HeaderCodec[Header.Cookie] = header(Header.Cookie)

  /** The codec for the `Date` header, as a `Header.Date`. */
  final val date: HeaderCodec[Header.Date] = header(Header.Date)

  /** The codec for the `DNT` header, as a `Header.DNT`. */
  final val dnt: HeaderCodec[Header.DNT] = header(Header.DNT)

  /** The codec for the `ETag` header, as a `Header.ETag`. */
  final val etag: HeaderCodec[Header.ETag] = header(Header.ETag)

  /** The codec for the `Expect` header, as a `Header.Expect`. */
  final val expect: HeaderCodec[Header.Expect] = header(Header.Expect)

  /** The codec for the `Expires` header, as a `Header.Expires`. */
  final val expires: HeaderCodec[Header.Expires] = header(Header.Expires)

  /** The codec for the `Forwarded` header, as a `Header.Forwarded`. */
  final val forwarded: HeaderCodec[Header.Forwarded] = header(Header.Forwarded)

  /** The codec for the `From` header, as a `Header.From`. */
  final val from: HeaderCodec[Header.From] = header(Header.From)

  /** The codec for the `Host` header, as a `Header.Host`. */
  final val host: HeaderCodec[Header.Host] = header(Header.Host)

  /** The codec for the `If-Match` header, as a `Header.IfMatch`. */
  final val ifMatch: HeaderCodec[Header.IfMatch] = header(Header.IfMatch)

  /**
   * The codec for the `If-Modified-Since` header, as a
   * `Header.IfModifiedSince`.
   */
  final val ifModifiedSince: HeaderCodec[Header.IfModifiedSince] = header(Header.IfModifiedSince)

  /** The codec for the `If-None-Match` header, as a `Header.IfNoneMatch`. */
  final val ifNoneMatch: HeaderCodec[Header.IfNoneMatch] = header(Header.IfNoneMatch)

  /** The codec for the `If-Range` header, as a `Header.IfRange`. */
  final val ifRange: HeaderCodec[Header.IfRange] = header(Header.IfRange)

  /**
   * The codec for the `If-Unmodified-Since` header, as a
   * `Header.IfUnmodifiedSince`.
   */
  final val ifUnmodifiedSince: HeaderCodec[Header.IfUnmodifiedSince] = header(Header.IfUnmodifiedSince)

  /** The codec for the `Last-Modified` header, as a `Header.LastModified`. */
  final val lastModified: HeaderCodec[Header.LastModified] = header(Header.LastModified)

  /** The codec for the `Link` header, as a `Header.Link`. */
  final val link: HeaderCodec[Header.Link] = header(Header.Link)

  /** The codec for the `Location` header, as a `Header.Location`. */
  final val location: HeaderCodec[Header.Location] = header(Header.Location)

  /** The codec for the `Max-Forwards` header, as a `Header.MaxForwards`. */
  final val maxForwards: HeaderCodec[Header.MaxForwards] = header(Header.MaxForwards)

  /** The codec for the `Origin` header, as a `Header.Origin`. */
  final val origin: HeaderCodec[Header.Origin] = header(Header.Origin)

  /** The codec for the `Pragma` header, as a `Header.Pragma`. */
  final val pragma: HeaderCodec[Header.Pragma] = header(Header.Pragma)

  /**
   * The codec for the `Proxy-Authenticate` header, as a
   * `Header.ProxyAuthenticate`.
   */
  final val proxyAuthenticate: HeaderCodec[Header.ProxyAuthenticate] = header(Header.ProxyAuthenticate)

  /**
   * The codec for the `Proxy-Authorization` header, as a
   * `Header.ProxyAuthorization`.
   */
  final val proxyAuthorization: HeaderCodec[Header.ProxyAuthorization] = header(Header.ProxyAuthorization)

  /** The codec for the `Range` header, as a `Header.Range`. */
  final val range: HeaderCodec[Header.Range] = header(Header.Range)

  /** The codec for the `Referer` header, as a `Header.Referer`. */
  final val referer: HeaderCodec[Header.Referer] = header(Header.Referer)

  /** The codec for the `Retry-After` header, as a `Header.RetryAfter`. */
  final val retryAfter: HeaderCodec[Header.RetryAfter] = header(Header.RetryAfter)

  /**
   * The codec for the `Sec-WebSocket-Location` header, as a
   * `Header.SecWebSocketLocation`.
   */
  final val secWebSocketLocation: HeaderCodec[Header.SecWebSocketLocation] = header(Header.SecWebSocketLocation)

  /**
   * The codec for the `Sec-WebSocket-Origin` header, as a
   * `Header.SecWebSocketOrigin`.
   */
  final val secWebSocketOrigin: HeaderCodec[Header.SecWebSocketOrigin] = header(Header.SecWebSocketOrigin)

  /**
   * The codec for the `Sec-WebSocket-Protocol` header, as a
   * `Header.SecWebSocketProtocol`.
   */
  final val secWebSocketProtocol: HeaderCodec[Header.SecWebSocketProtocol] = header(Header.SecWebSocketProtocol)

  /**
   * The codec for the `Sec-WebSocket-Version` header, as a
   * `Header.SecWebSocketVersion`.
   */
  final val secWebSocketVersion: HeaderCodec[Header.SecWebSocketVersion] = header(Header.SecWebSocketVersion)

  /**
   * The codec for the `Sec-WebSocket-Key` header, as a
   * `Header.SecWebSocketKey`.
   */
  final val secWebSocketKey: HeaderCodec[Header.SecWebSocketKey] = header(Header.SecWebSocketKey)

  /**
   * The codec for the `Sec-WebSocket-Accept` header, as a
   * `Header.SecWebSocketAccept`.
   */
  final val secWebSocketAccept: HeaderCodec[Header.SecWebSocketAccept] = header(Header.SecWebSocketAccept)

  /**
   * The codec for the `Sec-WebSocket-Extensions` header, as a
   * `Header.SecWebSocketExtensions`.
   */
  final val secWebSocketExtensions: HeaderCodec[Header.SecWebSocketExtensions] = header(Header.SecWebSocketExtensions)

  /** The codec for the `Server` header, as a `Header.Server`. */
  final val server: HeaderCodec[Header.Server] = header(Header.Server)

  /** The codec for the `Set-Cookie` header, as a `Header.SetCookie`. */
  final val setCookie: HeaderCodec[Header.SetCookie] = header(Header.SetCookie)

  /** The codec for the `TE` header, as a `Header.Te`. */
  final val te: HeaderCodec[Header.Te] = header(Header.Te)

  /** The codec for the `Trailer` header, as a `Header.Trailer`. */
  final val trailer: HeaderCodec[Header.Trailer] = header(Header.Trailer)

  /**
   * The codec for the `Transfer-Encoding` header, as a
   * `Header.TransferEncoding`.
   */
  final val transferEncoding: HeaderCodec[Header.TransferEncoding] = header(Header.TransferEncoding)

  /** The codec for the `Upgrade` header, as a `Header.Upgrade`. */
  final val upgrade: HeaderCodec[Header.Upgrade] = header(Header.Upgrade)

  /**
   * The codec for the `Upgrade-Insecure-Requests` header, as a
   * `Header.UpgradeInsecureRequests`.
   */
  final val upgradeInsecureRequests: HeaderCodec[Header.UpgradeInsecureRequests] = header(
    Header.UpgradeInsecureRequests,
  )

  /** The codec for the `User-Agent` header, as a `Header.UserAgent`. */
  final val userAgent: HeaderCodec[Header.UserAgent] = header(Header.UserAgent)

  /** The codec for the `Vary` header, as a `Header.Vary`. */
  final val vary: HeaderCodec[Header.Vary] = header(Header.Vary)

  /** The codec for the `Via` header, as a `Header.Via`. */
  final val via: HeaderCodec[Header.Via] = header(Header.Via)

  /**
   * The codec for the `Sec-WebSocket-Location` header, as a
   * `Header.SecWebSocketLocation`; the same as `secWebSocketLocation`.
   */
  final val webSocketLocation: HeaderCodec[Header.SecWebSocketLocation] = header(Header.SecWebSocketLocation)

  /**
   * The codec for the `Sec-WebSocket-Origin` header, as a
   * `Header.SecWebSocketOrigin`; the same as `secWebSocketOrigin`.
   */
  final val webSocketOrigin: HeaderCodec[Header.SecWebSocketOrigin] = header(Header.SecWebSocketOrigin)

  /**
   * The codec for the `Sec-WebSocket-Protocol` header, as a
   * `Header.SecWebSocketProtocol`; the same as `secWebSocketProtocol`.
   */
  final val webSocketProtocol: HeaderCodec[Header.SecWebSocketProtocol] = header(Header.SecWebSocketProtocol)

  /**
   * The codec for the `WWW-Authenticate` header, as a `Header.WWWAuthenticate`.
   */
  final val wwwAuthenticate: HeaderCodec[Header.WWWAuthenticate] = header(Header.WWWAuthenticate)

  /**
   * The codec for the `X-Frame-Options` header, as a `Header.XFrameOptions`.
   */
  final val xFrameOptions: HeaderCodec[Header.XFrameOptions] = header(Header.XFrameOptions)

  /**
   * The codec for the `X-Requested-With` header, as a `Header.XRequestedWith`.
   */
  final val xRequestedWith: HeaderCodec[Header.XRequestedWith] = header(Header.XRequestedWith)
}
