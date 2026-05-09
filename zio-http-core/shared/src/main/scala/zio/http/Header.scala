package zio.http

import scala.util.control.NonFatal

import zio.Chunk

import zio.schema.Schema
import zio.schema.codec.DecodeError
import zio.schema.validation.ValidationError

import zio.http.headers._
import zio.http.codec.HttpCodecError
import zio.http.internal.{ErrorConstructor, StringSchemaCodec}

trait Header {
  def headerName: String
  def renderedValue: String
}

object Header {

  trait Typed[H <: Header] {
    def name: String
    def parse(value: String): Either[String, H]
    def render(value: H): String
  }

  final case class Custom(customName: CharSequence, value: CharSequence) extends Header {
    override def headerName: String    = customName.toString
    override def renderedValue: String = value.toString
  }

  sealed trait HeaderTypeBase {
    type HeaderValue

    def names: Chunk[String]

    def fromHeaders(headers: Headers): Either[String, HeaderValue]

    private[http] def fromHeadersUnsafe(headers: Headers): HeaderValue

    def toHeaders(value: HeaderValue): Headers =
      value match {
        case h: zio.http.Header => Headers.empty.add(h.headerName, h.renderedValue)
        case _                  => Headers.empty
      }
  }

  object HeaderTypeBase {
    type Typed[HV] = HeaderTypeBase { type HeaderValue = HV }
  }

  sealed trait SchemaHeaderType extends HeaderTypeBase {
    def schema: Schema[HeaderValue]

    def optional: HeaderTypeBase.Typed[Option[HeaderValue]]
  }

  object SchemaHeaderType {
    type Typed[H] = SchemaHeaderType { type HeaderValue = H }

    private val errorConstructor =
      new ErrorConstructor {
        override def missing(fieldName: String): HttpCodecError =
          HttpCodecError.MissingHeader(fieldName)

        override def missingAll(fieldNames: Chunk[String]): HttpCodecError =
          HttpCodecError.MissingHeaders(fieldNames)

        override def invalid(errors: Chunk[ValidationError]): HttpCodecError =
          HttpCodecError.InvalidEntity.wrap(errors)

        override def malformed(fieldName: String, error: DecodeError): HttpCodecError =
          HttpCodecError.DecodingErrorHeader(fieldName, error)

        override def invalidCount(fieldName: String, expected: Int, actual: Int): HttpCodecError =
          HttpCodecError.InvalidHeaderCount(fieldName, expected, actual)
      }

    def apply[H](implicit schema0: Schema[H]): SchemaHeaderType.Typed[H] = {
      new SchemaHeaderType {
        type HeaderValue = H
        val schema: Schema[H]                    = schema0
        val codec: StringSchemaCodec[H, Headers] =
          StringSchemaCodec.headerFromSchema(schema0, errorConstructor, null)

        override def names: Chunk[String] =
          codec.recordFields.map(_._1.fieldName)

        override def optional: HeaderTypeBase.Typed[Option[H]] =
          apply(schema.optional)

        override def fromHeaders(headers: Headers): Either[String, H] =
          try Right(codec.decode(headers))
          catch {
            case NonFatal(e) => Left(e.getMessage)
          }

        private[http] override def fromHeadersUnsafe(headers: Headers): H =
          codec.decode(headers)

        override def toHeaders(value: H): Headers =
          codec.encode(value, Headers.empty)
      }
    }

    def apply[H](name: String)(implicit schema0: Schema[H]): SchemaHeaderType.Typed[H] = {
      new SchemaHeaderType {
        type HeaderValue = H
        val schema: Schema[H]                    = schema0
        val codec: StringSchemaCodec[H, Headers] =
          StringSchemaCodec.headerFromSchema(schema, errorConstructor, name)

        override def names: Chunk[String] =
          codec.recordFields.map(_._1.fieldName)

        override def optional: HeaderTypeBase.Typed[Option[H]] =
          apply(name)(schema.optional)

        override def fromHeaders(headers: Headers): Either[String, H] =
          try Right(codec.decode(headers))
          catch {
            case NonFatal(e) => Left(e.getMessage)
          }

        private[http] override def fromHeadersUnsafe(headers: Headers): H =
          codec.decode(headers)

        override def toHeaders(value: H): Headers =
          codec.encode(value, Headers.empty)
      }
    }
  }

  sealed trait HeaderType extends HeaderTypeBase {
    type HeaderValue <: zio.http.Header

    def names: Chunk[String] = Chunk.single(name)

    def name: String

    def parse(value: String): Either[String, HeaderValue]

    def render(value: HeaderValue): String

    def fromHeaders(headers: Headers): Either[String, HeaderValue] =
      headers.rawGet(name) match {
        case None        => Left(s"Header $name not found")
        case Some(value) => parse(value)
      }

    private[http] def fromHeadersUnsafe(headers: Headers): HeaderValue =
      headers.rawGet(name) match {
        case None => throw HttpCodecError.MissingHeader(name)
        case Some(value) =>
          parse(value) match {
            case Left(error) =>
              throw HttpCodecError.DecodingErrorHeader(name, DecodeError.ReadError(zio.Cause.empty, error))
            case Right(v) => v
          }
      }

    override def toHeaders(value: HeaderValue): Headers =
      Headers.empty.add(value.headerName, value.renderedValue)
  }

  object HeaderType {
    type Typed[HV] = HeaderType { type HeaderValue = HV }
  }

  private[http] abstract class HeaderTypeOf[H <: zio.http.Header](
    typed: zio.http.Header.Typed[H],
  ) extends HeaderType {
    override type HeaderValue = H
    override def name: String                              = typed.name
    override def parse(value: String): Either[String, H]  = typed.parse(value)
    override def render(value: H): String                 = typed.render(value)
  }

  

  object Accept extends HeaderTypeOf[headers.Accept](headers.Accept) {
    type MediaRange = headers.Accept.MediaRange
    val MediaRange = headers.Accept.MediaRange
  }

  object AcceptEncoding extends HeaderTypeOf[headers.AcceptEncoding](headers.AcceptEncoding)
  object AcceptLanguage extends HeaderTypeOf[headers.AcceptLanguage](headers.AcceptLanguage)
  object AcceptPatch extends HeaderTypeOf[headers.AcceptPatch](headers.AcceptPatch)
  object AcceptRanges extends HeaderTypeOf[headers.AcceptRanges](headers.AcceptRanges)
  object AccessControlAllowCredentials
      extends HeaderTypeOf[headers.AccessControlAllowCredentials](headers.AccessControlAllowCredentials)
  object AccessControlAllowHeaders
      extends HeaderTypeOf[headers.AccessControlAllowHeaders](headers.AccessControlAllowHeaders)
  object AccessControlAllowMethods
      extends HeaderTypeOf[headers.AccessControlAllowMethods](headers.AccessControlAllowMethods)
  object AccessControlAllowOrigin
      extends HeaderTypeOf[headers.AccessControlAllowOrigin](headers.AccessControlAllowOrigin)
  object AccessControlExposeHeaders
      extends HeaderTypeOf[headers.AccessControlExposeHeaders](headers.AccessControlExposeHeaders)
  object AccessControlMaxAge extends HeaderTypeOf[headers.AccessControlMaxAge](headers.AccessControlMaxAge)
  object AccessControlRequestHeaders
      extends HeaderTypeOf[headers.AccessControlRequestHeaders](headers.AccessControlRequestHeaders)
  object AccessControlRequestMethod
      extends HeaderTypeOf[headers.AccessControlRequestMethod](headers.AccessControlRequestMethod)
  object Age           extends HeaderTypeOf[headers.Age](headers.Age)
  object Allow         extends HeaderTypeOf[headers.Allow](headers.Allow)
  object Authorization extends HeaderTypeOf[headers.Authorization](headers.Authorization) {

    type Basic  = headers.Authorization.Basic
    type Bearer = headers.Authorization.Bearer
    type Digest = headers.Authorization.Digest

    object Basic extends HeaderType {
      override type HeaderValue = headers.Authorization.Basic
      override def name: String = "authorization"
      override def parse(value: String): Either[String, headers.Authorization.Basic] =
        headers.Authorization.parse(value).flatMap {
          case b: headers.Authorization.Basic => Right(b)
          case _                             => Left("Not a Basic authorization header")
        }
      override def render(value: headers.Authorization.Basic): String =
        headers.Authorization.render(value.asInstanceOf[headers.Authorization])
    }

    object Bearer extends HeaderType {
      override type HeaderValue = headers.Authorization.Bearer
      override def name: String = "authorization"
      override def parse(value: String): Either[String, headers.Authorization.Bearer] =
        headers.Authorization.parse(value).flatMap {
          case b: headers.Authorization.Bearer => Right(b)
          case _                              => Left("Not a Bearer authorization header")
        }
      override def render(value: headers.Authorization.Bearer): String =
        headers.Authorization.render(value.asInstanceOf[headers.Authorization])
    }

    object Digest extends HeaderType {
      override type HeaderValue = headers.Authorization.Digest
      override def name: String = "authorization"
      override def parse(value: String): Either[String, headers.Authorization.Digest] =
        headers.Authorization.parse(value).flatMap {
          case b: headers.Authorization.Digest => Right(b)
          case _                              => Left("Not a Digest authorization header")
        }
      override def render(value: headers.Authorization.Digest): String =
        headers.Authorization.render(value.asInstanceOf[headers.Authorization])
    }
  }

  object CacheControl          extends HeaderTypeOf[headers.CacheControl](headers.CacheControl)
  object ClearSiteData         extends HeaderTypeOf[headers.ClearSiteData](headers.ClearSiteData)
  object Connection            extends HeaderTypeOf[headers.Connection](headers.Connection)
  object ContentBase           extends HeaderTypeOf[headers.ContentBase](headers.ContentBase)
  object ContentDisposition    extends HeaderTypeOf[headers.ContentDisposition](headers.ContentDisposition)
  object ContentEncoding       extends HeaderTypeOf[headers.ContentEncoding](headers.ContentEncoding)
  object ContentLanguage       extends HeaderTypeOf[headers.ContentLanguage](headers.ContentLanguage)
  object ContentLength         extends HeaderTypeOf[headers.ContentLength](headers.ContentLength)
  object ContentLocation       extends HeaderTypeOf[headers.ContentLocation](headers.ContentLocation)
  object ContentMd5            extends HeaderTypeOf[headers.ContentMd5](headers.ContentMd5)
  object ContentRange          extends HeaderTypeOf[headers.ContentRange](headers.ContentRange)
  object ContentSecurityPolicy extends HeaderTypeOf[headers.ContentSecurityPolicy](headers.ContentSecurityPolicy)
  object ContentTransferEncoding
      extends HeaderTypeOf[headers.ContentTransferEncoding](headers.ContentTransferEncoding)
  object ContentType extends HeaderTypeOf[headers.ContentType](headers.ContentType)
  object Cookie      extends HeaderTypeOf[headers.CookieHeader](headers.CookieHeader)
  object Date        extends HeaderTypeOf[headers.Date](headers.Date)
  object DNT         extends HeaderTypeOf[headers.DNT](headers.DNT)
  object ETag        extends HeaderTypeOf[headers.ETag](headers.ETag)
  object Expect      extends HeaderTypeOf[headers.Expect](headers.Expect)
  object Expires     extends HeaderTypeOf[headers.Expires](headers.Expires)
  object Forwarded   extends HeaderTypeOf[headers.Forwarded](headers.Forwarded)
  object From        extends HeaderTypeOf[headers.From](headers.From)
  object Host        extends HeaderTypeOf[headers.Host](headers.Host)
  object IfMatch     extends HeaderTypeOf[headers.IfMatch](headers.IfMatch)
  object IfModifiedSince   extends HeaderTypeOf[headers.IfModifiedSince](headers.IfModifiedSince)
  object IfNoneMatch       extends HeaderTypeOf[headers.IfNoneMatch](headers.IfNoneMatch)
  object IfRange           extends HeaderTypeOf[headers.IfRange](headers.IfRange)
  object IfUnmodifiedSince extends HeaderTypeOf[headers.IfUnmodifiedSince](headers.IfUnmodifiedSince)
  object LastModified      extends HeaderTypeOf[headers.LastModified](headers.LastModified)
  object Link              extends HeaderTypeOf[headers.Link](headers.Link)
  object Location          extends HeaderTypeOf[headers.Location](headers.Location)
  object MaxForwards       extends HeaderTypeOf[headers.MaxForwards](headers.MaxForwards)
  object Origin            extends HeaderTypeOf[headers.Origin](headers.Origin)
  object Pragma            extends HeaderTypeOf[headers.Pragma](headers.Pragma)
  object ProxyAuthenticate extends HeaderTypeOf[headers.ProxyAuthenticate](headers.ProxyAuthenticate)
  object ProxyAuthorization extends HeaderTypeOf[headers.ProxyAuthorization](headers.ProxyAuthorization)
  object Range              extends HeaderTypeOf[headers.Range](headers.Range)
  object Referer            extends HeaderTypeOf[headers.Referer](headers.Referer)
  object RetryAfter         extends HeaderTypeOf[headers.RetryAfter](headers.RetryAfter)
  object SecWebSocketAccept extends HeaderTypeOf[headers.SecWebSocketAccept](headers.SecWebSocketAccept)
  object SecWebSocketExtensions
      extends HeaderTypeOf[headers.SecWebSocketExtensions](headers.SecWebSocketExtensions)
  object SecWebSocketKey      extends HeaderTypeOf[headers.SecWebSocketKey](headers.SecWebSocketKey)
  object SecWebSocketLocation extends HeaderTypeOf[headers.SecWebSocketLocation](headers.SecWebSocketLocation)
  object SecWebSocketOrigin   extends HeaderTypeOf[headers.SecWebSocketOrigin](headers.SecWebSocketOrigin)
  object SecWebSocketProtocol extends HeaderTypeOf[headers.SecWebSocketProtocol](headers.SecWebSocketProtocol)
  object SecWebSocketVersion  extends HeaderTypeOf[headers.SecWebSocketVersion](headers.SecWebSocketVersion)
  object Server               extends HeaderTypeOf[headers.Server](headers.Server)
  object SetCookie            extends HeaderTypeOf[headers.SetCookieHeader](headers.SetCookieHeader)
  object Te                   extends HeaderTypeOf[headers.Te](headers.Te)
  object Trailer              extends HeaderTypeOf[headers.Trailer](headers.Trailer)
  object TransferEncoding     extends HeaderTypeOf[headers.TransferEncoding](headers.TransferEncoding)
  object Upgrade              extends HeaderTypeOf[headers.Upgrade](headers.Upgrade)
  object UpgradeInsecureRequests
      extends HeaderTypeOf[headers.UpgradeInsecureRequests](headers.UpgradeInsecureRequests)
  object UserAgent      extends HeaderTypeOf[headers.UserAgent](headers.UserAgent)
  object Vary           extends HeaderTypeOf[headers.Vary](headers.Vary)
  object Via            extends HeaderTypeOf[headers.Via](headers.Via)
  object WWWAuthenticate extends HeaderTypeOf[headers.WWWAuthenticate](headers.WWWAuthenticate)
  object XFrameOptions  extends HeaderTypeOf[headers.XFrameOptions](headers.XFrameOptions)
  object XRequestedWith extends HeaderTypeOf[headers.XRequestedWith](headers.XRequestedWith)

  type Accept                       = headers.Accept
  type AcceptEncoding               = headers.AcceptEncoding
  type AcceptLanguage               = headers.AcceptLanguage
  type AcceptPatch                  = headers.AcceptPatch
  type AcceptRanges                 = headers.AcceptRanges
  type AccessControlAllowCredentials = headers.AccessControlAllowCredentials
  type AccessControlAllowHeaders    = headers.AccessControlAllowHeaders
  type AccessControlAllowMethods    = headers.AccessControlAllowMethods
  type AccessControlAllowOrigin     = headers.AccessControlAllowOrigin
  type AccessControlExposeHeaders   = headers.AccessControlExposeHeaders
  type AccessControlMaxAge          = headers.AccessControlMaxAge
  type AccessControlRequestHeaders  = headers.AccessControlRequestHeaders
  type AccessControlRequestMethod   = headers.AccessControlRequestMethod
  type Age                          = headers.Age
  type Allow                        = headers.Allow
  type Authorization                = headers.Authorization
  type CacheControl                 = headers.CacheControl
  type ClearSiteData                = headers.ClearSiteData
  type Connection                   = headers.Connection
  type ContentBase                  = headers.ContentBase
  type ContentDisposition           = headers.ContentDisposition
  type ContentEncoding              = headers.ContentEncoding
  type ContentLanguage              = headers.ContentLanguage
  type ContentLength                = headers.ContentLength
  type ContentLocation              = headers.ContentLocation
  type ContentMd5                   = headers.ContentMd5
  type ContentRange                 = headers.ContentRange
  type ContentSecurityPolicy        = headers.ContentSecurityPolicy
  type ContentTransferEncoding      = headers.ContentTransferEncoding
  type ContentType                  = headers.ContentType
  type Cookie                       = headers.CookieHeader
  type Date                         = headers.Date
  type DNT                          = headers.DNT
  type ETag                         = headers.ETag
  type Expect                       = headers.Expect
  type Expires                      = headers.Expires
  type Forwarded                    = headers.Forwarded
  type From                         = headers.From
  type Host                         = headers.Host
  type IfMatch                      = headers.IfMatch
  type IfModifiedSince              = headers.IfModifiedSince
  type IfNoneMatch                  = headers.IfNoneMatch
  type IfRange                      = headers.IfRange
  type IfUnmodifiedSince            = headers.IfUnmodifiedSince
  type LastModified                 = headers.LastModified
  type Link                         = headers.Link
  type Location                     = headers.Location
  type MaxForwards                  = headers.MaxForwards
  type Origin                       = headers.Origin
  type Pragma                       = headers.Pragma
  type ProxyAuthenticate            = headers.ProxyAuthenticate
  type ProxyAuthorization           = headers.ProxyAuthorization
  type Range                        = headers.Range
  type Referer                      = headers.Referer
  type RetryAfter                   = headers.RetryAfter
  type SecWebSocketAccept           = headers.SecWebSocketAccept
  type SecWebSocketExtensions       = headers.SecWebSocketExtensions
  type SecWebSocketKey              = headers.SecWebSocketKey
  type SecWebSocketLocation         = headers.SecWebSocketLocation
  type SecWebSocketOrigin           = headers.SecWebSocketOrigin
  type SecWebSocketProtocol         = headers.SecWebSocketProtocol
  type SecWebSocketVersion          = headers.SecWebSocketVersion
  type Server                       = headers.Server
  type SetCookie                    = headers.SetCookieHeader
  type Te                           = headers.Te
  type Trailer                      = headers.Trailer
  type TransferEncoding             = headers.TransferEncoding
  type Upgrade                      = headers.Upgrade
  type UpgradeInsecureRequests      = headers.UpgradeInsecureRequests
  type UserAgent                    = headers.UserAgent
  type Vary                         = headers.Vary
  type Via                          = headers.Via
  type WWWAuthenticate              = headers.WWWAuthenticate
  type XFrameOptions                = headers.XFrameOptions
  type XRequestedWith               = headers.XRequestedWith
}
