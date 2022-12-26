package zio.http.api

import zio.http.api.internal.RichTextCodec
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
import zio.http.model.{MediaType, Method}
import zio.{Chunk, Duration}

import java.time.ZonedDateTime
import scala.util.Try

object HeaderValueCodecs {

  final case class Protocol(protocol: String, version: Option[String])
  final case class HostAndPort(host: String, port: Option[Int])

  private val validStatusCodes = List(110, 111, 112, 113, 199, 214, 299)

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

  private val version: RichTextCodec[String]    =
    ((RichTextCodec.digits ~ RichTextCodec.literal(".") ~ RichTextCodec.digits) | RichTextCodec.string).transform(
      {
        case Left((major, _, minor)) => s"$major.$minor"
        case Right(value)            => value
      },
      {
        case s if s.contains(".") =>
          val Array(major, minor) = s.split('.')
          Left((Chunk.fromArray(major.toArray.map(_.toInt)), ".", Chunk.fromArray(minor.toArray.map(_.toInt))))
        case s                    => Right(s)
      },
    )
  private val protocol: RichTextCodec[Protocol] =
    (RichTextCodec.string | (RichTextCodec.string ~ RichTextCodec.literal("/") ~ version)).transform(
      {
        case Left(protocol)          => Protocol(protocol, None)
        case Right((protocol, _, v)) => Protocol(protocol, Some(v))
      },
      {
        case Protocol(protocol, Some(v)) => Right((protocol, "/", v))
        case Protocol(protocol, _)       => Left(protocol)
      },
    )

  private val optionalProtocol: RichTextCodec[Either[String, Protocol]] = version | protocol

  private val rawHostCodec: RichTextCodec[Either[String, (String, Int)]] =
    RichTextCodec.string | (RichTextCodec.string ~ RichTextCodec.colon.unit(':') ~ RichTextCodec.int)

  private val dayCodec: RichTextCodec[String] = RichTextCodec.enumeration(
    RichTextCodec.literalCI("Mon"),
    RichTextCodec.literalCI("Tue"),
    RichTextCodec.literalCI("Wed"),
    RichTextCodec.literalCI("Thu"),
    RichTextCodec.literalCI("Fri"),
    RichTextCodec.literalCI("Sat"),
    RichTextCodec.literalCI("Sun"),
  )

  private val monthCodec: RichTextCodec[String] = RichTextCodec.enumeration(
    RichTextCodec.literalCI("Jan"),
    RichTextCodec.literalCI("Feb"),
    RichTextCodec.literalCI("Mar"),
    RichTextCodec.literalCI("Apr"),
    RichTextCodec.literalCI("May"),
    RichTextCodec.literalCI("Jun"),
    RichTextCodec.literalCI("Jul"),
    RichTextCodec.literalCI("Aug"),
    RichTextCodec.literalCI("Sep"),
    RichTextCodec.literalCI("Oct"),
    RichTextCodec.literalCI("Nov"),
    RichTextCodec.literalCI("Dec"),
  )

  private val timeUnitCodec: RichTextCodec[Int] = (RichTextCodec.digit ~ RichTextCodec.digit).transform(
    { case (h1, h2) =>
      h1 * 10 + h2
    },
    { case h =>
      val h1 = h / 10
      val h2 = h % 10
      (h1, h2)
    },
  )

  private val timeCodec: RichTextCodec[String] =
    (timeUnitCodec ~ RichTextCodec.colon.unit(':') ~ timeUnitCodec ~ RichTextCodec.colon.unit(':') ~ timeUnitCodec)
      .transform(
        { case (h, m, s) =>
          s"$h:$m:$s"
        },
        { case s =>
          val Array(h, m, ss) = s.split(':')
          (h.toInt, m.toInt, ss.toInt)
        },
      )

  private val yearCodec: RichTextCodec[Int] =
    (RichTextCodec.digit ~ RichTextCodec.digit ~ RichTextCodec.digit ~ RichTextCodec.digit).transform(
      { case (y1, y2, y3, y4) =>
        y1 * 1000 + y2 * 100 + y3 * 10 + y4
      },
      { case y =>
        val y1 = y / 1000
        val y2 = (y % 1000) / 100
        val y3 = (y % 100) / 10
        val y4 = y  % 10
        (y1, y2, y3, y4)
      },
    )

  private val dayOfMonthCodec = timeUnitCodec

  private val zonedDateTimeCodec: RichTextCodec[ZonedDateTime] = (dayCodec ~ RichTextCodec.comma.unit(
    ',',
  ) ~ RichTextCodec.whitespaces ~ dayOfMonthCodec ~ RichTextCodec.whitespaces ~ monthCodec ~ RichTextCodec.whitespaces ~ yearCodec ~ RichTextCodec.whitespaces ~ timeCodec ~ RichTextCodec.whitespaces ~ RichTextCodec.string)
    .transform(
      { case (day, dayOfMonth, month, year, time, zone) =>
        ZonedDateTime.parse(s"$day, $dayOfMonth $month $year $time $zone")
      },
      { case zdt =>
        val day        = zdt.getDayOfWeek.toString
        val dayOfMonth = zdt.getDayOfMonth
        val month      = zdt.getMonth.toString
        val year       = zdt.getYear
        val time       = zdt.toLocalTime.toString
        val zone       = zdt.getZone.toString
        (day, dayOfMonth, month, year, time, zone)
      },
    )

  private val statusCodec: RichTextCodec[Int] =
    (RichTextCodec.digit ~ RichTextCodec.digit ~ RichTextCodec.digit).transform(
      { case (d1, d2, d3) =>
        d1 * 100 + d2 * 10 + d3
      },
      { case s =>
        val d1 = s / 100
        val d2 = (s % 100) / 10
        val d3 = s  % 10
        (d1, d2, d3)
      },
    )

  private val rawWarningCodec =
    statusCodec ~ RichTextCodec.whitespaces ~ RichTextCodec.string ~ RichTextCodec.whitespaces ~ RichTextCodec.string

  private val chunkCodec = RichTextCodec.literalCI("chunked")

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

  val pragmaCodec: RichTextCodec[Pragma] = RichTextCodec
    .literalCI("no-cache")
    .transform(
      {
        case "no-cache" => Pragma.PragmaNoCacheValue
        case _          => Pragma.InvalidPragmaValue
      },
      {
        case Pragma.PragmaNoCacheValue => "no-cache"
        case Pragma.InvalidPragmaValue => ""
      },
    )

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

  val transferEncodingCodec: RichTextCodec[TransferEncoding] = RichTextCodec.enumeration(chunkCodec, compressCodec, deflateCodec, gzipCodec)
    .replsep(RichTextCodec.comma.unit(',')).transform(
    (values: Chunk[String]) =>  TransferEncoding.MultipleEncodings(values.map { value =>
      value.trim match {
              case "chunked"  => TransferEncoding.ChunkedEncoding
              case "compress" => TransferEncoding.CompressEncoding
              case "deflate"  => TransferEncoding.DeflateEncoding
              case "gzip"     => TransferEncoding.GZipEncoding
              case _          => TransferEncoding.InvalidTransferEncoding
            }

      }),
    (values: TransferEncoding) => Chunk.fromArray(values.encoding.split(',').map(_.trim)),


  )

  val upgradeCodec: RichTextCodec[Upgrade] = protocol
    .replsep(RichTextCodec.comma.unit(','))
    .transform(
      values =>
        Upgrade.UpgradeProtocols(
          values.map(v => Upgrade.UpgradeValue(v.protocol, v.version.getOrElse(""))),
        ),
      {
        case Upgrade.UpgradeProtocols(values)        =>
          Chunk
            .fromArray(values.map(v => Protocol(v.protocol, if (v.version.isEmpty) None else Some(v.version))).toArray)
        case Upgrade.UpgradeValue(protocol, version) =>
          Chunk.single(Protocol(protocol, if (version.isEmpty) None else Some(version)))
        case Upgrade.InvalidUpgradeValue             =>
          Chunk.empty
      },
    )

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

  val varyCodec: RichTextCodec[Vary] =
    (RichTextCodec.star | (RichTextCodec.string.replsep(RichTextCodec.comma.unit(',')))).transform(
      {
        case Left(_)       => Vary.StarVary
        case Right(values) => Vary.HeadersVaryValue(values)
      },
      {
        case Vary.StarVary                 => Left('*')
        case Vary.HeadersVaryValue(values) => Right(values)
        case Vary.InvalidVaryValue         => Right(Chunk.empty)
      },
    )

  val viaCodec: RichTextCodec[Via] = (optionalProtocol ~ RichTextCodec.whitespaces ~ rawHostCodec)
    .replsep(RichTextCodec.comma.unit(','))
    .transform(
      values => {
        val data = values.map {
          case (Left(version), Left(pseudonym))       =>
            Via.DetailedValue(Via.ReceivedProtocol.Version(version), pseudonym)
          case (Left(version), Right((host, port)))   =>
            Via.DetailedValue(Via.ReceivedProtocol.Version(version), s"$host:$port")
          case (Right(protocol), Left(pseudonym))     =>
            Via.DetailedValue(
              Via.ReceivedProtocol.ProtocolVersion(protocol.protocol, protocol.version.getOrElse("")),
              pseudonym,
            )
          case (Right(protocol), Right((host, port))) =>
            Via.DetailedValue(
              Via.ReceivedProtocol.ProtocolVersion(protocol.protocol, protocol.version.getOrElse("")),
              s"$host:$port",
            )
        }
        Via.ViaValues(Chunk.fromArray(data.toArray))
      },
      values => parseViaValues(values),
    )

  private def parseViaValues(values: Via): Chunk[(Either[String, Protocol], Either[String, (String, Int)])] =
    values match {
      case Via.ViaValues(values)                                =>
        values.map {
          case Via.DetailedValue(Via.ReceivedProtocol.Version(version), pseudonym)                   =>
            if (pseudonym.contains(":")) {
              val hostPortArray = pseudonym.split(":")
              (Left(version), Right((hostPortArray(0), hostPortArray(1).toInt)))
            } else
              (Left(version), Left(pseudonym))
          case Via.DetailedValue(Via.ReceivedProtocol.ProtocolVersion(protocol, version), pseudonym) =>
            if (pseudonym.contains(":")) {
              val hostPortArray = pseudonym.split(":")
              (
                Right(Protocol(protocol, if (version.isEmpty) None else Some(version))),
                Right((hostPortArray(0), hostPortArray(1).toInt)),
              )
            } else
              (Right(Protocol(protocol, if (version.isEmpty) None else Some(version))), Left(pseudonym))

          case Via.DetailedValue(Via.ReceivedProtocol.InvalidProtocol, _) =>
            (Left(""), Left(""))
        }
      case Via.DetailedValue(receivedProtocol, hostOrPseudonym) =>
        receivedProtocol match {
          case Via.ReceivedProtocol.Version(version)                   =>
            Chunk.single((Left(version), Left(hostOrPseudonym)))
          case Via.ReceivedProtocol.ProtocolVersion(protocol, version) =>
            Chunk.single(
              (Right(Protocol(protocol, if (version.isEmpty) None else Some(version))), Left(hostOrPseudonym)),
            )
          case Via.ReceivedProtocol.InvalidProtocol                    => Chunk.empty
        }
      case Via.InvalidVia                                       => Chunk.empty
    }

  val warningCodec: RichTextCodec[Warning] = ((rawWarningCodec ~ zonedDateTimeCodec) | rawWarningCodec)
    .transform(
      {
        case Left((code, agent, text, zone)) =>
          if (validStatusCodes.contains(code)) Warning.WarningValue(code, agent, text, Some(zone))
          else Warning.InvalidWarning

        case Right((code, agent, text)) =>
          if (validStatusCodes.contains(code)) Warning.WarningValue(code, agent, text, None)
          else Warning.InvalidWarning
      },
      {
        case Warning.WarningValue(code, agent, text, Some(zonedDateTime)) =>
          Left((code, agent, text, zonedDateTime))
        case Warning.WarningValue(code, agent, text, None)                =>
          Right((code, agent, text))
        case Warning.InvalidWarning                                       =>
          Right((0, "", ""))
      },
    )

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
