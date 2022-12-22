package zio.http.api

import zio.{Chunk, Duration}
import zio.http.api.internal.RichTextCodec
import zio.http.model.{MediaType, Method}
import zio.http.model.headers.HeaderTypedValues.Accept.{InvalidAcceptValue, MediaTypeWithQFactor}
import zio.http.model.headers.HeaderTypedValues.DNT.{
  InvalidDNTValue,
  NotSpecifiedDNTValue,
  TrackingAllowedDNTValue,
  TrackingNotAllowedDNTValue,
}
import zio.http.model.headers.HeaderTypedValues.Expect.{ExpectValue, InvalidExpectValue}
import zio.http.model.headers.HeaderTypedValues._
import zio.http.model.headers.values.ContentSecurityPolicy

import scala.util.Try

object HeaderValueCodecs {

  private val httpMethodCodec: RichTextCodec[String] = RichTextCodec
    .enumeration(
      RichTextCodec.literalCI("GET"),
      RichTextCodec
        .literalCI("POST"),
      RichTextCodec
        .literalCI("PUT"),
      RichTextCodec
        .literalCI("DELETE"),
      RichTextCodec
        .literalCI("PATCH"),
      RichTextCodec
        .literalCI("HEAD"),
      RichTextCodec
        .literalCI("OPTIONS"),
      RichTextCodec
        .literalCI("TRACE"),
      RichTextCodec
        .literalCI("CONNECT"),
    )

  private val quantifier    = RichTextCodec.literalCI(";q=") ~ RichTextCodec.double
  // accept encoding
  private val gzipCodec     = RichTextCodec.literalCI("gzip")
  private val deflateCodec  = RichTextCodec.literalCI("deflate")
  private val brCodec       = RichTextCodec.literalCI("br")
  private val identityCodec = RichTextCodec.literalCI("identity")
  private val compressCodec = RichTextCodec.literalCI("compress")
  private val starCodec     = RichTextCodec.literalCI("*")

  private val gzipCodecComplete: RichTextCodec[(String, Option[Double])] =
    ((gzipCodec ~ quantifier) | gzipCodec).transform(f, g)
  private val deflateCodecComplete  = ((deflateCodec ~ quantifier) | deflateCodec).transform(f, g)
  private val brCodecComplete       = ((brCodec ~ quantifier) | brCodec).transform(f, g)
  private val identityCodecComplete =
    ((identityCodec ~ quantifier) | identityCodec).transform(f, g)
  private val compressCodecComplete = ((compressCodec ~ quantifier) | compressCodec).transform(f, g)
  private val starCodecComplete     = ((starCodec ~ quantifier) | starCodec).transform(f, g)

  private val acceptEncodingCodecAlt: RichTextCodec[(String, Option[Double])] =
    RichTextCodec.enumeration(
      gzipCodecComplete,
      deflateCodecComplete,
      brCodecComplete,
      identityCodecComplete,
      compressCodecComplete,
      starCodecComplete,
    )

  val acceptEncodingCodec: RichTextCodec[AcceptEncoding] =
    acceptEncodingCodecAlt
      .replsep(RichTextCodec.comma.unit(','))
      .transform(
        raw => {
          AcceptEncoding.MultipleEncodings(raw.map {
            case ("gzip", q)     => AcceptEncoding.GZipEncoding(q)
            case ("deflate", q)  => AcceptEncoding.DeflateEncoding(q)
            case ("br", q)       => AcceptEncoding.BrEncoding(q)
            case ("identity", q) => AcceptEncoding.IdentityEncoding(q)
            case ("compress", q) => AcceptEncoding.CompressEncoding(q)
            case ("*", q)        => AcceptEncoding.NoPreferenceEncoding(q)
            case _               => AcceptEncoding.InvalidEncoding
          })
        },
        ae => acceptEncodingToString(ae),
      )

  private def acceptEncodingToString(encoding: AcceptEncoding): Chunk[(String, Option[Double])] = encoding match {
    case AcceptEncoding.GZipEncoding(q)              => Chunk.single(("gzip", q))
    case AcceptEncoding.DeflateEncoding(q)           => Chunk.single(("deflate", q))
    case AcceptEncoding.BrEncoding(q)                => Chunk.single(("br", q))
    case AcceptEncoding.IdentityEncoding(q)          => Chunk.single(("identity", q))
    case AcceptEncoding.CompressEncoding(q)          => Chunk.single(("compress", q))
    case AcceptEncoding.NoPreferenceEncoding(q)      => Chunk.single(("*", q))
    case AcceptEncoding.InvalidEncoding              => Chunk.single(("Invalid header value", None))
    case AcceptEncoding.MultipleEncodings(encodings) =>
      encodings.flatMap(acceptEncodingToString)
  }

  private def f(e: Either[(String, (String, Double)), String]): (String, Option[Double]) =
    e match {
      case Left((name, (_, value))) => (name, Some(value))
      case Right(name)              => (name, None)
    }

  private def g(e: (String, Option[Double])): Either[(String, (String, Double)), String] = {
    e match {
      case (name, Some(value)) => Left((name, (";q=", value)))
      case (name, None)        => Right(name)
    }
  }

  // accept language
  private val lang                                          =
    RichTextCodec.filter(c => c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '-' || c == '*').repeat
  val langComplete: RichTextCodec[(String, Option[Double])] = ((lang ~ quantifier) | lang).transform(
    {
      case Left((name, (_, value))) => (name.mkString, Some(value))
      case Right(name)              => (name.mkString, None)
    },
    {
      case (name, Some(value)) => Left((Chunk.fromArray(name.toArray), (";q=", value)))
      case (name, None)        => Right(Chunk.fromArray(name.toArray))
    },
  )

  val acceptLanguageCodec: RichTextCodec[AcceptLanguage] = langComplete
    .replsep(RichTextCodec.comma.unit(','))
    .transform(
      raw => {
        println(raw)
        AcceptLanguage.AcceptedLanguages(raw.map { case (name, value) => AcceptLanguage.AcceptedLanguage(name, value) })
      },
      {
        case AcceptLanguage.AcceptedLanguages(values) =>
          values.map {
            case AcceptLanguage.AcceptedLanguage(name, value) => (name, value)
            case AcceptLanguage.AnyLanguage                   => ("*", None)
            case _                                            => ("*", None)

          }
        case _                                        => Chunk.empty
      },
    )

  // accept

  private val mediaTypeCodec: RichTextCodec[String] =
    RichTextCodec
      .filter(c => c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '/' || c == '*' || c == '-' || c == '+')
      .repeat
      .transform(
        _.mkString,
        s => Chunk.fromArray(s.toArray),
      )

  private val mediaTypeCodecWithQualifier: RichTextCodec[(String, Option[Double])] =
    (mediaTypeCodec ~ RichTextCodec
      .literalCI(";q=")
      .unit("") ~ RichTextCodec.double).transform(
      { case (name, value) =>
        (name.mkString, Some(value))
      },
      {
        case (name, Some(value)) => (name, value)
        case (name, None)        => (name, 0.0)

      },
    )

  val mediaTypeCodecAlternative: RichTextCodec[(String, Option[Double])] =
    (mediaTypeCodecWithQualifier | mediaTypeCodec)
      .transform(
        {
          case Left(value) => value
          case Right(name) => (name.mkString, None)
        },
        {
          case (name, Some(value)) => Left((name, Some(value)))
          case (name, None)        => Right(name)
        },
      )
  val acceptCodec: RichTextCodec[Accept]                                 =
    mediaTypeCodecAlternative
      .replsep(RichTextCodec.comma.unit(','))
      .transform(
        values => {
          values.foreach(println)
          val acceptHeaderValues = values.map { subValue =>
            MediaType
              .forContentType(subValue._1)
              .map(mt => MediaTypeWithQFactor(mt, extractQFactor(mt)))
              .getOrElse {
                MediaType
                  .parseCustomMediaType(subValue._1)
                  .map(mt => MediaTypeWithQFactor(mt, extractQFactor(mt)))
                  .orNull
              }
          }

          if (acceptHeaderValues.nonEmpty && acceptHeaderValues.length == acceptHeaderValues.count(_ == null))
            Accept.AcceptValue(acceptHeaderValues)
          else Accept.InvalidAcceptValue
        },
        {
          case Accept.AcceptValue(mimeTypes) =>
            mimeTypes.map { case MediaTypeWithQFactor(mime, maybeQFactor) =>
              (mime.toString, maybeQFactor)
            }
          case Accept.InvalidAcceptValue     => Chunk.empty
        },
      )

  private def extractQFactor(mediaType: MediaType): Option[Double] =
    mediaType.parameters.get("q").flatMap(qFactor => Try(qFactor.toDouble).toOption)

  val acceptPatchCodec: RichTextCodec[AcceptPatch] = ??? // Not easy

  val acceptRangesCodec: RichTextCodec[AcceptRanges] = RichTextCodec
    .enumeration(
      RichTextCodec.literalCI("bytes"),
      RichTextCodec
        .literalCI("none"),
    )
    .transform(
      {
        case "bytes" => AcceptRanges.Bytes
        case "none"  => AcceptRanges.None
        case _       => AcceptRanges.InvalidAcceptRanges
      },
      {
        case AcceptRanges.Bytes               => "bytes"
        case AcceptRanges.None                => "none"
        case AcceptRanges.InvalidAcceptRanges => ""
      },
    )

  val accessControlAllowCredentialsCodec: RichTextCodec[AccessControlAllowCredentials] = RichTextCodec
    .enumeration(
      RichTextCodec.literalCI("true"),
      RichTextCodec
        .literalCI("false"),
    )
    .transform(
      {
        case "true"  => AccessControlAllowCredentials.AllowCredentials
        case "false" => AccessControlAllowCredentials.DoNotAllowCredentials
        case _       => AccessControlAllowCredentials.DoNotAllowCredentials
      },
      {
        case AccessControlAllowCredentials.AllowCredentials => "true"
        case _                                              => "false"
      },
    )

  val accessControlAllowHeadersCodec: RichTextCodec[AccessControlAllowHeaders] = RichTextCodec
    .filter(c => c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '-' || c == '*')
    .repeat
    .replsep(RichTextCodec.comma.unit(','))
    .transform(
      values => AccessControlAllowHeaders.AccessControlAllowHeadersValue(values.map(_.mkString)),
      {
        case AccessControlAllowHeaders.AccessControlAllowHeadersValue(values) =>
          values.map(t => Chunk.fromArray(String.valueOf(t).toCharArray))
        case _                                                                => Chunk.empty
      },
    )

  val accessControlAllowMethodsCodec: RichTextCodec[AccessControlAllowMethods] = httpMethodCodec
    .replsep(RichTextCodec.comma.unit(','))
    .transform(
      values => AccessControlAllowMethods.AllowMethods(values.map(t => Method.fromString(t))),
      {
        case AccessControlAllowMethods.AllowMethods(values) =>
          values.map(_.toString())
        case _                                              => Chunk.empty
      },
    )

  val accessControlAllowOriginCodec: RichTextCodec[AccessControlAllowOrigin] = RichTextCodec
    .filter(c =>
      c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '-' || c == '*' || c == '.' || c == ':' || c == '/',
    )
    .repeat
    .transform(
      values => AccessControlAllowOrigin.ValidAccessControlAllowOrigin(values.mkString),
      {
        case AccessControlAllowOrigin.ValidAccessControlAllowOrigin(value) => Chunk.fromArray(value.toCharArray)
        case _                                                             => Chunk.empty
      },
    )

  val accessControlExposeHeadersCodec: RichTextCodec[AccessControlExposeHeaders] = RichTextCodec
    .filter(c => c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '-' || c == '*')
    .repeat
    .replsep(RichTextCodec.comma.unit(','))
    .transform(
      values =>
        values.map(_.mkString) match {
          case Chunk("*") => AccessControlExposeHeaders.All
          case Nil        => AccessControlExposeHeaders.NoHeaders
          case values     => AccessControlExposeHeaders.AccessControlExposeHeadersValue(values)
        },
      {
        case AccessControlExposeHeaders.AccessControlExposeHeadersValue(values) =>
          values.map(t => Chunk.fromArray(String.valueOf(t).toCharArray))
        case AccessControlExposeHeaders.All                                     => Chunk(Chunk('*'))
        case AccessControlExposeHeaders.NoHeaders                               => Chunk.empty
      },
    )

  val accessControlMaxAgeCodec: RichTextCodec[AccessControlMaxAge] = RichTextCodec.digits
    .transform(
      values => AccessControlMaxAge.ValidAccessControlMaxAge(Duration.fromSeconds(values.mkString.toLong)),
      {
        case AccessControlMaxAge.ValidAccessControlMaxAge(value) => Chunk.single(value.toSeconds.toInt)
        case _                                                   => Chunk.empty
      },
    )

  val accessControlRequestHeadersCodec: RichTextCodec[AccessControlRequestHeaders] = RichTextCodec
    .filter(c => c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '-' || c == '*')
    .repeat
    .replsep(RichTextCodec.comma.unit(','))
    .transform(
      values =>
        values.map(_.mkString) match {
          case Chunk("*") => AccessControlRequestHeaders.AllRequestHeaders
          case Nil        => AccessControlRequestHeaders.NoRequestHeaders
          case values     => AccessControlRequestHeaders.AccessControlRequestHeadersValue(values)
        },
      {
        case AccessControlRequestHeaders.AccessControlRequestHeadersValue(values) =>
          values.map(t => Chunk.fromArray(String.valueOf(t).toCharArray))
        case AccessControlRequestHeaders.AllRequestHeaders                        => Chunk(Chunk('*'))
        case AccessControlRequestHeaders.NoRequestHeaders                         => Chunk.empty
      },
    )

  val accessControlRequestMethodCodec: RichTextCodec[AccessControlRequestMethod] = httpMethodCodec
    .transform(
      values => AccessControlRequestMethod.RequestMethod(Method.fromString(values)),
      {
        case AccessControlRequestMethod.RequestMethod(value) => value.toString()
        case _                                               => ""
      },
    )

  val ageCodec: RichTextCodec[Age] = RichTextCodec.digits
    .transform(
      values => Age.AgeValue(values.mkString.toLong),
      {
        case Age.AgeValue(value) => Chunk.single(value.toInt)
        case _                   => Chunk.empty
      },
    )

  val allowCodec: RichTextCodec[Allow] = httpMethodCodec
    .replsep(RichTextCodec.comma.unit(','))
    .transform(
      values => Allow.AllowMethods(values.map(t => Method.fromString(t))),
      {
        case Allow.AllowMethods(values) => values.map(_.toString())
        case _                          => Chunk.empty
      },
    )

  val authenticationSchemeCodec: RichTextCodec[AuthenticationScheme] = ??? // Easy

  val authorizationCodec: RichTextCodec[Authorization] = ??? // Easy

  val cacheControlCodec: RichTextCodec[CacheControl] = ??? // Easy

  val connectionCodec: RichTextCodec[Connection] = RichTextCodec
    .enumeration(
      RichTextCodec.literalCI("close"),
      RichTextCodec
        .literalCI("keep-alive"),
      RichTextCodec.literal(""),
    )
    .transform(
      {
        case "close"      => Connection.Close
        case "keep-alive" => Connection.KeepAlive
        case _            => Connection.InvalidConnection
      },
      {
        case Connection.Close             => "close"
        case Connection.KeepAlive         => "keep-alive"
        case Connection.InvalidConnection => ""
      },
    )

  val contentBaseCodec: RichTextCodec[ContentBase] = ??? // Easy

  val contentDispositionCodec: RichTextCodec[ContentDisposition] = ??? // Easy

  val contentEncodingCodec: RichTextCodec[ContentEncoding] = ??? // Easy

  val contentLanguageCodec: RichTextCodec[ContentLanguage] = ??? // Easy

  val contentLengthCodec: RichTextCodec[ContentLength] = ??? // Easy

  val contentLocationCodec: RichTextCodec[ContentLocation] = ??? // Easy

  val contentMd5Codec: RichTextCodec[ContentMd5] = ??? // Easy

  val contentRangeCodec: RichTextCodec[ContentRange] = ??? // Easy

  val contentSecurityPolicyCodec: RichTextCodec[ContentSecurityPolicy] = ??? // hard

  val contentTransferEncodingCodec: RichTextCodec[ContentTransferEncoding] = ??? // Easy

  val contentTypeCodec: RichTextCodec[ContentType] = ??? // Easy

  val dateCodec: RichTextCodec[Date] = ??? // Easy

  val dntCodec: RichTextCodec[DNT] = (RichTextCodec.digit | RichTextCodec.literal("null")).transform(
    {
      case Left(1)  => TrackingNotAllowedDNTValue
      case Left(0)  => TrackingAllowedDNTValue
      case Left(_)  => InvalidDNTValue
      case Right(_) => NotSpecifiedDNTValue
    },
    {
      case NotSpecifiedDNTValue       => Right(null)
      case TrackingAllowedDNTValue    => Left(0)
      case TrackingNotAllowedDNTValue => Left(1)
      case InvalidDNTValue            => Right("invalid")
    },
  )

  val etagCodec: RichTextCodec[ETag] = ??? // Easy

  val expectCodec: RichTextCodec[Expect] = RichTextCodec
    .literalCI("100-continue")
    .transform(
      {
        case "100-continue" => ExpectValue
        case _              => InvalidExpectValue
      },
      {
        case e @ ExpectValue        => e.value
        case e @ InvalidExpectValue => e.value
      },
    )

  val expiresCodec: RichTextCodec[Expires] = ??? // Easy

  val fromCodec: RichTextCodec[From] = ??? // Easy

  val hostCodec: RichTextCodec[Host] = ??? // Easy

  val ifMatchCodec: RichTextCodec[IfMatch] = ??? // Easy

  val ifModifiedSinceCodec: RichTextCodec[IfModifiedSince] = ??? // Easy

  val ifNoneMatchCodec: RichTextCodec[IfNoneMatch] = ??? // Easy

  val ifRangeCodec: RichTextCodec[IfRange] = ??? // Easy

  val ifUnmodifiedSinceCodec: RichTextCodec[IfUnmodifiedSince] = ??? // Easy

  val lastModifiedCodec: RichTextCodec[LastModified] = ??? // Easy

  val locationCodec: RichTextCodec[Location] = ??? // Easy

  val maxForwardsCodec: RichTextCodec[MaxForwards] = ??? // Easy

  val originCodec: RichTextCodec[Origin] = ??? // Easy

  val pragmaCodec: RichTextCodec[Pragma] = ??? // Easy

  val proxyAuthenticateCodec: RichTextCodec[ProxyAuthenticate] = ??? // Easy

  val proxyAuthorizationCodec: RichTextCodec[ProxyAuthorization] = ??? // Easy

  val rangeCodec: RichTextCodec[Range] = ??? // Easy

  val refererCodec: RichTextCodec[Referer] = ??? // Easy

  val requestCookieCodec: RichTextCodec[RequestCookie] = ??? // Easy

  val responseCookieCodec: RichTextCodec[ResponseCookie] = ??? // Easy

  val retryAfterCodec: RichTextCodec[RetryAfter] = ??? // Easy

  val secWebSocketAcceptCodec: RichTextCodec[SecWebSocketAccept] = ??? // Easy

  val secWebSocketExtensionsCodec: RichTextCodec[SecWebSocketExtensions] = ??? // Easy

  val secWebSocketKeyCodec: RichTextCodec[SecWebSocketKey] = ??? // Easy

  val secWebSocketLocationCodec: RichTextCodec[SecWebSocketLocation] = ??? // Easy

  val secWebSocketOriginCodec: RichTextCodec[SecWebSocketOrigin] = ??? // Easy

  val secWebSocketProtocolCodec: RichTextCodec[SecWebSocketProtocol] = ??? // Easy

  val secWebSocketVersionCodec: RichTextCodec[SecWebSocketVersion] = (RichTextCodec.digit ~ RichTextCodec.digit)
    .transform(
      { case (a, b) =>
        SecWebSocketVersion.Version(a * 10 + b)
      },
      {
        case SecWebSocketVersion.Version(value) =>
          (value / 10, value % 10)
        case SecWebSocketVersion.InvalidVersion =>
          (0, 0)
      },
    )

  val serverCodec: RichTextCodec[Server] = RichTextCodec.string.transform(
    value => Server.ServerName(value),
    {
      case Server.ServerName(value) => value
      case _                        => ""
    },
  )

  val teCodec: RichTextCodec[Te] = ??? // Easy

  val trailerCodec: RichTextCodec[Trailer] = RichTextCodec
    .filter(c => c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '_')
    .repeat
    .transform(
      values => Trailer.TrailerValue(values.mkString),
      {
        case Trailer.TrailerValue(value) =>
          Chunk.fromArray(value.toCharArray)
        case Trailer.InvalidTrailerValue =>
          Chunk.empty
      },
    )

  val transferEncodingCodec: RichTextCodec[TransferEncoding] = ??? // Easy

  val upgradeCodec: RichTextCodec[Upgrade] = ??? // Easy

  val upgradeInsecureRequestsCodec: RichTextCodec[UpgradeInsecureRequests] = RichTextCodec
    .literal("1")
    .transform(
      {
        case "1" => UpgradeInsecureRequests.UpgradeInsecureRequests
        case _   => UpgradeInsecureRequests.InvalidUpgradeInsecureRequests
      },
      {
        case UpgradeInsecureRequests.UpgradeInsecureRequests        => "1"
        case UpgradeInsecureRequests.InvalidUpgradeInsecureRequests => "invalid"
      },
    )

  val userAgentCodec: RichTextCodec[UserAgent] = ??? // Easy

  val varyCodec: RichTextCodec[Vary] = ??? // Easy

  val viaCodec: RichTextCodec[Via] = ??? // Easy

  val warningCodec: RichTextCodec[Warning] = ??? // Easy

  val wwwAuthenticateCodec: RichTextCodec[WWWAuthenticate] = ??? // Easy

  val xFrameOptionsCodec: RichTextCodec[XFrameOptions] = RichTextCodec
    .enumeration(
      RichTextCodec
        .literalCI("deny"),
      RichTextCodec.literalCI("sameorigin"),
    )
    .transform(
      {
        case "deny"       => XFrameOptions.Deny
        case "sameorigin" =>
          XFrameOptions.SameOrigin
        case _            => XFrameOptions.Invalid
      },
      {
        case XFrameOptions.Deny       => "deny"
        case XFrameOptions.SameOrigin => "sameorigin"
        case XFrameOptions.Invalid    => ""
      },
    )

  val xRequestedWithCodec: RichTextCodec[XRequestedWith] = RichTextCodec.string.transform(
    { case value =>
      XRequestedWith.XMLHttpRequest(value)
    },
    { case XRequestedWith.XMLHttpRequest(value) =>
      value
    },
  )

}
