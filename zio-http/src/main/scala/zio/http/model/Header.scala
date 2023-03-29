package zio.http.model

import java.net.URI
import java.nio.charset.{Charset, StandardCharsets, UnsupportedCharsetException}
import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import java.util.Base64

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.control.NonFatal
import scala.util.matching.Regex
import scala.util.{Either, Failure, Success, Try}

import zio._

import zio.http._

sealed trait Header {
  type Self <: Header
  def self: Self

  def headerType: Header.HeaderType.Typed[Self]
  def headerName: String    = headerType.name
  def renderedValue: String = headerType.render(self)

  private[http] def headerNameAsCharSequence: CharSequence    = headerName
  private[http] def renderedValueAsCharSequence: CharSequence = renderedValue

  lazy val untyped: Header.Custom = Header.Custom(headerName, renderedValue)
}

object Header {
  sealed trait HeaderType {
    type HeaderValue

    def name: String

    def parse(value: String): Either[String, HeaderValue]

    def render(value: HeaderValue): String
  }

  object HeaderType {
    type Typed[HV] = HeaderType { type HeaderValue = HV }
  }

  final case class Custom(customName: CharSequence, value: CharSequence) extends Header {
    override type Self = Custom
    override def self: Self = this

    override def headerType: HeaderType.Typed[Custom] = new Header.HeaderType {
      override type HeaderValue = Custom

      override lazy val name: String = self.customName.toString

      override def parse(value: String): Either[String, HeaderValue] = Right(Custom(self.customName, value))

      override def render(value: HeaderValue): String = value.value.toString
    }

    private[http] override def headerNameAsCharSequence: CharSequence    = customName
    private[http] override def renderedValueAsCharSequence: CharSequence = value

    override def hashCode(): Int = {
      var h       = 0
      val kLength = customName.length()
      var i       = 0
      while (i < kLength) {
        h = 17 * h + customName.charAt(i)
        i = i + 1
      }
      i = 0
      val vLength = value.length()
      while (i < vLength) {
        h = 17 * h + value.charAt(i)
        i = i + 1
      }
      h
    }

    override def equals(that: Any): Boolean = {
      that match {
        case Custom(k, v) =>
          def eqs(l: CharSequence, r: CharSequence): Boolean = {
            if (l.length() != r.length()) false
            else {
              var i     = 0
              var equal = true

              while (i < l.length()) {
                if (l.charAt(i) != r.charAt(i)) {
                  equal = false
                  i = l.length()
                }
                i = i + 1
              }
              equal
            }
          }

          eqs(self.customName, k) && eqs(self.value, v)

        case _ => false
      }
    }

    override def toString(): String = (customName, value).toString()
  }

  final case class Accept(mimeTypes: NonEmptyChunk[Accept.MediaTypeWithQFactor]) extends Header {
    override type Self = Accept
    override def self: Self                           = this
    override def headerType: HeaderType.Typed[Accept] = Accept
  }

  object Accept extends HeaderType {
    override type HeaderValue = Accept

    override def name: String = "accept"

    /**
     * The Accept header value one or more MIME types optionally weighed with
     * quality factor.
     */
    final case class MediaTypeWithQFactor(mediaType: MediaType, qFactor: Option[Double])

    def apply(mediaType: MediaType, qFactor: Option[Double]): Accept =
      Accept(NonEmptyChunk(MediaTypeWithQFactor(mediaType, qFactor)))

    def apply(first: MediaType, rest: MediaType*): Accept =
      Accept(NonEmptyChunk(first, rest: _*).map(MediaTypeWithQFactor(_, None)))

    def apply(first: MediaTypeWithQFactor, rest: MediaTypeWithQFactor*): Accept =
      Accept(NonEmptyChunk(first, rest: _*))

    def parse(value: String): Either[String, Accept] = {
      val acceptHeaderValues =
        Chunk
          .fromArray(
            value
              .split(",")
              .map(_.trim)
              .map { subValue =>
                MediaType
                  .forContentType(subValue)
                  .map(mt => MediaTypeWithQFactor(mt, extractQFactor(mt)))
                  .getOrElse {
                    MediaType
                      .parseCustomMediaType(subValue)
                      .map(mt => MediaTypeWithQFactor(mt, extractQFactor(mt)))
                      .orNull
                  }
              },
          )

      val valid = acceptHeaderValues.filter(_ != null)
      if (valid.size != acceptHeaderValues.size) Left("Invalid Accept header")
      else NonEmptyChunk.fromChunk(acceptHeaderValues).toRight("Invalid Accept header").map(Accept(_))
    }

    def render(header: Accept): String =
      header.mimeTypes.map { case MediaTypeWithQFactor(mime, maybeQFactor) =>
        s"${mime.fullType}${maybeQFactor.map(qFactor => s";q=$qFactor").getOrElse("")}"
      }.mkString(", ")

    private def extractQFactor(mediaType: MediaType): Option[Double] =
      mediaType.parameters.get("q").flatMap(qFactor => Try(qFactor.toDouble).toOption)
  }

  /**
   * Represents an AcceptEncoding header value.
   */
  sealed trait AcceptEncoding extends Header {
    override type Self = AcceptEncoding
    override def self: Self                                   = this
    override def headerType: HeaderType.Typed[AcceptEncoding] = AcceptEncoding

    val raw: String
  }

  object AcceptEncoding extends HeaderType {
    override type HeaderValue = AcceptEncoding

    override def name: String = "accept-encoding"

    /**
     * A compression format that uses the Brotli algorithm.
     */
    final case class Br(weight: Option[Double] = None) extends AcceptEncoding {
      override val raw: String = "br"
    }

    /**
     * A compression format that uses the Lempel-Ziv-Welch (LZW) algorithm.
     */
    final case class Compress(weight: Option[Double] = None) extends AcceptEncoding {
      override val raw: String = "compress"
    }

    /**
     * A compression format that uses the zlib structure with the deflate
     * compression algorithm.
     */
    final case class Deflate(weight: Option[Double] = None) extends AcceptEncoding {
      override val raw: String = "deflate"
    }

    /**
     * A compression format that uses the Lempel-Ziv coding (LZ77) with a 32-bit
     * CRC.
     */
    final case class GZip(weight: Option[Double] = None) extends AcceptEncoding {
      override val raw: String = "gzip"
    }

    /**
     * Indicates the identity function (that is, without modification or
     * compression). This value is always considered as acceptable, even if
     * omitted.
     */
    final case class Identity(weight: Option[Double] = None) extends AcceptEncoding {
      override val raw: String = "identity"
    }

    /**
     * Maintains a chunk of AcceptEncoding values.
     */
    final case class Multiple(encodings: NonEmptyChunk[AcceptEncoding]) extends AcceptEncoding {
      override val raw: String = encodings.map(_.raw).mkString(",")
    }

    /**
     * Matches any content encoding not already listed in the header. This is
     * the default value if the header is not present.
     */
    final case class NoPreference(weight: Option[Double]) extends AcceptEncoding {
      override val raw: String = "*"
    }

    private def identifyEncodingFull(raw: String): Option[AcceptEncoding] = {
      val index = raw.indexOf(";q=")
      if (index == -1)
        identifyEncoding(raw)
      else {
        identifyEncoding(raw.substring(0, index), weight = Try(raw.substring(index + 3).toDouble).toOption)
      }
    }

    private def identifyEncoding(raw: String, weight: Option[Double] = None): Option[AcceptEncoding] = {
      raw.trim match {
        case "br"       => Some(Br(weight))
        case "compress" => Some(Compress(weight))
        case "deflate"  => Some(Deflate(weight))
        case "gzip"     => Some(GZip(weight))
        case "identity" => Some(Identity(weight))
        case "*"        => Some(NoPreference(weight))
        case _          => None
      }
    }

    def apply(first: AcceptEncoding, rest: AcceptEncoding*): AcceptEncoding =
      Multiple(NonEmptyChunk(first, rest: _*))

    def parse(value: String): Either[String, AcceptEncoding] = {
      val index = value.indexOf(",")

      @tailrec def loop(
        value: String,
        index: Int,
        acc: Chunk[AcceptEncoding],
      ): Either[String, Chunk[AcceptEncoding]] = {
        if (index == -1) {
          identifyEncodingFull(value) match {
            case Some(encoding) =>
              Right(acc :+ encoding)
            case None           =>
              Left(s"Invalid accept encoding ($value)")
          }
        } else {
          val valueChunk = value.substring(0, index)
          val remaining  = value.substring(index + 1)
          val nextIndex  = remaining.indexOf(",")

          identifyEncodingFull(valueChunk) match {
            case Some(encoding) =>
              loop(
                remaining,
                nextIndex,
                acc :+ encoding,
              )
            case None           =>
              Left(s"Invalid accept encoding ($valueChunk)")
          }
        }
      }

      if (index == -1)
        identifyEncodingFull(value) match {
          case Some(encoding) => Right(encoding)
          case None           => Left(s"Invalid accept encoding ($value)")
        }
      else
        loop(value, index, Chunk.empty[AcceptEncoding]).flatMap { encodings =>
          NonEmptyChunk.fromChunk(encodings) match {
            case Some(value) => Right(Multiple(value))
            case None        => Left(s"Invalid accept encoding ($value)")
          }
        }
    }

    def render(encoding: AcceptEncoding): String =
      encoding match {
        case b @ Br(weight)           => weight.fold(b.raw)(value => s"${b.raw};q=$value")
        case c @ Compress(weight)     => weight.fold(c.raw)(value => s"${c.raw};q=$value")
        case d @ Deflate(weight)      => weight.fold(d.raw)(value => s"${d.raw};q=$value")
        case g @ GZip(weight)         => weight.fold(g.raw)(value => s"${g.raw};q=$value")
        case i @ Identity(weight)     => weight.fold(i.raw)(value => s"${i.raw};q=$value")
        case Multiple(encodings)      => encodings.map(render).mkString(",")
        case n @ NoPreference(weight) => weight.fold(n.raw)(value => s"${n.raw};q=$value")
      }

  }

  /**
   * The Accept-Language request HTTP header indicates the natural language and
   * locale that the client prefers.
   */
  sealed trait AcceptLanguage extends Header {
    override type Self = AcceptLanguage
    override def self: Self                                   = this
    override def headerType: HeaderType.Typed[AcceptLanguage] = AcceptLanguage
  }

  object AcceptLanguage extends HeaderType {
    override type HeaderValue = AcceptLanguage

    override def name: String = "accept-language"

    case class Single(language: String, weight: Option[Double]) extends AcceptLanguage

    case class Multiple(languages: NonEmptyChunk[AcceptLanguage]) extends AcceptLanguage

    case object Any extends AcceptLanguage

    def parse(value: String): Either[String, AcceptLanguage] = {
      @tailrec def loop(index: Int, value: String, acc: Chunk[AcceptLanguage]): Chunk[AcceptLanguage] = {
        if (index == -1) acc :+ parseAcceptedLanguage(value.trim)
        else {
          val valueChunk     = value.substring(0, index)
          val valueRemaining = value.substring(index + 1)
          val newIndex       = valueRemaining.indexOf(',')
          loop(
            newIndex,
            valueRemaining,
            acc :+ parseAcceptedLanguage(valueChunk.trim),
          )
        }
      }

      if (validCharacters.findFirstIn(value).isEmpty) Left("Accept-Language contains invalid characters")
      else if (value.isEmpty) Left("Accept-Language cannot be empty")
      else if (value == "*") Right(AcceptLanguage.Any)
      else
        NonEmptyChunk.fromChunk(loop(value.indexOf(','), value, Chunk.empty)) match {
          case Some(value) => Right(Multiple(value))
          case None        => Left("Accept-Language cannot be empty")
        }
    }

    def render(acceptLanguage: AcceptLanguage): String =
      acceptLanguage match {
        case Single(language, weight) =>
          val weightString = weight match {
            case Some(w) => s";q=$w"
            case None    => ""
          }
          s"$language$weightString"
        case Multiple(languages)      => languages.map(render).mkString(",")
        case Any                      => "*"
      }

    /**
     * Allowed characters in the header are 0-9, A-Z, a-z, space or *,-.;=
     */
    private val validCharacters: Regex = "^[0-9a-zA-Z *,\\-.;=]+$".r

    private def parseAcceptedLanguage(value: String): AcceptLanguage = {
      val weightIndex = value.indexOf(";q=")
      if (weightIndex != -1) {
        val language = value.substring(0, weightIndex)
        val weight   = value.substring(weightIndex + 3)
        Single(
          language,
          Try(weight.toDouble).toOption
            .filter(w => w >= 0.0 && w <= 1.0),
        )
      } else Single(value, None)
    }
  }

  /**
   * The Accept-Patch response HTTP header advertises which media-type the
   * server is able to understand in a PATCH request.
   */
  final case class AcceptPatch(mediaTypes: NonEmptyChunk[MediaType]) extends Header {
    override type Self = AcceptPatch
    override def self: Self                                = this
    override def headerType: HeaderType.Typed[AcceptPatch] = AcceptPatch
  }

  object AcceptPatch extends HeaderType {
    override type HeaderValue = AcceptPatch

    override def name: String = "accept-patch"

    def parse(value: String): Either[String, AcceptPatch] =
      if (value.nonEmpty) {
        val parsedMediaTypes = Chunk
          .fromArray(
            value
              .split(",")
              .map(mediaTypeStr =>
                MediaType
                  .forContentType(mediaTypeStr)
                  .getOrElse(
                    MediaType
                      .parseCustomMediaType(mediaTypeStr)
                      .orNull,
                  ),
              ),
          )
          .filter(_ != null)

        NonEmptyChunk.fromChunk(parsedMediaTypes) match {
          case Some(value) => Right(AcceptPatch(value))
          case None        => Left("Invalid Accept-Patch header")
        }
      } else Left("Accept-Patch header cannot be empty")

    def render(acceptPatch: AcceptPatch): String =
      acceptPatch.mediaTypes.map(_.fullType).mkString(",")

  }

  /**
   * The Accept-Ranges HTTP response header is a marker used by the server to
   * advertise its support for partial requests from the client for file
   * downloads. The value of this field indicates the unit that can be used to
   * define a range. By default the RFC 7233 specification supports only 2
   * possible values.
   */
  sealed trait AcceptRanges extends Header {
    override type Self = AcceptRanges
    override def self: Self                                 = this
    override def headerType: HeaderType.Typed[AcceptRanges] = AcceptRanges

    val encodedName: String
  }

  object AcceptRanges extends HeaderType {
    override type HeaderValue = AcceptRanges
    override def name: String = "accept-ranges"

    case object Bytes extends AcceptRanges {
      override val encodedName = "bytes"
    }

    case object None extends AcceptRanges {
      override val encodedName = "none"
    }

    def parse(value: String): Either[String, AcceptRanges] =
      value match {
        case Bytes.encodedName => Right(Bytes)
        case None.encodedName  => Right(None)
        case _                 => Left("Invalid Accept-Ranges header")
      }

    def render(acceptRangers: AcceptRanges): String =
      acceptRangers.encodedName
  }

  sealed trait AccessControlAllowCredentials extends Header {
    override type Self = AccessControlAllowCredentials
    override def self: Self                                                  = this
    override def headerType: HeaderType.Typed[AccessControlAllowCredentials] = AccessControlAllowCredentials
  }

  object AccessControlAllowCredentials extends HeaderType {
    override type HeaderValue = AccessControlAllowCredentials

    override def name: String = "access-control-allow-credentials"

    /**
     * The Access-Control-Allow-Credentials header is sent in response to a
     * preflight request which includes the Access-Control-Request-Headers to
     * indicate whether or not the actual request can be made using credentials.
     */
    case object Allow extends AccessControlAllowCredentials

    /**
     * The Access-Control-Allow-Credentials header is not sent in response to a
     * preflight request.
     */
    case object DoNotAllow extends AccessControlAllowCredentials

    def allow(value: Boolean): AccessControlAllowCredentials =
      value match {
        case true  => Allow
        case false => DoNotAllow
      }

    def parse(value: String): Either[String, AccessControlAllowCredentials] =
      Right {
        value match {
          case "true"  => Allow
          case "false" => DoNotAllow
          case _       => DoNotAllow
        }
      }

    def render(
      accessControlAllowCredentials: AccessControlAllowCredentials,
    ): String =
      accessControlAllowCredentials match {
        case Allow      => "true"
        case DoNotAllow => "false"
      }
  }

  sealed trait AccessControlAllowHeaders extends Header {
    override type Self = AccessControlAllowHeaders
    override def self: Self                                              = this
    override def headerType: HeaderType.Typed[AccessControlAllowHeaders] = AccessControlAllowHeaders
  }

  /**
   * The Access-Control-Allow-Headers response header is used in response to a
   * preflight request which includes the Access-Control-Request-Headers to
   * indicate which HTTP headers can be used during the actual request.
   */
  object AccessControlAllowHeaders extends HeaderType {
    override type HeaderValue = AccessControlAllowHeaders

    override def name: String = "access-control-allow-headers"

    final case class Some(values: NonEmptyChunk[String]) extends AccessControlAllowHeaders

    case object All extends AccessControlAllowHeaders

    case object None extends AccessControlAllowHeaders

    def apply(headers: String*) =
      NonEmptyChunk.fromIterableOption(headers) match {
        case scala.Some(value) => Some(value)
        case scala.None        => None
      }

    def parse(value: String): Either[String, AccessControlAllowHeaders] =
      Right {
        value match {
          case ""          => None
          case "*"         => All
          case headerNames =>
            NonEmptyChunk.fromChunk(
              Chunk.fromArray(
                headerNames
                  .split(",")
                  .map(_.trim),
              ),
            ) match {
              case scala.Some(value) => Some(value)
              case scala.None        => None
            }
        }
      }

    def render(accessControlAllowHeaders: AccessControlAllowHeaders): String =
      accessControlAllowHeaders match {
        case Some(value) => value.mkString(", ")
        case All         => "*"
        case None        => ""
      }

  }

  sealed trait AccessControlAllowMethods extends Header {
    override type Self = AccessControlAllowMethods
    override def self: Self                                              = this
    override def headerType: HeaderType.Typed[AccessControlAllowMethods] = AccessControlAllowMethods

    def contains(method: Method): Boolean =
      this match {
        case AccessControlAllowMethods.All           => true
        case AccessControlAllowMethods.Some(methods) => methods.contains(method)
        case AccessControlAllowMethods.None          => false
      }
  }

  object AccessControlAllowMethods extends HeaderType {
    override type HeaderValue = AccessControlAllowMethods

    override def name: String = "access-control-allow-methods"

    final case class Some(methods: NonEmptyChunk[Method]) extends AccessControlAllowMethods

    case object All extends AccessControlAllowMethods

    case object None extends AccessControlAllowMethods

    def apply(methods: Method*): AccessControlAllowMethods =
      NonEmptyChunk.fromIterableOption(methods) match {
        case scala.Some(value) => Some(value)
        case scala.None        => None
      }

    def parse(value: String): Either[String, AccessControlAllowMethods] = {
      Right {
        value match {
          case ""          => None
          case "*"         => All
          case methodNames =>
            NonEmptyChunk.fromChunk(
              Chunk.fromArray(
                methodNames
                  .split(",")
                  .map(_.trim)
                  .map(Method.fromString),
              ),
            ) match {
              case scala.Some(value) => Some(value)
              case scala.None        => None
            }
        }
      }
    }

    def render(accessControlAllowMethods: AccessControlAllowMethods): String =
      accessControlAllowMethods match {
        case Some(methods) => methods.map(_.toString()).mkString(", ")
        case All           => "*"
        case None          => ""
      }
  }

  /**
   * The Access-Control-Allow-Origin response header indicates whether the
   * response can be shared with requesting code from the given origin.
   *
   * For requests without credentials, the literal value "*" can be specified as
   * a wildcard; the value tells browsers to allow requesting code from any
   * origin to access the resource. Attempting to use the wildcard with
   * credentials results in an error.
   *
   * <origin> Specifies an origin. Only a single origin can be specified. If the
   * server supports clients from multiple origins, it must return the origin
   * for the specific client making the request.
   *
   * null Specifies the origin "null".
   */
  sealed trait AccessControlAllowOrigin extends Header {
    override type Self = AccessControlAllowOrigin
    override def self: Self                                             = this
    override def headerType: HeaderType.Typed[AccessControlAllowOrigin] = AccessControlAllowOrigin
  }

  object AccessControlAllowOrigin extends HeaderType {
    override type HeaderValue = AccessControlAllowOrigin

    override def name: String = "access-control-allow-origin"

    final case class Specific(origin: Origin) extends AccessControlAllowOrigin

    case object All extends AccessControlAllowOrigin

    def apply(scheme: String, host: String, port: Option[Int] = None): AccessControlAllowOrigin =
      Specific(Origin(scheme, host, port))

    def parse(value: String): Either[String, AccessControlAllowOrigin] = {
      if (value == "*") {
        Right(AccessControlAllowOrigin.All)
      } else {
        Origin.parse(value).map { origin =>
          AccessControlAllowOrigin.Specific(origin)
        }
      }
    }

    def render(accessControlAllowOrigin: AccessControlAllowOrigin): String =
      accessControlAllowOrigin match {
        case Specific(origin) => Origin.render(origin)
        case All              => "*"
      }
  }

  /**
   * The Access-Control-Expose-Headers response header allows a server to
   * indicate which response headers should be made available to scripts running
   * in the browser, in response to a cross-origin request.
   */
  sealed trait AccessControlExposeHeaders extends Header {
    override type Self = AccessControlExposeHeaders
    override def self: Self                                               = this
    override def headerType: HeaderType.Typed[AccessControlExposeHeaders] = AccessControlExposeHeaders
  }

  object AccessControlExposeHeaders extends HeaderType {
    override type HeaderValue = AccessControlExposeHeaders

    override def name: String = "access-control-expose-headers"

    final case class Some(values: NonEmptyChunk[CharSequence]) extends AccessControlExposeHeaders

    case object All extends AccessControlExposeHeaders

    case object None extends AccessControlExposeHeaders

    def parse(value: String): Either[String, AccessControlExposeHeaders] = {
      Right {
        value match {
          case ""          => None
          case "*"         => All
          case headerNames =>
            NonEmptyChunk.fromChunk(
              Chunk.fromArray(
                headerNames
                  .split(",")
                  .map(_.trim),
              ),
            ) match {
              case scala.Some(value) => Some(value)
              case scala.None        => None
            }
        }
      }
    }

    def render(accessControlExposeHeaders: AccessControlExposeHeaders): String =
      accessControlExposeHeaders match {
        case Some(value) => value.mkString(", ")
        case All         => "*"
        case None        => ""
      }

  }

  /**
   * The Access-Control-Max-Age response header indicates how long the results
   * of a preflight request (that is the information contained in the
   * Access-Control-Allow-Methods and Access-Control-Allow-Headers headers) can
   * be cached.
   *
   * Maximum number of seconds the results can be cached, as an unsigned
   * non-negative integer. Firefox caps this at 24 hours (86400 seconds).
   * Chromium (prior to v76) caps at 10 minutes (600 seconds). Chromium
   * (starting in v76) caps at 2 hours (7200 seconds). The default value is 5
   * seconds.
   */
  final case class AccessControlMaxAge(duration: Duration) extends Header {
    override type Self = AccessControlMaxAge
    override def self: Self                                        = this
    override def headerType: HeaderType.Typed[AccessControlMaxAge] = AccessControlMaxAge
  }

  object AccessControlMaxAge extends HeaderType {
    override type HeaderValue = AccessControlMaxAge

    override def name: String = "access-control-max-age"

    def parse(seconds: String): Either[String, AccessControlMaxAge] =
      Try(seconds.toLong).toOption.flatMap { long =>
        if (long > -1) Some(AccessControlMaxAge(long.seconds))
        else None
      }.toRight("Invalid Access-Control-Max-Age header value")

    def render(accessControlMaxAge: AccessControlMaxAge): String = {
      accessControlMaxAge.duration.getSeconds.toString
    }
  }

  final case class AccessControlRequestHeaders(values: NonEmptyChunk[String]) extends Header {
    override type Self = AccessControlRequestHeaders
    override def self: Self                                                = this
    override def headerType: HeaderType.Typed[AccessControlRequestHeaders] = AccessControlRequestHeaders
  }

  /**
   * The Access-Control-Request-Headers request header is used by browsers when
   * issuing a preflight request to let the server know which HTTP headers the
   * client might send when the actual request is made (such as with
   * setRequestHeader()). The complementary server-side header of
   * Access-Control-Allow-Headers will answer this browser-side header.
   */
  object AccessControlRequestHeaders extends HeaderType {
    override type HeaderValue = AccessControlRequestHeaders

    override def name: String = "access-control-request-headers"

    def parse(values: String): Either[String, AccessControlRequestHeaders] = {
      NonEmptyChunk.fromChunk(Chunk.fromArray(values.trim().split(",")).filter(_.nonEmpty)) match {
        case None     => Left("AccessControlRequestHeaders cannot be empty")
        case Some(xs) => Right(AccessControlRequestHeaders(xs))
      }
    }

    def render(headers: AccessControlRequestHeaders): String =
      headers.values.mkString(",")
  }

  final case class AccessControlRequestMethod(method: Method) extends Header {
    override type Self = AccessControlRequestMethod
    override def self: Self                                               = this
    override def headerType: HeaderType.Typed[AccessControlRequestMethod] = AccessControlRequestMethod
  }

  object AccessControlRequestMethod extends HeaderType {
    override type HeaderValue = AccessControlRequestMethod

    override def name: String = "access-control-request-method"

    def parse(value: String): Either[String, AccessControlRequestMethod] = {
      val method = Method.fromString(value)
      if (method == Method.CUSTOM(value)) Left(s"Invalid Access-Control-Request-Method")
      else Right(AccessControlRequestMethod(method))
    }

    def render(requestMethod: AccessControlRequestMethod): String =
      requestMethod.method.name
  }

  /**
   * Age header value.
   */
  final case class Age(duration: Duration) extends Header {
    override type Self = Age
    override def self: Self                        = this
    override def headerType: HeaderType.Typed[Age] = Age
  }

  object Age extends HeaderType {
    override type HeaderValue = Age

    override def name: String = "age"

    def parse(value: String): Either[String, Age] =
      Try(value.trim.toInt) match {
        case Failure(_)                  => Left(s"Invalid Age")
        case Success(value) if value > 0 => Right(Age(value.seconds))
        case Success(_)                  => Left(s"Negative Age")
      }

    def render(age: Age): String =
      age.duration.getSeconds.toString
  }

  /**
   * The Allow header must be sent if the server responds with a 405 Method Not
   * Allowed status code to indicate which request methods can be used.
   */
  final case class Allow(methods: NonEmptyChunk[Method]) extends Header {
    override type Self = Allow
    override def self: Self                          = this
    override def headerType: HeaderType.Typed[Allow] = Allow
  }

  object Allow extends HeaderType {
    override type HeaderValue = Allow

    override def name: String = "allow"

    val OPTIONS: Allow = Allow(NonEmptyChunk.single(Method.OPTIONS))
    val GET: Allow     = Allow(NonEmptyChunk.single(Method.GET))
    val HEAD: Allow    = Allow(NonEmptyChunk.single(Method.HEAD))
    val POST: Allow    = Allow(NonEmptyChunk.single(Method.POST))
    val PUT: Allow     = Allow(NonEmptyChunk.single(Method.PUT))
    val PATCH: Allow   = Allow(NonEmptyChunk.single(Method.PATCH))
    val DELETE: Allow  = Allow(NonEmptyChunk.single(Method.DELETE))
    val TRACE: Allow   = Allow(NonEmptyChunk.single(Method.TRACE))
    val CONNECT: Allow = Allow(NonEmptyChunk.single(Method.CONNECT))

    def parse(value: String): Either[String, Allow] = {
      @tailrec def loop(index: Int, value: String, acc: Chunk[Method]): Either[String, Chunk[Method]] = {
        if (value.isEmpty) Left("Invalid Allow header: empty value")
        else if (index == -1) {
          Method.fromString(value.trim) match {
            case Method.CUSTOM(name) => Left(s"Invalid Allow method: $name")
            case method: Method      => Right(acc :+ method)
          }
        } else {
          val valueChunk     = value.substring(0, index)
          val valueRemaining = value.substring(index + 1)
          val newIndex       = valueRemaining.indexOf(',')

          Method.fromString(valueChunk.trim) match {
            case Method.CUSTOM(name) =>
              Left(s"Invalid Allow method: $name")
            case method: Method      =>
              loop(
                newIndex,
                valueRemaining,
                acc :+ method,
              )
          }
        }
      }

      loop(value.indexOf(','), value, Chunk.empty).flatMap { methods =>
        NonEmptyChunk.fromChunk(methods) match {
          case Some(methods) => Right(Allow(methods))
          case None          => Left("Invalid Allow header: empty value")
        }
      }
    }

    def render(allow: Allow): String =
      allow.methods.map(_.name).mkString(", ")

  }

  sealed trait AuthenticationScheme {
    val name: String
  }

  object AuthenticationScheme {

    case object Basic extends AuthenticationScheme {
      override val name: String = "Basic"
    }

    case object Bearer extends AuthenticationScheme {
      override val name: String = "Bearer"
    }

    case object Digest extends AuthenticationScheme {
      override val name: String = "Digest"
    }

    case object HOBA extends AuthenticationScheme {
      override val name: String = "HOBA"
    }

    case object Mutual extends AuthenticationScheme {
      override val name: String = "Mutual"
    }

    case object Negotiate extends AuthenticationScheme {
      override val name: String = "Negotiate"
    }

    case object OAuth extends AuthenticationScheme {
      override val name: String = "OAuth"
    }

    case object Scram extends AuthenticationScheme {
      override val name: String = "SCRAM"
    }

    case object ScramSha1 extends AuthenticationScheme {
      override val name: String = "SCRAM-SHA-1"
    }

    case object ScramSha256 extends AuthenticationScheme {
      override val name: String = "SCRAM-SHA-256"
    }

    case object Vapid extends AuthenticationScheme {
      override val name: String = "vapid"
    }

    case object `AWS4-HMAC-SHA256` extends AuthenticationScheme {
      override val name: String = "AWS4-HMAC-SHA256"
    }

    def parse(name: String): Either[String, AuthenticationScheme] = {
      name.trim.toUpperCase match {
        case "BASIC"            => Right(Basic)
        case "BEARER"           => Right(Bearer)
        case "DIGEST"           => Right(Digest)
        case "HOBA"             => Right(HOBA)
        case "MUTUAL"           => Right(Mutual)
        case "NEGOTIATE"        => Right(Negotiate)
        case "OAUTH"            => Right(OAuth)
        case "SCRAM"            => Right(Scram)
        case "SCRAM-SHA-1"      => Right(ScramSha1)
        case "SCRAM-SHA-256"    => Right(ScramSha256)
        case "VAPID"            => Right(Vapid)
        case "AWS4-HMAC-SHA256" => Right(`AWS4-HMAC-SHA256`)
        case name: String       => Left(s"Unsupported authentication scheme: $name")
      }
    }

    def render(authenticationScheme: AuthenticationScheme): String =
      authenticationScheme.name

  }

  /**
   * Authorization header value.
   *
   * The Authorization header value contains one of the auth schemes
   */
  sealed trait Authorization extends Header {
    override type Self = Authorization
    override def self: Self                                  = this
    override def headerType: HeaderType.Typed[Authorization] = Authorization
  }

  object Authorization extends HeaderType {
    override type HeaderValue = Authorization

    override def name: String = "authorization"

    final case class Basic(username: String, password: String) extends Authorization

    final case class Digest(
      response: String,
      username: String,
      realm: String,
      uri: URI,
      opaque: String,
      algorithm: String,
      qop: String,
      cnonce: String,
      nonce: String,
      nc: Int,
      userhash: Boolean,
    ) extends Authorization

    final case class Bearer(token: String) extends Authorization

    final case class Unparsed(authScheme: String, authParameters: String) extends Authorization

    def parse(value: String): Either[String, Authorization] = {
      val parts = value.split(" ")
      if (parts.length >= 2) {
        parts(0).toLowerCase match {
          case "basic"  => parseBasic(parts(1))
          case "digest" => parseDigest(parts.tail.mkString(" "))
          case "bearer" => Right(Bearer(parts(1)))
          case _        => Right(Unparsed(parts(0), parts.tail.mkString(" ")))
        }
      } else Left(s"Invalid Authorization header value: $value")
    }

    def render(header: Authorization): String = header match {
      case Basic(username, password) =>
        s"Basic ${Base64.getEncoder.encodeToString(s"$username:$password".getBytes(StandardCharsets.UTF_8))}"

      case Digest(response, username, realm, uri, opaque, algo, qop, cnonce, nonce, nc, userhash) =>
        s"""Digest response="$response",username="$username",realm="$realm",uri=${uri.toString},opaque="$opaque",algorithm=$algo,""" +
          s"""qop=$qop,cnonce="$cnonce",nonce="$nonce",nc=$nc,userhash=${userhash.toString}"""
      case Bearer(token)                                                                          => s"Bearer $token"
      case Unparsed(scheme, params)                                                               => s"$scheme $params"
    }

    private def parseBasic(value: String): Either[String, Authorization] = {
      try {
        val partsOfBasic = new String(Base64.getDecoder.decode(value)).split(":")
        if (partsOfBasic.length == 2) {
          Right(Basic(partsOfBasic(0), partsOfBasic(1)))
        } else {
          Left("Basic Authorization header value is not in the format username:password")
        }
      } catch {
        case _: IllegalArgumentException =>
          Left("Basic Authorization header value is not a valid base64 encoded string")
      }
    }

    private final val quotationMarkChar = "\""
    private final val commaChar         = ","
    private final val equalsChar        = '='

    // https://datatracker.ietf.org/doc/html/rfc7616
    private def parseDigest(value: String): Either[String, Authorization] =
      try {
        def parseDigestKey(index: Int): (String, Int) = {
          val equalsIndex = value.indexOf(equalsChar, index)
          val currentKey  = value.substring(index, equalsIndex).toLowerCase.trim
          (currentKey, equalsIndex + 1)
        }

        def parseDigestValue(index: Int): (String, Int) = {
          val endChar           = if (value(index) == '"') quotationMarkChar else commaChar
          val maybeEndCharIndex = value.indexOf(endChar, index + 1)
          val endCharIndex      = if (maybeEndCharIndex == -1) value.length else maybeEndCharIndex
          val currentValue      = value.substring(index, endCharIndex).stripPrefix(quotationMarkChar)
          val newIndex          = if (endChar == commaChar) endCharIndex + 1 else endCharIndex + 2
          (currentValue, newIndex)
        }

        @tailrec
        def go(index: Int = 0, paramsAcc: Map[String, String] = Map.empty): Map[String, String] = if (
          index < value.length
        ) {
          val (key, tmpIndex)   = parseDigestKey(index)
          val (value, newIndex) = parseDigestValue(tmpIndex)
          go(newIndex, paramsAcc + (key -> value))
        } else paramsAcc

        val params = go()

        val maybeDigest = for {
          response <- params.get("response")
          userhash <- params.get("userhash").flatMap(v => Try(v.toBoolean).toOption).orElse(Some(false))
          username     = params.get("username")
          usernameStar = params.get("username*")
          usernameFinal <-
            if (username.isDefined && usernameStar.isEmpty) {
              username
            } else if (username.isEmpty && usernameStar.isDefined && !userhash) {
              usernameStar
            } else {
              None
            }
          realm         <- params.get("realm")
          uri           <- params.get("uri").flatMap(v => Try(new URI(v)).toOption)
          opaque        <- params.get("opaque")
          algo          <- params.get("algorithm")
          qop           <- params.get("qop")
          cnonce        <- params.get("cnonce")
          nonce         <- params.get("nonce")
          nc            <- params.get("nc").flatMap(v => Try(v.toInt).toOption)
        } yield Digest(response, usernameFinal, realm, uri, opaque, algo, qop, cnonce, nonce, nc, userhash)

        maybeDigest
          .toRight("Digest Authorization header value is not in the correct format")
      } catch {
        case _: IndexOutOfBoundsException =>
          Left("Digest Authorization header value is not in the correct format")
      }
  }

  /**
   * CacheControl header value.
   */
  sealed trait CacheControl extends Header {
    override type Self = CacheControl
    override def self: Self                                 = this
    override def headerType: HeaderType.Typed[CacheControl] = CacheControl

    val raw: String
  }

  object CacheControl extends HeaderType {
    override type HeaderValue = CacheControl

    override def name: String = "cache-control"

    /**
     * The immutable response directive indicates that the response will not be
     * updated while it's fresh
     */
    case object Immutable extends CacheControl {
      override val raw: String = "immutable"
    }

    /**
     * The max-age=N response directive indicates that the response remains
     * fresh until N seconds after the response is generated.
     *
     * The max-age=N request directive indicates that the client allows a stored
     * response that is generated on the origin server within N seconds
     */
    final case class MaxAge(freshForSeconds: Int) extends CacheControl {
      override val raw: String = "max-age"
    }

    /**
     * The max-stale=N request directive indicates that the client allows a
     * stored response that is stale within N seconds.
     */
    final case class MaxStale(staleWithinSeconds: Int) extends CacheControl {
      override val raw: String = "max-stale"
    }

    /**
     * The min-fresh=N request directive indicates that the client allows a
     * stored response that is fresh for at least N seconds.
     */
    final case class MinFresh(freshAtLeastSeconds: Int) extends CacheControl {
      override val raw: String = "min-fresh"
    }

    /**
     * The must-revalidate response directive indicates that the response can be
     * stored in caches and can be reused while fresh. If the response becomes
     * stale, it must be validated with the origin server before reuse.
     */
    case object MustRevalidate extends CacheControl {
      override val raw: String = "must-revalidate"
    }

    /**
     * The must-understand response directive indicates that a cache should
     * store the response only if it understands the requirements for caching
     * based on status code.
     */
    case object MustUnderstand extends CacheControl {
      override val raw: String = "must-understand"
    }

    /**
     * Maintains a chunk of CacheControl values.
     */
    final case class Multiple(values: NonEmptyChunk[CacheControl]) extends CacheControl {
      override val raw: String = values.map(_.raw).mkString(",")
    }

    /**
     * The no-cache response directive indicates that the response can be stored
     * in caches, but the response must be validated with the origin server
     * before each reuse.
     *
     * The no-cache request directive asks caches to validate the response with
     * the origin server before reuse.
     */
    case object NoCache extends CacheControl {
      override val raw: String = "no-cache"
    }

    /**
     * The no-store response directive indicates that any caches of any kind
     * (private or shared) should not store this response.
     *
     * The no-store request directive allows a client to request that caches
     * refrain from storing the request and corresponding response — even if the
     * origin server's response could be stored.
     */
    case object NoStore extends CacheControl {
      override val raw: String = "no-store"
    }

    /**
     * The no-transform indicates that any intermediary (regardless of whether
     * it implements a cache) shouldn't transform the response/request contents.
     */
    case object NoTransform extends CacheControl {
      override val raw: String = "no-transform"
    }

    /**
     * The client indicates that cache should obtain an already-cached response.
     * If a cache has stored a response, it's reused.
     */
    case object OnlyIfCached extends CacheControl {
      override val raw: String = "only-if-cached"
    }

    /**
     * The private response directive indicates that the response can be stored
     * only in a private cache
     */
    case object Private extends CacheControl {
      override val raw: String = "private"
    }

    /**
     * The proxy-revalidate response directive is the equivalent of
     * must-revalidate, but specifically for shared caches only.
     */
    case object ProxyRevalidate extends CacheControl {
      override val raw: String = "proxy-revalidate"
    }

    /**
     * The public response directive indicates that the response can be stored
     * in a shared cache.
     */
    case object Public extends CacheControl {
      override val raw: String = "public"
    }

    /**
     * The s-maxage response directive also indicates how long the response is
     * fresh for (similar to max-age) — but it is specific to shared caches, and
     * they will ignore max-age when it is present.
     */
    final case class SMaxAge(freshForSeconds: Int) extends CacheControl {
      override val raw: String = "s-maxage"
    }

    /**
     * The stale-if-error response directive indicates that the cache can reuse
     * a stale response when an origin server responds with an error (500, 502,
     * 503, or 504).
     */
    final case class StaleIfError(seconds: Int) extends CacheControl {
      override val raw: String = "stale-if-error"
    }

    /**
     * The stale-while-revalidate response directive indicates that the cache
     * could reuse a stale response while it revalidates it to a cache.
     */
    final case class StaleWhileRevalidate(seconds: Int) extends CacheControl {
      override val raw: String = "stale-while-revalidate"
    }

    def parse(value: String): Either[String, CacheControl] = {
      val index = value.indexOf(",")

      @tailrec def loop(value: String, index: Int, acc: Chunk[CacheControl]): Either[String, Chunk[CacheControl]] = {
        if (index == -1) {
          identifyCacheControl(value) match {
            case Left(value)         => Left(value)
            case Right(cacheControl) => Right(acc :+ cacheControl)
          }
        } else {
          val valueChunk = value.substring(0, index)
          val remaining  = value.substring(index + 1)
          val nextIndex  = remaining.indexOf(",")
          identifyCacheControl(valueChunk) match {
            case Left(error)         => Left(error)
            case Right(cacheControl) =>
              loop(
                remaining,
                nextIndex,
                acc :+ cacheControl,
              )
          }
        }
      }

      if (index == -1)
        identifyCacheControl(value)
      else
        loop(value, index, Chunk.empty[CacheControl]).flatMap { cacheControls =>
          NonEmptyChunk.fromChunk(cacheControls) match {
            case None        => Left("Cache-Control header must contain at least one value")
            case Some(value) => Right(Multiple(value))
          }
        }
    }

    def render(value: CacheControl): String = {
      value match {
        case Immutable                         => Immutable.raw
        case m @ MaxAge(freshForSeconds)       => s"${m.raw}=$freshForSeconds"
        case m @ MaxStale(staleWithinSeconds)  => s"${m.raw}=$staleWithinSeconds"
        case m @ MinFresh(freshAtLeastSeconds) => s"${m.raw}=$freshAtLeastSeconds"
        case MustRevalidate                    => MustRevalidate.raw
        case MustUnderstand                    => MustUnderstand.raw
        case Multiple(values)                  => values.map(render).mkString(",")
        case NoCache                           => NoCache.raw
        case NoStore                           => NoStore.raw
        case NoTransform                       => NoTransform.raw
        case OnlyIfCached                      => OnlyIfCached.raw
        case Private                           => Private.raw
        case ProxyRevalidate                   => ProxyRevalidate.raw
        case Public                            => Public.raw
        case s @ SMaxAge(freshForSeconds)      => s"${s.raw}=$freshForSeconds"
        case s @ StaleIfError(seconds)         => s"${s.raw}=$seconds"
        case s @ StaleWhileRevalidate(seconds) => s"${s.raw}=$seconds"
      }
    }

    private def identifyCacheControl(value: String): Either[String, CacheControl] = {
      val index = value.indexOf("=")
      if (index == -1)
        identifyCacheControlValue(value)
      else
        identifyCacheControlValue(value.substring(0, index), Try(value.substring(index + 1).toInt).toOption)

    }

    private def identifyCacheControlValue(value: String, seconds: Option[Int] = None): Either[String, CacheControl] = {
      val trimmedValue = value.trim()
      trimmedValue match {
        case "max-age"                => Right(MaxAge(seconds.getOrElse(0)))
        case "max-stale"              => Right(MaxStale(seconds.getOrElse(0)))
        case "min-fresh"              => Right(MinFresh(seconds.getOrElse(0)))
        case "s-maxage"               => Right(SMaxAge(seconds.getOrElse(0)))
        case NoCache.raw              => Right(NoCache)
        case NoStore.raw              => Right(NoStore)
        case NoTransform.raw          => Right(NoTransform)
        case OnlyIfCached.raw         => Right(OnlyIfCached)
        case MustRevalidate.raw       => Right(MustRevalidate)
        case ProxyRevalidate.raw      => Right(ProxyRevalidate)
        case MustUnderstand.raw       => Right(MustUnderstand)
        case Private.raw              => Right(Private)
        case Public.raw               => Right(Public)
        case Immutable.raw            => Right(Immutable)
        case "stale-while-revalidate" => Right(StaleWhileRevalidate(seconds.getOrElse(0)))
        case "stale-if-error"         => Right(StaleIfError(seconds.getOrElse(0)))
        case _                        => Left(s"Unknown cache control value: $trimmedValue")
      }
    }

  }

  /**
   * Connection header value.
   */
  sealed trait Connection extends Header {
    override type Self = Connection
    override def self: Self                               = this
    override def headerType: HeaderType.Typed[Connection] = Connection

    val value: String
  }

  object Connection extends HeaderType {
    override type HeaderValue = Connection

    override def name: String = "connection"

    /**
     * This directive indicates that either the client or the server would like
     * to close the connection. This is the default on HTTP/1.0 requests.
     */
    case object Close extends Connection {
      override val value: String = "close"
    }

    /**
     * Any comma-separated list of HTTP headers [Usually keep-alive only]
     * indicates that the client would like to keep the connection open. Keeping
     * a connection open is the default on HTTP/1.1 requests. The list of
     * headers are the name of the header to be removed by the first
     * non-transparent proxy or cache in-between: these headers define the
     * connection between the emitter and the first entity, not the destination
     * node.
     */
    case object KeepAlive extends Connection {
      override val value: String = "keep-alive"
    }

    def parse(connection: String): Either[String, Connection] = {
      connection.trim.toLowerCase() match {
        case Close.value     => Right(Close)
        case KeepAlive.value => Right(KeepAlive)
        case _               => Left("Invalid Connection")
      }
    }

    def render(connection: Connection): String = connection.value
  }

  final case class ContentBase(uri: URI) extends Header {
    override type Self = ContentBase
    override def self: Self                                = this
    override def headerType: HeaderType.Typed[ContentBase] = ContentBase
  }

  object ContentBase extends HeaderType {
    override type HeaderValue = ContentBase

    override def name: String = "content-base"

    def parse(s: String): Either[String, ContentBase] =
      Try(ContentBase(new java.net.URL(s).toURI)).toEither.left.map(_ => "Invalid Content-Base header")

    def render(cb: ContentBase): String =
      cb.uri.toString

    def uri(uri: URI): ContentBase = ContentBase(uri)
  }

  sealed trait ContentDisposition extends Header {
    override type Self = ContentDisposition
    override def self: Self                                       = this
    override def headerType: HeaderType.Typed[ContentDisposition] = ContentDisposition
  }

  object ContentDisposition extends HeaderType {
    override type HeaderValue = ContentDisposition

    override def name: String = "content-disposition"

    final case class Attachment(filename: Option[String])             extends ContentDisposition
    final case class Inline(filename: Option[String])                 extends ContentDisposition
    final case class FormData(name: String, filename: Option[String]) extends ContentDisposition

    private val AttachmentRegex         = """attachment; filename="(.*)"""".r
    private val InlineRegex             = """inline; filename="(.*)"""".r
    private val FormDataRegex           = """form-data; name="(.*)"; filename="(.*)"""".r
    private val FormDataNoFileNameRegex = """form-data; name="(.*)"""".r

    def parse(contentDisposition: String): Either[String, ContentDisposition] = {
      if (contentDisposition.startsWith("attachment")) {
        Right(contentDisposition match {
          case AttachmentRegex(filename) => Attachment(Some(filename))
          case _                         => Attachment(None)
        })
      } else if (contentDisposition.startsWith("inline")) {
        Right(contentDisposition match {
          case InlineRegex(filename) => Inline(Some(filename))
          case _                     => Inline(None)
        })
      } else if (contentDisposition.startsWith("form-data")) {
        contentDisposition match {
          case FormDataRegex(name, filename) => Right(FormData(name, Some(filename)))
          case FormDataNoFileNameRegex(name) => Right(FormData(name, None))
          case _                             => Left("Invalid form-data content disposition")
        }
      } else {
        Left("Invalid content disposition")
      }
    }

    def render(contentDisposition: ContentDisposition): String = {
      contentDisposition match {
        case Attachment(filename)     => s"attachment; ${filename.map("filename=" + _).getOrElse("")}"
        case Inline(filename)         => s"inline; ${filename.map("filename=" + _).getOrElse("")}"
        case FormData(name, filename) => s"form-data; name=$name; ${filename.map("filename=" + _).getOrElse("")}"
      }
    }

    val inline: ContentDisposition                                   = Inline(None)
    val attachment: ContentDisposition                               = Attachment(None)
    def inline(filename: String): ContentDisposition                 = Inline(Some(filename))
    def attachment(filename: String): ContentDisposition             = Attachment(Some(filename))
    def formData(name: String): ContentDisposition                   = FormData(name, None)
    def formData(name: String, filename: String): ContentDisposition = FormData(name, Some(filename))
  }

  sealed trait ContentEncoding extends Header {
    override type Self = ContentEncoding
    override def self: Self                                    = this
    override def headerType: HeaderType.Typed[ContentEncoding] = ContentEncoding

    val encoding: String
  }

  object ContentEncoding extends HeaderType {
    override type HeaderValue = ContentEncoding

    override def name: String = "content-encoding"

    /**
     * A format using the Brotli algorithm.
     */
    case object Br extends ContentEncoding {
      override val encoding: String = "br"
    }

    /**
     * A format using the Lempel-Ziv-Welch (LZW) algorithm. The value name was
     * taken from the UNIX compress program, which implemented this algorithm.
     * Like the compress program, which has disappeared from most UNIX
     * distributions, this content-encoding is not used by many browsers today,
     * partly because of a patent issue (it expired in 2003).
     */
    case object Compress extends ContentEncoding {
      override val encoding: String = "compress"
    }

    /**
     * Using the zlib structure (defined in RFC 1950) with the deflate
     * compression algorithm (defined in RFC 1951).
     */
    case object Deflate extends ContentEncoding {
      override val encoding: String = "deflate"
    }

    /**
     * A format using the Lempel-Ziv coding (LZ77), with a 32-bit CRC. This is
     * the original format of the UNIX gzip program. The HTTP/1.1 standard also
     * recommends that the servers supporting this content-encoding should
     * recognize x-gzip as an alias, for compatibility purposes.
     */
    case object GZip extends ContentEncoding {
      override val encoding: String = "gzip"
    }

    /**
     * Maintains a list of ContentEncoding values.
     */
    final case class Multiple(encodings: NonEmptyChunk[ContentEncoding]) extends ContentEncoding {
      override val encoding: String = encodings.map(_.encoding).mkString(",")
    }

    private def findEncoding(value: String): Option[ContentEncoding] = {
      value.trim match {
        case "br"       => Some(Br)
        case "compress" => Some(Compress)
        case "deflate"  => Some(Deflate)
        case "gzip"     => Some(GZip)
        case _          => None
      }
    }

    /**
     * @param value
     *   of string , seperated for multiple values
     * @return
     *   ContentEncoding
     *
     * Note: This implementation ignores the invalid string that might occur in
     * MultipleEncodings case.
     */
    def parse(value: String): Either[String, ContentEncoding] = {
      val encodings = Chunk.fromArray(value.split(",").map(findEncoding)).flatten

      NonEmptyChunk.fromChunk(encodings) match {
        case Some(value) =>
          if (value.size == 1) Right(value.head)
          else Right(Multiple(value))
        case None        => Left("Empty ContentEncoding")
      }
    }

    def render(value: ContentEncoding): String = value.encoding

  }

  sealed trait ContentLanguage extends Header {
    override type Self = ContentLanguage
    override def self: Self                                    = this
    override def headerType: HeaderType.Typed[ContentLanguage] = ContentLanguage
  }

  object ContentLanguage extends HeaderType {
    override type HeaderValue = ContentLanguage

    override def name: String = "content-language"

    case object Arabic extends ContentLanguage

    case object Bulgarian extends ContentLanguage

    case object Catalan extends ContentLanguage

    case object Chinese extends ContentLanguage

    case object Croatian extends ContentLanguage

    case object Czech extends ContentLanguage

    case object Danish extends ContentLanguage

    case object Dutch extends ContentLanguage

    case object English extends ContentLanguage

    case object Estonian extends ContentLanguage

    case object Finnish extends ContentLanguage

    case object French extends ContentLanguage

    case object German extends ContentLanguage

    case object Greek extends ContentLanguage

    case object Hebrew extends ContentLanguage

    case object Hindi extends ContentLanguage

    case object Hungarian extends ContentLanguage

    case object Icelandic extends ContentLanguage

    case object Indonesian extends ContentLanguage

    case object Italian extends ContentLanguage

    case object Japanese extends ContentLanguage

    case object Korean extends ContentLanguage

    case object Latvian extends ContentLanguage

    case object Lithuanian extends ContentLanguage

    case object Norwegian extends ContentLanguage

    case object Polish extends ContentLanguage

    case object Portuguese extends ContentLanguage

    case object Romanian extends ContentLanguage

    case object Russian extends ContentLanguage

    case object Serbian extends ContentLanguage

    case object Slovak extends ContentLanguage

    case object Slovenian extends ContentLanguage

    case object Spanish extends ContentLanguage

    case object Swedish extends ContentLanguage

    case object Thai extends ContentLanguage

    case object Turkish extends ContentLanguage

    case object Ukrainian extends ContentLanguage

    case object Vietnamese extends ContentLanguage

    def parse(value: String): Either[String, ContentLanguage] =
      value.toLowerCase.take(2) match {
        case "ar" => Right(Arabic)
        case "bg" => Right(Bulgarian)
        case "ca" => Right(Catalan)
        case "zh" => Right(Chinese)
        case "hr" => Right(Croatian)
        case "cs" => Right(Czech)
        case "da" => Right(Danish)
        case "nl" => Right(Dutch)
        case "en" => Right(English)
        case "et" => Right(Estonian)
        case "fi" => Right(Finnish)
        case "fr" => Right(French)
        case "de" => Right(German)
        case "el" => Right(Greek)
        case "he" => Right(Hebrew)
        case "hi" => Right(Hindi)
        case "hu" => Right(Hungarian)
        case "is" => Right(Icelandic)
        case "id" => Right(Indonesian)
        case "it" => Right(Italian)
        case "ja" => Right(Japanese)
        case "ko" => Right(Korean)
        case "lv" => Right(Latvian)
        case "lt" => Right(Lithuanian)
        case "nb" => Right(Norwegian)
        case "pl" => Right(Polish)
        case "pt" => Right(Portuguese)
        case "ro" => Right(Romanian)
        case "ru" => Right(Russian)
        case "sr" => Right(Serbian)
        case "sk" => Right(Slovak)
        case "sl" => Right(Slovenian)
        case "es" => Right(Spanish)
        case "sv" => Right(Swedish)
        case "th" => Right(Thai)
        case "tr" => Right(Turkish)
        case "uk" => Right(Ukrainian)
        case "vi" => Right(Vietnamese)
        case _    => Left(s"Invalid ContentLanguage: $value")
      }

    def render(contentLanguage: ContentLanguage): String =
      contentLanguage match {
        case Arabic     => "ar"
        case Bulgarian  => "bg"
        case Catalan    => "ca"
        case Chinese    => "zh"
        case Croatian   => "hr"
        case Czech      => "cs"
        case Danish     => "da"
        case Dutch      => "nl"
        case English    => "en"
        case Estonian   => "et"
        case Finnish    => "fi"
        case French     => "fr"
        case German     => "de"
        case Greek      => "el"
        case Hebrew     => "he"
        case Hindi      => "hi"
        case Hungarian  => "hu"
        case Icelandic  => "is"
        case Indonesian => "id"
        case Italian    => "it"
        case Japanese   => "ja"
        case Korean     => "ko"
        case Latvian    => "lv"
        case Lithuanian => "lt"
        case Norwegian  => "no"
        case Polish     => "pl"
        case Portuguese => "pt"
        case Romanian   => "ro"
        case Russian    => "ru"
        case Serbian    => "sr"
        case Slovak     => "sk"
        case Slovenian  => "sl"
        case Spanish    => "es"
        case Swedish    => "sv"
        case Thai       => "th"
        case Turkish    => "tr"
        case Ukrainian  => "uk"
        case Vietnamese => "vi"
      }
  }

  /**
   * The Content-Length header indicates the size of the message body, in bytes,
   * sent to the recipient.
   */
  final case class ContentLength(length: Long) extends Header {
    override type Self = ContentLength
    override def self: Self                                  = this
    override def headerType: HeaderType.Typed[ContentLength] = ContentLength
  }

  object ContentLength extends HeaderType {
    override type HeaderValue = ContentLength

    override def name: String = "content-length"

    def parse(value: String): Either[String, ContentLength] =
      Try(value.trim.toLong) match {
        case Failure(_)     => Left("Invalid Content-Length header")
        case Success(value) => fromLong(value)
      }

    def render(contentLength: ContentLength): String =
      contentLength.length.toString

    private def fromLong(value: Long): Either[String, ContentLength] =
      if (value >= 0)
        Right(ContentLength(value))
      else
        Left("Invalid Content-Length header")

  }

  final case class ContentLocation(value: URI) extends Header {
    override type Self = ContentLocation
    override def self: Self                                    = this
    override def headerType: HeaderType.Typed[ContentLocation] = ContentLocation
  }

  object ContentLocation extends HeaderType {
    override type HeaderValue = ContentLocation

    override def name: String = "content-location"

    def parse(value: String): Either[String, ContentLocation] =
      Try(ContentLocation(new URI(value))).toEither.left.map(_ => "Invalid Content-Location header")

    def render(contentLocation: ContentLocation): String =
      contentLocation.value.toString
  }

  final case class ContentMd5(value: String) extends Header {
    override type Self = ContentMd5
    override def self: Self                               = this
    override def headerType: HeaderType.Typed[ContentMd5] = ContentMd5
  }

  object ContentMd5 extends HeaderType {
    override type HeaderValue = ContentMd5

    override def name: String = "content-md5"

    private val MD5Regex = """[A-Fa-f0-9]{32}""".r

    def parse(value: String): Either[String, ContentMd5] =
      value match {
        case MD5Regex() => Right(ContentMd5(value))
        case _          => Left("Invalid Content-MD5 header")
      }

    def render(contentMd5: ContentMd5): String =
      contentMd5.value
  }

  sealed trait ContentRange extends Header {
    override type Self = ContentRange
    override def self: Self                                 = this
    override def headerType: HeaderType.Typed[ContentRange] = ContentRange

    def start: Option[Int]

    def end: Option[Int]

    def total: Option[Int]

    def unit: String
  }

  object ContentRange extends HeaderType {
    override type HeaderValue = ContentRange

    override def name: String = "content-range"

    final case class EndTotal(unit: String, s: Int, e: Int, t: Int) extends ContentRange {
      def start: Option[Int] = Some(s)

      def end: Option[Int] = Some(e)

      def total: Option[Int] = Some(t)
    }

    final case class StartEnd(unit: String, s: Int, e: Int) extends ContentRange {
      def start: Option[Int] = Some(s)

      def end: Option[Int] = Some(e)

      def total: Option[Int] = None
    }

    final case class RangeTotal(unit: String, t: Int) extends ContentRange {
      def start: Option[Int] = None

      def end: Option[Int] = None

      def total: Option[Int] = Some(t)
    }

    private val contentRangeStartEndTotalRegex = """(\w+) (\d+)-(\d+)/(\d+)""".r
    private val contentRangeStartEndRegex      = """(\w+) (\d+)-(\d+)/*""".r
    private val contentRangeTotalRegex         = """(\w+) */(\d+)""".r

    def parse(s: String): Either[String, ContentRange] =
      s match {
        case contentRangeStartEndTotalRegex(unit, start, end, total) =>
          Right(EndTotal(unit, start.toInt, end.toInt, total.toInt))
        case contentRangeStartEndRegex(unit, start, end)             =>
          Right(StartEnd(unit, start.toInt, end.toInt))
        case contentRangeTotalRegex(unit, total)                     =>
          Right(RangeTotal(unit, total.toInt))
        case _                                                       =>
          Left("Invalid content range")
      }

    def render(c: ContentRange): String =
      c match {
        case EndTotal(unit, start, end, total) =>
          s"$unit $start-$end/$total"
        case StartEnd(unit, start, end)        =>
          s"$unit $start-$end/*"
        case RangeTotal(unit, total)           =>
          s"$unit */$total"
      }

  }

  // scalafmt: { maxColumn = 180 }
  sealed trait ContentSecurityPolicy extends Header {
    override type Self = ContentSecurityPolicy
    override def self: Self                                          = this
    override def headerType: HeaderType.Typed[ContentSecurityPolicy] = ContentSecurityPolicy
  }

  // TODO: Should we make deprecated types deprecated in code?
  object ContentSecurityPolicy extends HeaderType {
    override type HeaderValue = ContentSecurityPolicy

    override def name: String = "content-security-policy"

    final case class SourcePolicy(srcType: SourcePolicyType, src: Source) extends ContentSecurityPolicy

    case object BlockAllMixedContent extends ContentSecurityPolicy

    // TODO: Deprecated and only Safari supports this and it is non-standard. Should we remove it?
    final case class PluginTypes(value: String) extends ContentSecurityPolicy

    // TODO: no modern browser supports this. Should we remove it?
    final case class Referrer(referrer: ReferrerPolicy) extends ContentSecurityPolicy

    final case class ReportTo(groupName: String) extends ContentSecurityPolicy

    final case class ReportUri(uri: URI) extends ContentSecurityPolicy

    final case class RequireSriFor(requirement: RequireSriForValue) extends ContentSecurityPolicy

    final case class Sandbox(value: SandboxValue) extends ContentSecurityPolicy

    final case class TrustedTypes(value: TrustedTypesValue) extends ContentSecurityPolicy

    case object UpgradeInsecureRequests extends ContentSecurityPolicy

    sealed trait SourcePolicyType

    object SourcePolicyType {
      case object `base-uri` extends SourcePolicyType

      case object `child-src` extends SourcePolicyType

      case object `connect-src` extends SourcePolicyType

      case object `default-src` extends SourcePolicyType

      case object `font-src` extends SourcePolicyType

      case object `form-action` extends SourcePolicyType

      case object `frame-ancestors` extends SourcePolicyType

      case object `frame-src` extends SourcePolicyType

      case object `img-src` extends SourcePolicyType

      case object `manifest-src` extends SourcePolicyType

      case object `media-src` extends SourcePolicyType

      case object `object-src` extends SourcePolicyType

      case object `prefetch-src` extends SourcePolicyType

      case object `script-src` extends SourcePolicyType

      case object `script-src-attr` extends SourcePolicyType

      case object `script-src-elem` extends SourcePolicyType

      case object `style-src` extends SourcePolicyType

      case object `style-src-attr` extends SourcePolicyType

      case object `style-src-elem` extends SourcePolicyType

      case object `upgrade-insecure-requests` extends SourcePolicyType

      case object `worker-src` extends SourcePolicyType

      def parse(s: String): Option[SourcePolicyType] = s match {
        case "base-uri"                  => Some(`base-uri`)
        case "child-src"                 => Some(`child-src`)
        case "connect-src"               => Some(`connect-src`)
        case "default-src"               => Some(`default-src`)
        case "font-src"                  => Some(`font-src`)
        case "form-action"               => Some(`form-action`)
        case "frame-ancestors"           => Some(`frame-ancestors`)
        case "frame-src"                 => Some(`frame-src`)
        case "img-src"                   => Some(`img-src`)
        case "manifest-src"              => Some(`manifest-src`)
        case "media-src"                 => Some(`media-src`)
        case "object-src"                => Some(`object-src`)
        case "prefetch-src"              => Some(`prefetch-src`)
        case "script-src"                => Some(`script-src`)
        case "script-src-attr"           => Some(`script-src-attr`)
        case "script-src-elem"           => Some(`script-src-elem`)
        case "style-src"                 => Some(`style-src`)
        case "style-src-attr"            => Some(`style-src-attr`)
        case "style-src-elem"            => Some(`style-src-elem`)
        case "upgrade-insecure-requests" => Some(`upgrade-insecure-requests`)
        case "worker-src"                => Some(`worker-src`)
        case _                           => None
      }

      def render(policyType: SourcePolicyType) =
        policyType match {
          case `base-uri`                  => "base-uri"
          case `child-src`                 => "child-src"
          case `connect-src`               => "connect-src"
          case `default-src`               => "default-src"
          case `font-src`                  => "font-src"
          case `form-action`               => "form-action"
          case `frame-ancestors`           => "frame-ancestors"
          case `frame-src`                 => "frame-src"
          case `img-src`                   => "img-src"
          case `manifest-src`              => "manifest-src"
          case `media-src`                 => "media-src"
          case `object-src`                => "object-src"
          case `prefetch-src`              => "prefetch-src"
          case `script-src`                => "script-src"
          case `script-src-attr`           => "script-src-attr"
          case `script-src-elem`           => "script-src-elem"
          case `style-src`                 => "style-src"
          case `style-src-attr`            => "style-src-attr"
          case `style-src-elem`            => "style-src-elem"
          case `upgrade-insecure-requests` => "upgrade-insecure-requests"
          case `worker-src`                => "worker-src"
        }
    }

    sealed trait Source {
      self =>
      def &&(other: Source): Source =
        if (other == Source.none) self else Source.Sequence(self, other)
    }

    object Source {
      case object none extends Source {
        override def &&(other: Source): Source = other
      }

      final case class Host(uri: URI) extends Source

      final case class Scheme(scheme: String) extends Source

      case object Self extends Source

      case object UnsafeEval extends Source

      case object WasmUnsafeEval extends Source

      case object UnsafeHashes extends Source

      case object UnsafeInline extends Source

      final case class Nonce(value: String) extends Source

      final case class Hash(algorithm: HashAlgorithm, value: String) extends Source

      case object StrictDynamic extends Source

      case object ReportSample extends Source

      final case class Sequence(left: Source, right: Source) extends Source

      sealed trait HashAlgorithm

      object HashAlgorithm {
        case object Sha256 extends HashAlgorithm

        case object Sha384 extends HashAlgorithm

        case object Sha512 extends HashAlgorithm

        def parse(s: String): Option[HashAlgorithm] = s match {
          case "sha256" => Some(Sha256)
          case "sha384" => Some(Sha384)
          case "sha512" => Some(Sha512)
          case _        => None
        }
      }

      private val NonceRegex  = "'nonce-(.*)'".r
      private val Sha256Regex = "'sha256-(.*)'".r
      private val Sha384Regex = "'sha384-(.*)'".r
      private val Sha512Regex = "'sha512-(.*)'".r

      def parse(s: String): Option[Source] = s match {
        case "'none'"           => Some(none)
        case "'self'"           => Some(Self)
        case "'unsafe-eval'"    => Some(UnsafeEval)
        case "'wasm-eval'"      => Some(WasmUnsafeEval)
        case "'unsafe-hashes'"  => Some(UnsafeHashes)
        case "'unsafe-inline'"  => Some(UnsafeInline)
        case "'strict-dynamic'" => Some(StrictDynamic)
        case "'report-sample'"  => Some(ReportSample)
        case NonceRegex(nonce)  => Some(Nonce(nonce))
        case Sha256Regex(hash)  => Some(Hash(HashAlgorithm.Sha256, hash))
        case Sha384Regex(hash)  => Some(Hash(HashAlgorithm.Sha384, hash))
        case Sha512Regex(hash)  => Some(Hash(HashAlgorithm.Sha512, hash))
        case s                  => Try(URI.create(s)).map(Host(_)).toOption
      }

      def render(source: Source): String = source match {
        case Source.none           => "'none'"
        case Self                  => "'self'"
        case UnsafeEval            => "'unsafe-eval'"
        case WasmUnsafeEval        => "'wasm-eval'"
        case UnsafeHashes          => "'unsafe-hashes'"
        case UnsafeInline          => "'unsafe-inline'"
        case StrictDynamic         => "'strict-dynamic'"
        case ReportSample          => "'report-sample'"
        case Nonce(nonce)          => s"'nonce-$nonce'"
        case Hash(algorithm, hash) => s"'$algorithm-$hash'"
        case Sequence(left, right) => s"${render(left)} ${render(right)}"
        case Host(uri)             => uri.toString
        case Scheme(scheme)        => s"$scheme:"
      }

      def host(uri: URI): Source = Host(uri)

      def scheme(scheme: String): Source = Scheme(scheme)

      def nonce(value: String): Source = Nonce(value)

      def hash(algorithm: HashAlgorithm, value: String): Source = Hash(algorithm, value)
    }

    sealed trait SandboxValue {
      self =>
      def &&(other: SandboxValue): SandboxValue =
        if (other == SandboxValue.Empty) self else SandboxValue.Sequence(self, other)
    }

    object SandboxValue {
      case object Empty extends SandboxValue {
        override def &&(other: SandboxValue): SandboxValue = other
      }

      case object AllowForms extends SandboxValue

      case object AllowSameOrigin extends SandboxValue

      case object AllowScripts extends SandboxValue

      case object AllowPopups extends SandboxValue

      case object AllowModals extends SandboxValue

      case object AllowOrientationLock extends SandboxValue

      case object AllowPointerLock extends SandboxValue

      case object AllowPresentation extends SandboxValue

      case object AllowPopupsToEscapeSandbox extends SandboxValue

      case object AllowTopNavigation extends SandboxValue

      final case class Sequence(left: SandboxValue, right: SandboxValue) extends SandboxValue

      def parse(value: String): Option[SandboxValue] = {
        def parseOne: String => Option[SandboxValue] = {
          case "allow-forms"                    => Some(AllowForms)
          case "allow-same-origin"              => Some(AllowSameOrigin)
          case "allow-scripts"                  => Some(AllowScripts)
          case "allow-popups"                   => Some(AllowPopups)
          case "allow-modals"                   => Some(AllowModals)
          case "allow-orientation-lock"         => Some(AllowOrientationLock)
          case "allow-pointer-lock"             => Some(AllowPointerLock)
          case "allow-presentation"             => Some(AllowPresentation)
          case "allow-popups-to-escape-sandbox" => Some(AllowPopupsToEscapeSandbox)
          case "allow-top-navigation"           => Some(AllowTopNavigation)
          case _                                => None
        }

        value match {
          case "" => Some(Empty)
          case s  =>
            Chunk.fromArray(s.split(" ")).foldLeft(Option(Empty): Option[SandboxValue]) {
              case (Some(acc), v) => parseOne(v).map(acc && _)
              case (None, _)      => None
            }
        }
      }

      def render(value: SandboxValue): String = {
        def toStringOne: SandboxValue => String = {
          case AllowForms                 => "allow-forms"
          case AllowSameOrigin            => "allow-same-origin"
          case AllowScripts               => "allow-scripts"
          case AllowPopups                => "allow-popups"
          case AllowModals                => "allow-modals"
          case AllowOrientationLock       => "allow-orientation-lock"
          case AllowPointerLock           => "allow-pointer-lock"
          case AllowPresentation          => "allow-presentation"
          case AllowPopupsToEscapeSandbox => "allow-popups-to-escape-sandbox"
          case AllowTopNavigation         => "allow-top-navigation"
          case Empty                      => ""
          case Sequence(left, right)      => toStringOne(left) + " " + toStringOne(right)
        }

        toStringOne(value)
      }
    }

    sealed trait TrustedTypesValue extends scala.Product with Serializable {
      self =>
      def &&(other: TrustedTypesValue): TrustedTypesValue =
        if (other == TrustedTypesValue.none) self else TrustedTypesValue.Sequence(self, other)
    }

    object TrustedTypesValue {
      case object none extends TrustedTypesValue {
        override def &&(other: TrustedTypesValue): TrustedTypesValue = other
      }

      final case class PolicyName(value: String) extends TrustedTypesValue

      case object `allow-duplicates` extends TrustedTypesValue

      case object Wildcard extends TrustedTypesValue

      final case class Sequence(left: TrustedTypesValue, right: TrustedTypesValue) extends TrustedTypesValue

      private val PolicyNameRegex = """\*|[a-zA-Z0-9-#=_/@.%]+|'allow-duplicates'|'none'""".r

      def parse(value: String): Option[TrustedTypesValue] = {
        val allValues = PolicyNameRegex.findAllIn(value).toList
        if (allValues.isEmpty) None
        else {
          Some {
            allValues.map {
              case "*"                  => TrustedTypesValue.Wildcard
              case "'none'"             => TrustedTypesValue.none
              case "'allow-duplicates'" => TrustedTypesValue.`allow-duplicates`
              case policyName           => TrustedTypesValue.PolicyName(policyName)
            }.reduce(_ && _)
          }
        }
      }

      def fromTrustedTypesValue(value: TrustedTypesValue): String =
        value match {
          case TrustedTypesValue.none                   => "'none'"
          case TrustedTypesValue.Wildcard               => "*"
          case TrustedTypesValue.`allow-duplicates`     => "'allow-duplicates'"
          case TrustedTypesValue.PolicyName(policyName) => policyName
          case TrustedTypesValue.Sequence(left, right)  =>
            fromTrustedTypesValue(left) + " " + fromTrustedTypesValue(right)
        }
    }

    sealed trait ReferrerPolicy extends scala.Product with Serializable

    object ReferrerPolicy {

      case object `no-referrer` extends ReferrerPolicy

      case object `none-when-downgrade` extends ReferrerPolicy

      case object `origin` extends ReferrerPolicy

      case object `origin-when-cross-origin` extends ReferrerPolicy

      case object `unsafe-url` extends ReferrerPolicy

      def parse(referrer: String): Option[ReferrerPolicy] =
        referrer match {
          case "no-referrer"              => Some(`no-referrer`)
          case "none-when-downgrade"      => Some(`none-when-downgrade`)
          case "origin"                   => Some(`origin`)
          case "origin-when-cross-origin" => Some(`origin-when-cross-origin`)
          case "unsafe-url"               => Some(`unsafe-url`)
          case _                          => None
        }

      def render(referrer: ReferrerPolicy): String = referrer.productPrefix
    }

    sealed trait RequireSriForValue extends scala.Product with Serializable

    object RequireSriForValue {
      case object Script extends RequireSriForValue

      case object Style extends RequireSriForValue

      case object ScriptStyle extends RequireSriForValue

      def parse(value: String): Option[RequireSriForValue] =
        value match {
          case "script"       => Some(Script)
          case "style"        => Some(Style)
          case "script style" => Some(ScriptStyle)
          case _              => None
        }

      def fromRequireSriForValue(value: RequireSriForValue): String =
        value match {
          case Script      => "script"
          case Style       => "style"
          case ScriptStyle => "script style"
        }
    }

    def defaultSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`default-src`, src.foldLeft[Source](Source.none)(_ && _))

    def scriptSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`script-src`, src.foldLeft[Source](Source.none)(_ && _))

    def styleSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`style-src`, src.foldLeft[Source](Source.none)(_ && _))

    def imgSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`img-src`, src.foldLeft[Source](Source.none)(_ && _))

    def mediaSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`media-src`, src.foldLeft[Source](Source.none)(_ && _))

    def frameSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`frame-src`, src.foldLeft[Source](Source.none)(_ && _))

    def fontSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`font-src`, src.foldLeft[Source](Source.none)(_ && _))

    def connectSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`connect-src`, src.foldLeft[Source](Source.none)(_ && _))

    def objectSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`object-src`, src.foldLeft[Source](Source.none)(_ && _))

    private val PluginTypesRegex  = "plugin-types (.*)".r
    private val ReferrerRegex     = "referrer (.*)".r
    private val ReportToRegex     = "report-to (.*)".r
    private val ReportUriRegex    = "report-uri (.*)".r
    private val RequireSriRegex   = "require-sri-for (.*)".r
    private val TrustedTypesRegex = "trusted-types (.*)".r
    private val SandboxRegex      = "sandbox (.*)".r
    private val PolicyRegex       = "([a-z-]+) (.*)".r

    def parse(value: String): Either[String, ContentSecurityPolicy] =
      value match {
        case "block-all-mixed-content"       => Right(ContentSecurityPolicy.BlockAllMixedContent)
        case PluginTypesRegex(types)         => Right(ContentSecurityPolicy.PluginTypes(types))
        case ReferrerRegex(referrer)         => ReferrerPolicy.parse(referrer).map(ContentSecurityPolicy.Referrer(_)).toRight("Invalid referrer policy")
        case ReportToRegex(group)            => Right(ContentSecurityPolicy.ReportTo(group))
        case ReportUriRegex(uri)             => Try(new URI(uri)).map(ContentSecurityPolicy.ReportUri(_)).toEither.left.map(_ => "Invalid report-uri")
        case RequireSriRegex(value)          => RequireSriForValue.parse(value).map(ContentSecurityPolicy.RequireSriFor(_)).toRight("Invalid require-sri-for value")
        case TrustedTypesRegex(value)        => TrustedTypesValue.parse(value).map(ContentSecurityPolicy.TrustedTypes(_)).toRight("Invalid trusted-types value")
        case SandboxRegex(sandbox)           => SandboxValue.parse(sandbox).map(ContentSecurityPolicy.Sandbox(_)).toRight("Invalid sandbox value")
        case "upgrade-insecure-requests"     => Right(ContentSecurityPolicy.UpgradeInsecureRequests)
        case PolicyRegex(policyType, policy) => ContentSecurityPolicy.fromTypeAndPolicy(policyType, policy)
        case _                               => Left("Invalid Content-Security-Policy")

      }

    def render(csp: ContentSecurityPolicy): String =
      csp match {
        case ContentSecurityPolicy.BlockAllMixedContent    => "block-all-mixed-content"
        case ContentSecurityPolicy.PluginTypes(types)      => s"plugin-types $types"
        case ContentSecurityPolicy.Referrer(referrer)      => s"referrer ${ReferrerPolicy.render(referrer)}"
        case ContentSecurityPolicy.ReportTo(reportTo)      => s"report-to $reportTo"
        case ContentSecurityPolicy.ReportUri(uri)          => s"report-uri $uri"
        case ContentSecurityPolicy.RequireSriFor(value)    => s"require-sri-for ${RequireSriForValue.fromRequireSriForValue(value)}"
        case ContentSecurityPolicy.TrustedTypes(value)     => s"trusted-types ${TrustedTypesValue.fromTrustedTypesValue(value)}"
        case ContentSecurityPolicy.Sandbox(value)          => s"sandbox ${SandboxValue.render(value)}"
        case ContentSecurityPolicy.UpgradeInsecureRequests => "upgrade-insecure-requests"
        case SourcePolicy(policyType, policy)              => s"${SourcePolicyType.render(policyType)} ${Source.render(policy)}"
      }

    def fromTypeAndPolicy(policyType: String, policy: String): Either[String, ContentSecurityPolicy] =
      SourcePolicyType
        .parse(policyType)
        .flatMap(policyType => Source.parse(policy).map(SourcePolicy(policyType, _)))
        .toRight("Invalid Content-Security-Policy")

  }

  sealed trait ContentTransferEncoding extends Header {
    override type Self = ContentTransferEncoding
    override def self: Self                                            = this
    override def headerType: HeaderType.Typed[ContentTransferEncoding] = ContentTransferEncoding
  }

  object ContentTransferEncoding extends HeaderType {
    override type HeaderValue = ContentTransferEncoding

    override def name: String = "content-transfer-encoding"

    case object SevenBit extends ContentTransferEncoding

    case object EightBit extends ContentTransferEncoding

    case object Binary extends ContentTransferEncoding

    case object QuotedPrintable extends ContentTransferEncoding

    case object Base64 extends ContentTransferEncoding

    final case class XToken(token: String) extends ContentTransferEncoding

    private val XRegex = "x-(.*)".r

    def parse(s: String): Either[String, ContentTransferEncoding] =
      s.toLowerCase match {
        case "7bit"             => Right(SevenBit)
        case "8bit"             => Right(EightBit)
        case "binary"           => Right(Binary)
        case "quoted-printable" => Right(QuotedPrintable)
        case "base64"           => Right(Base64)
        case XRegex(token)      => Right(XToken(token))
        case _                  => Left("Invalid Content-Transfer-Encoding header")
      }

    def render(contentTransferEncoding: ContentTransferEncoding): String =
      contentTransferEncoding match {
        case SevenBit        => "7bit"
        case EightBit        => "8bit"
        case Binary          => "binary"
        case QuotedPrintable => "quoted-printable"
        case Base64          => "base64"
        case XToken(token)   => s"x-$token"
      }
  }

  final case class ContentType(mediaType: MediaType, boundary: Option[Boundary] = None, charset: Option[Charset] = None) extends Header {
    override type Self = ContentType
    override def self: Self                                = this
    override def headerType: HeaderType.Typed[ContentType] = ContentType
  }

  object ContentType extends HeaderType {
    override type HeaderValue = ContentType
    override def name: String = "content-type"

    def parse(s: String): Either[String, ContentType] = {
      Chunk.fromArray(s.split(";")).map(_.trim) match {
        case Chunk(mediaType)                                                                                                    =>
          MediaType.forContentType(mediaType).toRight("Invalid Content-Type header").map(ContentType(_, None, None))
        case Chunk(mediaType, directive) if directive.startsWith("charset=")                                                     =>
          for {
            mediaType <- MediaType.forContentType(mediaType).toRight("Invalid Content-Type header")
            charset   <-
              try Right(Charset.forName(directive.drop(8)))
              catch { case _: UnsupportedCharsetException => Left("Invalid charset in Content-Type header") }
          } yield ContentType(mediaType, None, Some(charset))
        case Chunk(mediaType, directive) if directive.startsWith("boundary=")                                                    =>
          for {
            mediaType <- MediaType.forContentType(mediaType).toRight("Invalid Content-Type header")
            boundary = directive.drop(9)
          } yield ContentType(mediaType, Some(Boundary(boundary)), None)
        case Chunk(mediaType, directive1, directive2) if directive1.startsWith("charset=") && directive2.startsWith("boundary=") =>
          for {
            mediaType <- MediaType.forContentType(mediaType).toRight("Invalid Content-Type header")
            charset   <-
              try Right(Charset.forName(directive1.drop(8)))
              catch { case _: UnsupportedCharsetException => Left("Invalid charset in Content-Type header") }
            boundary = directive2.drop(9)
          } yield ContentType(mediaType, Some(Boundary(boundary)), Some(charset))
        case Chunk(mediaType, directive1, directive2) if directive1.startsWith("boundary=") && directive2.startsWith("charset=") =>
          for {
            mediaType <- MediaType.forContentType(mediaType).toRight("Invalid Content-Type header")
            charset   <-
              try Right(Charset.forName(directive2.drop(8)))
              catch { case _: UnsupportedCharsetException => Left("Invalid charset in Content-Type header") }
            boundary = directive1.drop(9)
          } yield ContentType(mediaType, Some(Boundary(boundary)), Some(charset))
        case _                                                                                                                   =>
          Left("Invalid Content-Type header")
      }
    }

    def render(contentType: ContentType): String =
      (contentType.charset, contentType.boundary) match {
        case (None, None)                    => contentType.mediaType.fullType
        case (Some(charset), None)           => contentType.mediaType.fullType + "; charset=" + charset.toString
        case (None, Some(boundary))          => contentType.mediaType.fullType + "; boundary=" + boundary
        case (Some(charset), Some(boundary)) => contentType.mediaType.fullType + "; charset=" + charset.toString + "; boundary=" + boundary
      }
  }

  final case class Date(value: ZonedDateTime) extends Header {
    override type Self = Date
    override def self: Self                         = this
    override def headerType: HeaderType.Typed[Date] = Date
  }

  /**
   * The Date general HTTP header contains the date and time at which the
   * message originated.
   */
  object Date extends HeaderType {
    override type HeaderValue = Date

    override def name: String = "date"

    private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME

    def parse(value: String): Either[String, Date] =
      Try(Date(ZonedDateTime.parse(value, formatter).withZoneSameInstant(ZoneOffset.UTC))).toEither.left.map(_ => "Invalid Date header")

    def render(date: Date): String =
      formatter.format(date.value)
  }

  sealed trait DNT extends Header {
    override type Self = DNT
    override def self: Self                        = this
    override def headerType: HeaderType.Typed[DNT] = DNT
  }

  object DNT extends HeaderType {
    override type HeaderValue = DNT

    override def name: String = "dnt"

    case object TrackingAllowed extends DNT

    case object TrackingNotAllowed extends DNT

    case object NotSpecified extends DNT

    def parse(value: String): Either[String, DNT] = {
      value match {
        case "null" => Right(NotSpecified)
        case "1"    => Right(TrackingNotAllowed)
        case "0"    => Right(TrackingAllowed)
        case _      => Left("Invalid DNT header")
      }
    }

    def render(dnt: DNT): String =
      dnt match {
        case NotSpecified       => "null"
        case TrackingAllowed    => "0"
        case TrackingNotAllowed => "1"
      }
  }

  sealed trait ETag extends Header {
    override type Self = ETag
    override def self: Self                         = this
    override def headerType: HeaderType.Typed[ETag] = ETag
  }

  object ETag extends HeaderType {
    override type HeaderValue = ETag

    override def name: String = "etag"

    final case class Strong(validator: String) extends ETag

    final case class Weak(validator: String) extends ETag

    def parse(value: String): Either[String, ETag] = {
      value match {
        case str if str.startsWith("w/\"") && str.endsWith("\"") => Right(Weak(str.drop(3).dropRight(1)))
        case str if str.startsWith("W/\"") && str.endsWith("\"") => Right(Weak(str.drop(3).dropRight(1)))
        case str if str.startsWith("\"") && str.endsWith("\"")   => Right(Strong(str.drop(1).dropRight(1)))
        case _                                                   => Left("Invalid ETag header")
      }
    }

    def render(eTag: ETag): String = {
      eTag match {
        case Weak(value)   => s"""W/"$value""""
        case Strong(value) => s""""$value""""
      }
    }
  }

  /**
   * The Expect HTTP request header indicates expectations that need to be met
   * by the server to handle the request successfully. There is only one defined
   * expectation: 100-continue
   */
  sealed trait Expect extends Header {
    override type Self = Expect
    override def self: Self                           = this
    override def headerType: HeaderType.Typed[Expect] = Expect
    val value: String
  }

  object Expect extends HeaderType {
    override type HeaderValue = Expect

    override def name: String = "expect"

    case object `100-continue` extends Expect {
      val value = "100-continue"
    }

    def parse(value: String): Either[String, Expect] =
      value match {
        case `100-continue`.value => Right(`100-continue`)
        case _                    => Left("Invalid Expect header")
      }

    def render(expect: Expect): String =
      expect.value
  }

  final case class Expires(value: ZonedDateTime) extends Header {
    override type Self = Expires
    override def self: Self                            = this
    override def headerType: HeaderType.Typed[Expires] = Expires
  }

  /**
   * The Expires HTTP header contains the date/time after which the response is
   * considered expired.
   *
   * Invalid expiration dates with value 0 represent a date in the past and mean
   * that the resource is already expired.
   *
   * Expires: <Date>
   *
   * Date: <day-name>, <day> <month> <year> <hour>:<minute>:<second> GMT
   *
   * Example:
   *
   * Expires: Wed, 21 Oct 2015 07:28:00 GMT
   */
  object Expires extends HeaderType {
    override type HeaderValue = Expires

    override def name: String = "expires"

    private val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

    def parse(date: String): Either[String, Expires] =
      Try(Expires(ZonedDateTime.parse(date, formatter))).toEither.left.map(_ => "Invalid Expires header")

    def render(expires: Expires): String =
      formatter.format(expires.value)
  }

  /** From header value. */
  final case class From(email: String) extends Header {
    override type Self = From
    override def self: Self                         = this
    override def headerType: HeaderType.Typed[From] = From
  }

  object From extends HeaderType {
    override type HeaderValue = From

    override def name: String = "from"

    // Regex that does veery loose validation of email.
    private val emailRegex = "([^ ]+@[^ ]+[.][^ ]+)".r

    def parse(fromHeader: String): Either[String, From] =
      fromHeader match {
        case emailRegex(_) => Right(From(fromHeader))
        case _             => Left("Invalid From header")
      }

    def render(from: From): String =
      from.email
  }

  final case class Host(hostAddress: String, port: Option[Int] = None) extends Header {
    override type Self = Host
    override def self: Self                         = this
    override def headerType: HeaderType.Typed[Host] = Host
  }

  object Host extends HeaderType {
    override type HeaderValue = Host

    override def name: String = "host"

    def apply(hostAddress: String, port: Int): Host = Host(hostAddress, Some(port))

    def parse(value: String): Either[String, Host] = {
      Chunk.fromArray(value.split(":")) match {
        case Chunk(host, portS)           =>
          Try(portS.toInt).map(port => Host(host, Some(port))).toEither.left.map(_ => "Invalid Host header")
        case Chunk(host) if host.nonEmpty =>
          Right(Host(host))
        case _                            =>
          Left("Invalid Host header")
      }
    }

    def render(host: Host): String =
      host match {
        case Host(address, None)       => address
        case Host(address, Some(port)) => s"$address:$port"
      }
  }

  sealed trait IfMatch extends Header {
    override type Self = IfMatch
    override def self: Self                            = this
    override def headerType: HeaderType.Typed[IfMatch] = IfMatch
  }

  object IfMatch extends HeaderType {
    override type HeaderValue = IfMatch

    override def name: String = "if-match"

    case object Any extends IfMatch

    final case class ETags(etags: NonEmptyChunk[String]) extends IfMatch

    def parse(value: String): Either[String, IfMatch] = {
      val etags = Chunk.fromArray(value.split(",").map(_.trim)).filter(_.nonEmpty)
      etags match {
        case Chunk("*") => Right(Any)
        case _          =>
          NonEmptyChunk.fromChunk(etags) match {
            case Some(value) => Right(ETags(value))
            case scala.None  => Left("Invalid If-Match header")
          }
      }
    }

    def render(ifMatch: IfMatch): String = ifMatch match {
      case Any          => "*"
      case ETags(etags) => etags.mkString(",")
    }

  }

  final case class IfModifiedSince(value: ZonedDateTime) extends Header {
    override type Self = IfModifiedSince
    override def self: Self                                    = this
    override def headerType: HeaderType.Typed[IfModifiedSince] = IfModifiedSince
  }

  object IfModifiedSince extends HeaderType {
    override type HeaderValue = IfModifiedSince

    override def name: String = "if-modified-since"

    private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME

    def parse(value: String): Either[String, IfModifiedSince] =
      Try(IfModifiedSince(ZonedDateTime.parse(value, formatter).withZoneSameInstant(ZoneOffset.UTC))).toEither.left.map(_ => "Invalid If-Modified-Since header")

    def render(ifModifiedSince: IfModifiedSince): String =
      formatter.format(ifModifiedSince.value)
  }

  sealed trait IfNoneMatch extends Header {
    override type Self = IfNoneMatch
    override def self: Self                                = this
    override def headerType: HeaderType.Typed[IfNoneMatch] = IfNoneMatch
  }

  object IfNoneMatch extends HeaderType {
    override type HeaderValue = IfNoneMatch

    override def name: String = "if-none-match"

    case object Any extends IfNoneMatch

    final case class ETags(etags: NonEmptyChunk[String]) extends IfNoneMatch

    def parse(value: String): Either[String, IfNoneMatch] = {
      val etags = Chunk.fromArray(value.split(",").map(_.trim)).filter(_.nonEmpty)
      etags match {
        case Chunk("*") => Right(Any)
        case _          =>
          NonEmptyChunk.fromChunk(etags) match {
            case Some(value) => Right(ETags(value))
            case scala.None  => Left("Invalid If-None-Match header")
          }
      }
    }

    def render(ifMatch: IfNoneMatch): String = ifMatch match {
      case Any          => "*"
      case ETags(etags) => etags.mkString(",")
    }
  }

  /**
   * The If-Range HTTP request header makes a range request conditional.
   * Possible values:
   *   - <day-name>, <day> <month> <year> <hour>:<minute>:<second> GMT
   *   - <etag> a string of ASCII characters placed between double quotes (Like
   *     "675af34563dc-tr34"). A weak entity tag (one prefixed by W/) must not
   *     be used in this header.
   */
  sealed trait IfRange extends Header {
    override type Self = IfRange
    override def self: Self                            = this
    override def headerType: HeaderType.Typed[IfRange] = IfRange
  }

  object IfRange extends HeaderType {
    override type HeaderValue = IfRange

    override def name: String = "if-range"

    private val webDateTimeFormatter =
      DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

    final case class ETag(value: String) extends IfRange

    final case class DateTime(value: ZonedDateTime) extends IfRange

    def parse(value: String): Either[String, IfRange] =
      value match {
        case value if value.startsWith("\"") && value.endsWith("\"") =>
          Right(ETag(value.drop(1).dropRight(1)))
        case dateTime                                                =>
          Try(DateTime(ZonedDateTime.from(webDateTimeFormatter.parse(dateTime)))).toEither.left.map(_ => "Invalid If-Range header")
      }

    def render(ifRange: IfRange): String =
      ifRange match {
        case DateTime(value) => webDateTimeFormatter.format(value)
        case ETag(value)     => s""""$value""""
      }
  }

  final case class IfUnmodifiedSince(value: ZonedDateTime) extends Header {
    override type Self = IfUnmodifiedSince
    override def self: Self                                      = this
    override def headerType: HeaderType.Typed[IfUnmodifiedSince] = IfUnmodifiedSince
  }

  /**
   * If-Unmodified-Since request header makes the request for the resource
   * conditional: the server will send the requested resource or accept it in
   * the case of a POST or another non-safe method only if the resource has not
   * been modified after the date specified by this HTTP header.
   */
  object IfUnmodifiedSince extends HeaderType {
    override type HeaderValue = IfUnmodifiedSince

    override def name: String = "if-unmodified-since"

    private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME

    def parse(value: String): Either[String, IfUnmodifiedSince] =
      Try(IfUnmodifiedSince(ZonedDateTime.parse(value, formatter).withZoneSameInstant(ZoneOffset.UTC))).toEither.left.map(_ => "Invalid If-Unmodified-Since header")

    def render(ifModifiedSince: IfUnmodifiedSince): String =
      formatter.format(ifModifiedSince.value)

  }

  final case class LastModified(value: ZonedDateTime) extends Header {
    override type Self = LastModified
    override def self: Self                                 = this
    override def headerType: HeaderType.Typed[LastModified] = LastModified
  }

  object LastModified extends HeaderType {
    override type HeaderValue = LastModified

    override def name: String = "last-modified"

    private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME

    def parse(value: String): Either[String, LastModified] =
      Try(LastModified(ZonedDateTime.parse(value, formatter).withZoneSameInstant(ZoneOffset.UTC))).toEither.left.map(_ => "Invalid Last-Modified header")

    def render(lastModified: LastModified): String =
      formatter.format(lastModified.value)
  }

  /**
   * Location header value.
   */
  final case class Location(url: URL) extends Header {
    override type Self = Location
    override def self: Self                             = this
    override def headerType: HeaderType.Typed[Location] = Location
  }

  object Location extends HeaderType {
    override type HeaderValue = Location

    override def name: String = "location"

    def parse(value: String): Either[String, Location] = {
      if (value == "") Left("Invalid Location header (empty)")
      else
        URL
          .fromString(value)
          .left
          .map(error => s"Invalid Location header: $error")
          .map(url => Location(url))
    }

    def render(urlLocation: Location): String =
      urlLocation.url.encode
  }

  /**
   * Max-Forwards header value
   */
  final case class MaxForwards(value: Int) extends Header {
    override type Self = MaxForwards
    override def self: Self                                = this
    override def headerType: HeaderType.Typed[MaxForwards] = MaxForwards
  }

  object MaxForwards extends HeaderType {
    override type HeaderValue = MaxForwards

    override def name: String = "max-forwards"

    def parse(value: String): Either[String, MaxForwards] = {
      Try(value.toInt) match {
        case Success(value) if value >= 0L => Right(MaxForwards(value))
        case _                             => Left("Invalid Max-Forwards header")
      }
    }

    def render(maxForwards: MaxForwards): String =
      maxForwards.value.toString
  }

  /** Origin header value. */
  sealed trait Origin extends Header {
    override type Self = Origin
    override def self: Self                           = this
    override def headerType: HeaderType.Typed[Origin] = Origin
  }

  object Origin extends HeaderType {
    override type HeaderValue = Origin

    override def name: String = "origin"

    /** The Origin header value is privacy sensitive or is an opaque origin. */
    case object Null extends Origin

    /** The Origin header value contains scheme, host and maybe port. */
    final case class Value(scheme: String, host: String, port: Option[Int] = None) extends Origin

    def apply(scheme: String, host: String, port: Option[Int] = None): Origin =
      Value(scheme, host, port)

    def parse(value: String): Either[String, Origin] =
      if (value == "null") Right(Null)
      else
        URL.fromString(value) match {
          case Left(_)                                              => Left("Invalid Origin header")
          case Right(url) if url.host.isEmpty || url.scheme.isEmpty => Left("Invalid Origin header")
          case Right(url)                                           => Right(Value(url.scheme.get.encode, url.host.get, url.portIfNotDefault))
        }

    def render(origin: Origin): String = {
      origin match {
        case Null                           => "null"
        case Value(scheme, host, maybePort) =>
          maybePort match {
            case Some(port) => s"$scheme://$host:$port"
            case None       => s"$scheme://$host"
          }
      }
    }
  }

  /** Pragma header value. */
  sealed trait Pragma extends Header {
    override type Self = Pragma
    override def self: Self                           = this
    override def headerType: HeaderType.Typed[Pragma] = Pragma
  }

  object Pragma extends HeaderType {
    override type HeaderValue = Pragma

    override def name: String = "pragma"

    /** Pragma no-cache value. */
    case object NoCache extends Pragma

    /** Invalid pragma value. */

    def parse(value: String): Either[String, Pragma] =
      value.toLowerCase match {
        case "no-cache" => Right(NoCache)
        case _          => Left("Invalid Pragma header")
      }

    def render(pragma: Pragma): String =
      pragma match {
        case NoCache => "no-cache"
      }
  }

  /**
   * The HTTP Proxy-Authenticate response header defines the authentication
   * method that should be used to gain access to a resource behind a proxy
   * server. It authenticates the request to the proxy server, allowing it to
   * transmit the request further.
   *
   * @param scheme
   *   Authentication type
   * @param realm
   *   A description of the protected area, the realm. If no realm is specified,
   *   clients often display a formatted host name instead.
   */
  final case class ProxyAuthenticate(scheme: AuthenticationScheme, realm: Option[String]) extends Header {
    override type Self = ProxyAuthenticate
    override def self: Self                                      = this
    override def headerType: HeaderType.Typed[ProxyAuthenticate] = ProxyAuthenticate
  }

  object ProxyAuthenticate extends HeaderType {
    override type HeaderValue = ProxyAuthenticate

    override def name: String = "proxy-authenticate"

    def parse(value: String): Either[String, ProxyAuthenticate] = {
      val parts = value.split(" realm=").map(_.trim).filter(_.nonEmpty)
      parts match {
        case Array(authScheme, realm) => toProxyAuthenticate(authScheme, Some(realm))
        case Array(authScheme)        => toProxyAuthenticate(authScheme, None)
        case _                        => Left("Invalid Proxy-Authenticate header")
      }
    }

    def render(proxyAuthenticate: ProxyAuthenticate): String = proxyAuthenticate match {
      case ProxyAuthenticate(scheme, Some(realm)) => s"${scheme.name} realm=$realm"
      case ProxyAuthenticate(scheme, None)        => s"${scheme.name}"
    }

    private def toProxyAuthenticate(authScheme: String, realm: Option[String]): Either[String, ProxyAuthenticate] =
      AuthenticationScheme.parse(authScheme).map(ProxyAuthenticate(_, realm))
  }

  /**
   * Proxy-Authorization: <type> <credentials>
   *
   * <type> - AuthenticationScheme
   *
   * <credentials> - The resulting string is base64 encoded
   *
   * Example
   *
   * Proxy-Authorization: Basic YWxhZGRpbjpvcGVuc2VzYW1l
   */
  final case class ProxyAuthorization(authenticationScheme: AuthenticationScheme, credential: String) extends Header {
    override type Self = ProxyAuthorization
    override def self: Self                                       = this
    override def headerType: HeaderType.Typed[ProxyAuthorization] = ProxyAuthorization
  }

  /**
   * The HTTP Proxy-Authorization request header contains the credentials to
   * authenticate a user agent to a proxy server, usually after the server has
   * responded with a 407 Proxy Authentication Required status and the
   * Proxy-Authenticate header.
   */
  object ProxyAuthorization extends HeaderType {
    override type HeaderValue = ProxyAuthorization

    override def name: String = "proxy-authorization"

    def parse(value: String): Either[String, ProxyAuthorization] = {
      value.split("\\s+") match {
        case Array(authorization, credential) if authorization.nonEmpty && credential.nonEmpty =>
          AuthenticationScheme.parse(authorization).map { authenticationScheme =>
            ProxyAuthorization(authenticationScheme, credential)
          }
        case _                                                                                 => Left("Invalid Proxy-Authorization header")
      }
    }

    def render(proxyAuthorization: ProxyAuthorization): String =
      s"${proxyAuthorization.authenticationScheme.name} ${proxyAuthorization.credential}"
  }

  sealed trait Range extends Header {
    override type Self = Range
    override def self: Self                          = this
    override def headerType: HeaderType.Typed[Range] = Range
  }

  object Range extends HeaderType {
    override type HeaderValue = Range

    override def name: String = "range"

    final case class Single(unit: String, start: Long, end: Option[Long]) extends Range

    final case class Multiple(unit: String, ranges: List[(Long, Option[Long])]) extends Range

    final case class Suffix(unit: String, value: Long) extends Range

    final case class Prefix(unit: String, value: Long) extends Range

    def parse(value: String): Either[String, Range] = {
      val parts = value.split("=")
      if (parts.length != 2) Left("Invalid Range header")
      else {
        Try {
          val unit  = parts(0)
          val range = parts(1)
          if (range.contains(",")) {
            val ranges       = range.split(",").map(_.trim).toList
            val parsedRanges = ranges.map { r =>
              if (r.contains("-")) {
                val startEnd = r.split("-")
                if (startEnd.length != 2) (startEnd(0).toLong, None)
                else {
                  val start = startEnd(0).toLong
                  val end   = startEnd(1).toLong
                  (start, Some(end))
                }
              } else (0L, None)
            }
            Multiple(unit, parsedRanges)
          } else if (range.contains("-")) {
            val startEnd = range.split("-")
            if (startEnd.length != 2)
              Single(unit, startEnd(0).toLong, None)
            else {
              if (startEnd(0).isEmpty)
                Suffix(unit, startEnd(1).toLong)
              else if (startEnd(1).isEmpty)
                Prefix(unit, startEnd(0).toLong)
              else
                Single(unit, startEnd(0).toLong, Some(startEnd(1).toLong))
            }
          } else {
            Suffix(unit, range.toLong)
          }
        }.toEither.left.map(_ => "Invalid Range header")
      }
    }

    def render(range: Range): String = range match {
      case Single(unit, start, end)   => s"$unit=$start-${end.getOrElse("")}"
      case Multiple(unit, ranges)     =>
        s"$unit=${ranges.map { case (start, end) => s"$start-${end.getOrElse("")}" }.mkString(",")}"
      case Suffix(unit, suffixLength) => s"$unit=-$suffixLength"
      case Prefix(unit, prefixLength) => s"$unit=$prefixLength-"
    }

  }

  /**
   * The Referer HTTP request header contains the absolute or partial address
   * from which a resource has been requested. The Referer header allows a
   * server to identify referring pages that people are visiting from or where
   * requested resources are being used. This data can be used for analytics,
   * logging, optimized caching, and more.
   *
   * When you click a link, the Referer contains the address of the page that
   * includes the link. When you make resource requests to another domain, the
   * Referer contains the address of the page that uses the requested resource.
   *
   * The Referer header can contain an origin, path, and querystring, and may
   * not contain URL fragments (i.e. #section) or username:password information.
   * The request's referrer policy defines the data that can be included. See
   * Referrer-Policy for more information and examples.
   */
  final case class Referer(url: URL) extends Header {
    override type Self = Referer
    override def self: Self                            = this
    override def headerType: HeaderType.Typed[Referer] = Referer
  }

  object Referer extends HeaderType {
    override type HeaderValue = Referer

    override def name: String = "referer"

    def parse(value: String): Either[String, Referer] = {
      URL.fromString(value) match {
        case Left(_)                                              => Left("Invalid Referer header")
        case Right(url) if url.host.isEmpty || url.scheme.isEmpty => Left("Invalid Referer header")
        case Right(url)                                           => Right(Referer(url))
      }
    }

    def render(referer: Referer): String =
      referer.url.toJavaURL.fold("")(_.toString())
  }

  final case class Cookie(value: NonEmptyChunk[model.Cookie.Request]) extends Header {
    override type Self = Cookie
    override def self: Self                           = this
    override def headerType: HeaderType.Typed[Cookie] = Cookie
  }

  /**
   * The Cookie HTTP request header contains stored HTTP cookies associated with
   * the server.
   */
  object Cookie extends HeaderType {
    override type HeaderValue = Cookie

    override def name: String = "cookie"

    def parse(value: String): Either[String, Cookie] =
      model.Cookie.Request.decode(value) match {
        case Left(value)  => Left(s"Invalid Cookie header: ${value.getMessage}")
        case Right(value) =>
          NonEmptyChunk.fromChunk(value) match {
            case Some(value) => Right(Cookie(value))
            case None        => Left("Invalid Cookie header")
          }
      }

    def render(cookie: Cookie): String =
      cookie.value.map(_.encode.getOrElse("")).mkString("; ")
  }

  final case class SetCookie(value: model.Cookie.Response) extends Header {
    override type Self = SetCookie
    override def self: Self                              = this
    override def headerType: HeaderType.Typed[SetCookie] = SetCookie
  }

  object SetCookie extends HeaderType {
    override type HeaderValue = SetCookie

    override def name: String = "set-cookie"

    def parse(value: String): Either[String, SetCookie] =
      model.Cookie.Response.decode(value) match {
        case Left(value)  => Left(s"Invalid Cookie header: ${value.getMessage}")
        case Right(value) => Right(SetCookie(value))
      }

    def render(cookie: SetCookie): String =
      cookie.value.encode.getOrElse("")
  }

  sealed trait RetryAfter extends Header {
    override type Self = RetryAfter
    override def self: Self                               = this
    override def headerType: HeaderType.Typed[RetryAfter] = RetryAfter
  }

  /**
   * The RetryAfter HTTP header contains the date/time after which to retry
   *
   * RetryAfter: <Date>
   *
   * Date: <day-name>, <day> <month> <year> <hour>:<minute>:<second> GMT
   *
   * Example:
   *
   * Expires: Wed, 21 Oct 2015 07:28:00 GMT
   *
   * Or RetryAfter the delay seconds.
   */
  object RetryAfter extends HeaderType {
    override type HeaderValue = RetryAfter

    override def name: String = "retry-after"

    final case class ByDate(date: ZonedDateTime) extends RetryAfter

    final case class ByDuration(delay: Duration) extends RetryAfter

    private val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

    def parse(dateOrSeconds: String): Either[String, RetryAfter] =
      Try(dateOrSeconds.toLong) match {
        case Failure(_)     =>
          Try(ZonedDateTime.parse(dateOrSeconds, formatter)) match {
            case Success(value) => Right(ByDate(value))
            case Failure(_)     => Left("Invalid RetryAfter")
          }
        case Success(value) =>
          if (value >= 0)
            Right(ByDuration(value.seconds))
          else
            Left("Invalid RetryAfter")
      }

    def render(retryAfter: RetryAfter): String =
      retryAfter match {
        case ByDate(date)         => formatter.format(date)
        case ByDuration(duration) =>
          duration.getSeconds.toString
      }
  }

  final case class SecWebSocketAccept(hashedKey: String) extends Header {
    override type Self = SecWebSocketAccept
    override def self: Self                                       = this
    override def headerType: HeaderType.Typed[SecWebSocketAccept] = SecWebSocketAccept
  }

  /**
   * The Sec-WebSocket-Accept header is used in the websocket opening handshake.
   * It would appear in the response headers. That is, this is header is sent
   * from server to client to inform that server is willing to initiate a
   * websocket connection.
   *
   * See:
   * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Accept
   */
  object SecWebSocketAccept extends HeaderType {
    override type HeaderValue = SecWebSocketAccept

    override def name: String = "sec-websocket-accept"

    def parse(value: String): Either[String, SecWebSocketAccept] =
      if (value.trim.isEmpty) Left("Invalid Sec-WebSocket-Accept header")
      else Right(SecWebSocketAccept(value))

    def render(secWebSocketAccept: SecWebSocketAccept): String =
      secWebSocketAccept.hashedKey
  }

  sealed trait SecWebSocketExtensions extends Header {
    override type Self = SecWebSocketExtensions
    override def self: Self                                           = this
    override def headerType: HeaderType.Typed[SecWebSocketExtensions] = SecWebSocketExtensions
  }

  /**
   * The Sec-WebSocket-Extensions header is used in the WebSocket handshake. It
   * is initially sent from the client to the server, and then subsequently sent
   * from the server to the client, to agree on a set of protocol-level
   * extensions to use during the connection.
   *
   * See:
   * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Extensions
   */
  object SecWebSocketExtensions extends HeaderType {
    override type HeaderValue = SecWebSocketExtensions

    override def name: String = "sec-websocket-extensions"

    // Sec-WebSocket-Extensions: foo, bar; baz=2

    // Sec-WebSocket-Extensions: deflate-stream
    //         Sec-WebSocket-Extensions: mux; max-channels=4; flow-control,
    //          deflate-stream
    //         Sec-WebSocket-Extensions: private-extension

    // Sec-WebSocket-Extensions = extension-list
    //         extension-list = 1#extension
    //         extension = extension-token *( ";" extension-param )
    //         extension-token = registered-token
    //         registered-token = token
    //         extension-param = token [ "=" (token | quoted-string) ]
    //             ;When using the quoted-string syntax variant, the value
    //             ;after quoted-string unescaping MUST conform to the
    //             ;'token' ABNF.

    sealed trait Extension

    object Extension {
      final case class Parameter(name: String, value: String) extends Extension

      final case class TokenParam(name: String) extends Extension
    }

    final case class Token(extension: Chunk[Extension]) extends SecWebSocketExtensions

    final case class Extensions(extensions: Chunk[Token]) extends SecWebSocketExtensions

    def parse(value: String): Either[String, SecWebSocketExtensions] =
      if (value.trim().isEmpty) Left("Invalid Sec-WebSocket-Extensions header")
      else {
        val extensions: Array[Token] = value
          .split(",")
          .map(_.trim)
          .flatMap { extension =>
            val parts  = extension.split(";").map(_.trim)
            val tokens =
              if (parts.length == 1) Array[Extension](Extension.TokenParam(parts(0)))
              else {
                val params: Array[Extension] = parts.map { part =>
                  val value = part.split("=")
                  val name  = value(0)
                  if (value.length == 1) Extension.TokenParam(name)
                  else Extension.Parameter(name, value(1))
                }
                params
              }
            Array(Token(Chunk.fromArray(tokens)))
          }
        Right(Extensions(Chunk.fromArray(extensions)))
      }

    def render(secWebSocketExtensions: SecWebSocketExtensions): String =
      secWebSocketExtensions match {
        case Extensions(extensions)              =>
          extensions
            .map(_.extension)
            .map(extension => renderParams(extension))
            .mkString(", ")
        case Token(extensions: Chunk[Extension]) => renderParams(extensions)
      }

    private def renderParams(extensions: Chunk[Extension]): String = {
      extensions.map {
        case Extension.TokenParam(value)      => value
        case Extension.Parameter(name, value) => s"$name=$value"
      }.mkString("; ")
    }

  }

  final case class SecWebSocketKey(base64EncodedKey: String) extends Header {
    override type Self = SecWebSocketKey
    override def self: Self                                    = this
    override def headerType: HeaderType.Typed[SecWebSocketKey] = SecWebSocketKey
  }

  /**
   * The Sec-WebSocket-Key header is used in the WebSocket handshake. It is sent
   * from the client to the server to provide part of the information used by
   * the server to prove that it received a valid WebSocket handshake. This
   * helps ensure that the server does not accept connections from non-WebSocket
   * clients (e.g. HTTP clients) that are being abused to send data to
   * unsuspecting WebSocket servers.
   *
   * See:
   * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Key
   */
  object SecWebSocketKey extends HeaderType {
    override type HeaderValue = SecWebSocketKey

    override def name: String = "sec-websocket-key"

    def parse(key: String): Either[String, SecWebSocketKey] = {
      try {
        val decodedKey = java.util.Base64.getDecoder.decode(key)
        if (decodedKey.length == 16) Right(SecWebSocketKey(key))
        else Left("Invalid Sec-WebSocket-Key header")
      } catch {
        case NonFatal(_) => Left("Invalid Sec-WebSocket-Key header")
      }

    }

    def render(secWebSocketKey: SecWebSocketKey): String =
      secWebSocketKey.base64EncodedKey
  }

  final case class SecWebSocketLocation(url: URL) extends Header {
    override type Self = SecWebSocketLocation
    override def self: Self                                         = this
    override def headerType: HeaderType.Typed[SecWebSocketLocation] = SecWebSocketLocation
  }

  object SecWebSocketLocation extends HeaderType {
    override type HeaderValue = SecWebSocketLocation

    override def name: String = "sec-websocket-location"

    def parse(value: String): Either[String, SecWebSocketLocation] = {
      if (value.trim == "") Left("Invalid Sec-WebSocket-Location header: empty value")
      else
        URL
          .fromString(value)
          .left
          .map(_ => "Invalid Sec-WebSocket-Location header: invalid URL")
          .map(url => SecWebSocketLocation(url))
    }

    def render(secWebSocketLocation: SecWebSocketLocation): String =
      secWebSocketLocation.url.encode
  }

  final case class SecWebSocketOrigin(url: URL) extends Header {
    override type Self = SecWebSocketOrigin
    override def self: Self                                       = this
    override def headerType: HeaderType.Typed[SecWebSocketOrigin] = SecWebSocketOrigin
  }

  /**
   * The Sec-WebSocket-Origin header is used to protect against unauthorized
   * cross-origin use of a WebSocket server by scripts using the |WebSocket| API
   * in a Web browser. The server is informed of the script origin generating
   * the WebSocket connection request.
   */
  object SecWebSocketOrigin extends HeaderType {
    override type HeaderValue = SecWebSocketOrigin

    override def name: String = "sec-websocket-origin"

    def parse(value: String): Either[String, SecWebSocketOrigin] = {
      if (value.trim == "") Left("Invalid Sec-WebSocket-Origin header: empty value")
      else
        URL
          .fromString(value)
          .left
          .map(_ => "Invalid Sec-WebSocket-Origin header: invalid URL")
          .map(url => SecWebSocketOrigin(url))
    }

    def render(secWebSocketOrigin: SecWebSocketOrigin): String = {
      secWebSocketOrigin.url.encode

    }
  }

  final case class SecWebSocketProtocol(subProtocols: NonEmptyChunk[String]) extends Header {
    override type Self = SecWebSocketProtocol
    override def self: Self                                         = this
    override def headerType: HeaderType.Typed[SecWebSocketProtocol] = SecWebSocketProtocol
  }

  /**
   * The Sec-WebSocket-Protocol header field is used in the WebSocket opening
   * handshake. It is sent from the client to the server and back from the
   * server to the client to confirm the subprotocol of the connection. This
   * enables scripts to both select a subprotocol and be sure that the server
   * agreed to serve that subprotocol.
   *
   * See:
   * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Protocol
   */
  object SecWebSocketProtocol extends HeaderType {
    override type HeaderValue = SecWebSocketProtocol

    override def name: String = "sec-websocket-protocol"

    def parse(subProtocols: String): Either[String, SecWebSocketProtocol] = {
      NonEmptyChunk.fromChunk(Chunk.fromArray(subProtocols.split(",")).map(_.trim).filter(_.nonEmpty)) match {
        case Some(value) => Right(SecWebSocketProtocol(value))
        case None        => Left("Invalid Sec-WebSocket-Protocol header")
      }
    }

    def render(secWebSocketProtocol: SecWebSocketProtocol): String =
      secWebSocketProtocol.subProtocols.mkString(", ")
  }

  final case class SecWebSocketVersion(version: Int) extends Header {
    override type Self = SecWebSocketVersion
    override def self: Self                                        = this
    override def headerType: HeaderType.Typed[SecWebSocketVersion] = SecWebSocketVersion
  }

  /**
   * The Sec-WebSocket-Version header field is used in the WebSocket opening
   * handshake. It is sent from the client to the server to indicate the
   * protocol version of the connection.
   *
   * See:
   * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Version
   */
  object SecWebSocketVersion extends HeaderType {
    override type HeaderValue = SecWebSocketVersion

    override def name: String = "sec-websocket-version"

    // https://www.iana.org/assignments/websocket/websocket.xml#version-number

    def parse(version: String): Either[String, SecWebSocketVersion] =
      try {
        val v = version.toInt
        if (v >= 0 && v <= 13) Right(SecWebSocketVersion(v))
        else Left("Invalid Sec-WebSocket-Version header")
      } catch {
        case NonFatal(_) => Left("Invalid Sec-WebSocket-Version header")
      }

    def render(secWebSocketVersion: SecWebSocketVersion): String =
      secWebSocketVersion.version.toString

  }

  /**
   * Server header value.
   */
  final case class Server(name: String) extends Header {
    override type Self = Server
    override def self: Self                           = this
    override def headerType: HeaderType.Typed[Server] = Server
  }

  object Server extends HeaderType {
    override type HeaderValue = Server

    override def name: String = "server"

    def parse(value: String): Either[String, Server] = {
      val trimmedValue = value.trim
      if (trimmedValue.isEmpty)
        Left("Invalid Server header: empty value")
      else Right(Server(trimmedValue))
    }

    def render(server: Server): String =
      server.name
  }

  sealed trait Te extends Header {
    override type Self = Te
    override def self: Self                       = this
    override def headerType: HeaderType.Typed[Te] = Te
    def raw: String
  }

  object Te extends HeaderType {
    override type HeaderValue = Te

    override def name: String = "te"

    /**
     * A compression format that uses the Lempel-Ziv-Welch (LZW) algorithm.
     */
    final case class Compress(weight: Option[Double]) extends Te {
      override def raw: String = "compress"
    }

    /**
     * A compression format that uses the zlib structure with the deflate
     * compression algorithm.
     */
    final case class Deflate(weight: Option[Double]) extends Te {
      override def raw: String = "deflate"
    }

    /**
     * A compression format that uses the Lempel-Ziv coding (LZ77) with a 32-bit
     * CRC.
     */
    final case class GZip(weight: Option[Double]) extends Te {
      override def raw: String = "gzip"
    }

    /**
     * Indicates the identity function (that is, without modification or
     * compression). This value is always considered as acceptable, even if
     * omitted.
     */
    case object Trailers extends Te {
      override def raw: String = "trailers"
    }

    /**
     * Maintains a chunk of AcceptEncoding values.
     */
    final case class Multiple(encodings: NonEmptyChunk[Te]) extends Te {
      override def raw: String = encodings.mkString(",")
    }

    private def identifyTeFull(raw: String): Option[Te] = {
      val index = raw.indexOf(";q=")
      if (index == -1)
        identifyTe(raw)
      else {
        identifyTe(raw.substring(0, index), Try(raw.substring(index + 3).toDouble).toOption)
      }
    }

    private def identifyTe(raw: String, weight: Option[Double] = None): Option[Te] = {
      raw.trim match {
        case "compress" => Some(Compress(weight))
        case "deflate"  => Some(Deflate(weight))
        case "gzip"     => Some(GZip(weight))
        case "trailers" => Some(Trailers)
        case _          => None
      }
    }

    def parse(value: String): Either[String, Te] = {
      val index = value.indexOf(",")

      @tailrec def loop(value: String, index: Int, acc: Chunk[Te]): Either[String, Chunk[Te]] = {
        if (index == -1) {
          identifyTeFull(value) match {
            case Some(te) => Right(acc :+ te)
            case None     => Left("Invalid TE header")
          }
        } else {
          val valueChunk = value.substring(0, index)
          val remaining  = value.substring(index + 1)
          val nextIndex  = remaining.indexOf(",")
          identifyTeFull(valueChunk) match {
            case Some(te) => loop(remaining, nextIndex, acc :+ te)
            case None     => Left("Invalid TE header")
          }
        }
      }

      if (index == -1)
        identifyTeFull(value).toRight("Invalid TE header")
      else
        loop(value, index, Chunk.empty[Te]).flatMap { encodings =>
          NonEmptyChunk.fromChunk(encodings).toRight("Invalid TE header").map(Multiple(_))
        }
    }

    def render(encoding: Te): String = encoding match {
      case c @ Compress(weight) => weight.fold(c.raw)(value => s"${c.raw};q=$value")
      case d @ Deflate(weight)  => weight.fold(d.raw)(value => s"${d.raw};q=$value")
      case g @ GZip(weight)     => weight.fold(g.raw)(value => s"${g.raw};q=$value")
      case Multiple(encodings)  => encodings.map(render).mkString(", ")
      case Trailers             => Trailers.raw
    }

  }

  /** Trailer header value. */
  final case class Trailer(header: String) extends Header {
    override type Self = Trailer
    override def self: Self                            = this
    override def headerType: HeaderType.Typed[Trailer] = Trailer
  }

  object Trailer extends HeaderType {
    override type HeaderValue = Trailer

    override def name: String = "trailer"

    private val headerRegex = "([a-z-_]*)".r

    def parse(value: String): Either[String, Trailer] =
      value.toLowerCase match {
        case headerRegex(value) => Right(Trailer(value))
        case _                  => Left("Invalid Trailer header")
      }

    def render(trailer: Trailer): String =
      trailer.header
  }

  sealed trait TransferEncoding extends Header {
    override type Self = TransferEncoding
    override def self: Self                                     = this
    override def headerType: HeaderType.Typed[TransferEncoding] = TransferEncoding
    val encoding: String
  }

  object TransferEncoding extends HeaderType {
    override type HeaderValue = TransferEncoding

    override def name: String = "transfer-encoding"

    /**
     * Data is sent in a series of chunks.
     */
    case object Chunked extends TransferEncoding {
      override val encoding: String = "chunked"
    }

    /**
     * A format using the Lempel-Ziv-Welch (LZW) algorithm. The value name was
     * taken from the UNIX compress program, which implemented this algorithm.
     * Like the compress program, which has disappeared from most UNIX
     * distributions, this content-encoding is not used by many browsers today,
     * partly because of a patent issue (it expired in 2003).
     */
    case object Compress extends TransferEncoding {
      override val encoding: String = "compress"
    }

    /**
     * Using the zlib structure (defined in RFC 1950) with the deflate
     * compression algorithm (defined in RFC 1951).
     */
    case object Deflate extends TransferEncoding {
      override val encoding: String = "deflate"
    }

    /**
     * A format using the Lempel-Ziv coding (LZ77), with a 32-bit CRC. This is
     * the original format of the UNIX gzip program. The HTTP/1.1 standard also
     * recommends that the servers supporting this content-encoding should
     * recognize x-gzip as an alias, for compatibility purposes.
     */
    case object GZip extends TransferEncoding {
      override val encoding: String = "gzip"
    }

    /**
     * Maintains a list of TransferEncoding values.
     */
    final case class Multiple(encodings: NonEmptyChunk[TransferEncoding]) extends TransferEncoding {
      override val encoding: String = encodings.map(_.encoding).mkString(",")
    }

    private def findEncoding(value: String): Option[TransferEncoding] = {
      value.trim match {
        case "chunked"  => Some(Chunked)
        case "compress" => Some(Compress)
        case "deflate"  => Some(Deflate)
        case "gzip"     => Some(GZip)
        case _          => None
      }
    }

    /**
     * @param value
     *   of string , separated for multiple values
     * @return
     *   TransferEncoding
     *
     * Note: This implementation ignores the invalid string that might occur in
     * MultipleEncodings case.
     */
    def parse(value: String): Either[String, TransferEncoding] = {
      val encodings = Chunk.fromArray(value.split(",")).map(findEncoding).flatten

      NonEmptyChunk.fromChunk(encodings) match {
        case Some(value) =>
          if (value.size == 1) Right(value.head)
          else Right(Multiple(value))
        case None        => Left("Empty TransferEncoding")
      }
    }

    def render(value: TransferEncoding): String = value.encoding

  }

  sealed trait Upgrade extends Header {
    override type Self = Upgrade
    override def self: Self                            = this
    override def headerType: HeaderType.Typed[Upgrade] = Upgrade
  }

  object Upgrade extends HeaderType {
    override type HeaderValue = Upgrade

    override def name: String = "upgrade"

    final case class Multiple(protocols: NonEmptyChunk[Protocol]) extends Upgrade

    final case class Protocol(protocol: String, version: String) extends Upgrade

    def parse(value: String): Either[String, Upgrade] = {
      NonEmptyChunk.fromChunk(Chunk.fromArray(value.split(",")).map(parseProtocol)) match {
        case None        => Left("Invalid Upgrade header")
        case Some(value) =>
          if (value.size == 1) value.head
          else
            value.tail
              .foldLeft(value.head.map(NonEmptyChunk.single(_))) {
                case (Right(acc), Right(value)) => Right(acc :+ value)
                case (Left(error), _)           => Left(error)
                case (_, Left(value))           => Left(value)
              }
              .map(Multiple(_))
      }
    }

    def render(upgrade: Upgrade): String =
      upgrade match {
        case Multiple(protocols)         => protocols.map(render).mkString(", ")
        case Protocol(protocol, version) => s"$protocol/$version"
      }

    private def parseProtocol(value: String): Either[String, Protocol] =
      Chunk.fromArray(value.split("/")).map(_.trim) match {
        case Chunk(protocol, version) => Right(Protocol(protocol, version))
        case _                        => Left("Invalid Upgrade header")
      }
  }

  final case class UpgradeInsecureRequests() extends Header {
    override type Self = UpgradeInsecureRequests
    override def self: Self                                            = this
    override def headerType: HeaderType.Typed[UpgradeInsecureRequests] = UpgradeInsecureRequests
  }

  /**
   * The HTTP Upgrade-Insecure-Requests request header sends a signal to the
   * server expressing the client's preference for an encrypted and
   * authenticated response.
   */
  object UpgradeInsecureRequests extends HeaderType {
    override type HeaderValue = UpgradeInsecureRequests

    override def name: String = "upgrade-insecure-requests"

    def parse(value: String): Either[String, UpgradeInsecureRequests] =
      if (value.trim == "1") Right(UpgradeInsecureRequests())
      else Left("Invalid Upgrade-Insecure-Requests header")

    def render(upgradeInsecureRequests: UpgradeInsecureRequests): String =
      "1"
  }

  sealed trait UserAgent extends Header {
    override type Self = UserAgent
    override def self: Self                              = this
    override def headerType: HeaderType.Typed[UserAgent] = UserAgent
  }

  /**
   * The "User-Agent" header field contains information about the user agent
   * originating the request, which is often used by servers to help identify
   * the scope of reported interoperability problems, to work around or tailor
   * responses to avoid particular user agent limitations, and for analytics
   * regarding browser or operating system use
   */
  object UserAgent extends HeaderType {
    override type HeaderValue = UserAgent

    override def name: String = "user-agent"

    final case class Complete(product: Product, comment: Option[Comment]) extends UserAgent

    final case class Product(name: String, version: Option[String]) extends UserAgent

    final case class Comment(comment: String) extends UserAgent

    private val productRegex  = """(?i)([a-z0-9]+)(?:/([a-z0-9.]+))?""".r
    private val commentRegex  = """(?i)\((.*)$""".r
    private val completeRegex = s"""^(?i)([a-z0-9]+)(?:/([a-z0-9.]+))(.*)$$""".r

    def parse(userAgent: String): Either[String, UserAgent] = {
      userAgent match {
        case productRegex(name, version)           => Right(Product(name, Option(version)))
        case commentRegex(comment)                 => Right(Comment(comment))
        case completeRegex(name, version, comment) =>
          Right(Complete(Product(name, Option(version)), Option(Comment(comment))))
        case _                                     => Left("Invalid User-Agent header")
      }
    }

    def render(userAgent: UserAgent): String = userAgent match {
      case Complete(product, comment) =>
        s"""${render(product)}${render(comment.getOrElse(Comment("")))}"""
      case Product(name, version)     => s"""$name${version.map("/" + _).getOrElse("")}"""
      case Comment(comment)           => s" ($comment)"
    }

  }

  /** Vary header value. */
  sealed trait Vary extends Header {
    override type Self = Vary
    override def self: Self                         = this
    override def headerType: HeaderType.Typed[Vary] = Vary
  }

  object Vary extends HeaderType {
    override type HeaderValue = Vary

    override def name: String = "vary"

    case class Headers(headers: NonEmptyChunk[String]) extends Vary

    case object Star extends Vary

    def apply(first: String, rest: String*): Vary = Headers(NonEmptyChunk(first, rest: _*))

    def parse(value: String): Either[String, Vary] = {
      Chunk.fromArray(value.toLowerCase().split("[, ]+")) match {
        case Chunk("*")              => Right(Star)
        case chunk if value.nonEmpty =>
          NonEmptyChunk.fromChunk(chunk) match {
            case Some(chunk) => Right(Headers(chunk.map(_.trim)))
            case None        => Left("Invalid Vary header")
          }
        case _                       => Left("Invalid Vary header")
      }
    }

    def render(vary: Vary): String = {
      vary match {
        case Star          => "*"
        case Headers(list) => list.mkString(", ")
      }
    }
  }

  sealed trait Via extends Header {
    override type Self = Via
    override def self: Self                        = this
    override def headerType: HeaderType.Typed[Via] = Via
  }

  /**
   * The Via general header is added by proxies, both forward and reverse, and
   * can appear in the request or response headers. It is used for tracking
   * message forwards, avoiding request loops, and identifying the protocol
   * capabilities of senders along the request/response chain
   */
  object Via extends HeaderType {
    override type HeaderValue = Via

    override def name: String = "via"

    sealed trait ReceivedProtocol

    object ReceivedProtocol {
      final case class Version(version: String) extends ReceivedProtocol

      final case class ProtocolVersion(protocol: String, version: String) extends ReceivedProtocol
    }

    final case class Detailed(receivedProtocol: ReceivedProtocol, receivedBy: String, comment: Option[String]) extends Via

    final case class Multiple(values: NonEmptyChunk[Via]) extends Via

    def parse(values: String): Either[String, Via] = {
      val viaValues = Chunk.fromArray(values.split(",")).map(_.trim).map { value =>
        Chunk.fromArray(value.split(" ")) match {
          case Chunk(receivedProtocol, receivedBy)          =>
            toReceivedProtocol(receivedProtocol).map { rp =>
              Detailed(rp, receivedBy, None)
            }
          case Chunk(receivedProtocol, receivedBy, comment) =>
            toReceivedProtocol(receivedProtocol).map { rp =>
              Detailed(rp, receivedBy, Some(comment))
            }
          case _                                            =>
            Left("Invalid Via header")
        }
      }

      NonEmptyChunk.fromChunk(viaValues) match {
        case None        => Left("Invalid Via header")
        case Some(value) =>
          if (value.size == 1) value.head
          else
            value.tail
              .foldLeft(value.head.map(NonEmptyChunk.single(_))) {
                case (Right(acc), Right(value)) => Right(acc :+ value)
                case (Left(error), _)           => Left(error)
                case (_, Left(value))           => Left(value)
              }
              .map(Multiple(_))
      }
    }

    def render(via: Via): String =
      via match {
        case Multiple(values)                                =>
          values.map(render).mkString(", ")
        case Detailed(receivedProtocol, receivedBy, comment) =>
          s"${fromReceivedProtocol(receivedProtocol)} $receivedBy ${comment.getOrElse("")}"
      }

    private def fromReceivedProtocol(receivedProtocol: ReceivedProtocol): String =
      receivedProtocol match {
        case ReceivedProtocol.Version(version)                   => version
        case ReceivedProtocol.ProtocolVersion(protocol, version) => s"$protocol/$version"
      }

    private def toReceivedProtocol(value: String): Either[String, ReceivedProtocol] = {
      value.split("/").toList match {
        case version :: Nil             => Right(ReceivedProtocol.Version(version))
        case protocol :: version :: Nil => Right(ReceivedProtocol.ProtocolVersion(protocol, version))
        case _                          => Left("Invalid received protocol")
      }
    }

  }

  /*
     A warning has the following syntax: <warn-code> <warn-agent> <warn-text> [<warn-date>]
   */
  final case class Warning(code: Int, agent: String, text: String, date: Option[ZonedDateTime] = None) extends Header {
    override type Self = Warning
    override def self: Self                            = this
    override def headerType: HeaderType.Typed[Warning] = Warning
  }

  /*
  The Warning HTTP header contains information about possible problems with the status of the message.
    More than one Warning header may appear in a response.

  Warning header fields can, in general, be applied to any message.
    However, some warn-codes are specific to caches and can only be applied to response messages.
   */

  object Warning extends HeaderType {
    override type HeaderValue = Warning

    override def name: String = "warning"

    private val validCodes         = List(110, 111, 112, 113, 199, 214, 299)
    private val expectedDateFormat = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

    def parse(warningString: String): Either[String, Warning] = {
      /*
        <warn-code>
         A three-digit warning number.
           The first digit indicates whether the Warning is required to be deleted from a stored response after validation.

         1xx warn-codes describe the freshness or validation status of the response and will be deleted by a cache after deletion.

          2xx warn-codes describe some aspect of the representation that is not rectified by a validation and
             will not be deleted by a cache after validation unless a full response is sent.
       */
      val warnCode: Int = Try {
        Integer.parseInt(warningString.split(" ")(0))
      }.getOrElse(-1)

      /*
         <warn-agent>
           The name or pseudonym of the server or software adding the Warning header (might be "-" when the agent is unknown).
       */
      val warnAgent: String = warningString.split(" ")(1)

      /*
         <warn-text>
         An advisory text describing the error.
       */
      val descriptionStartIndex = warningString.indexOf('\"')
      val descriptionEndIndex   = warningString.indexOf("\"", warningString.indexOf("\"") + 1)
      val description           =
        Try {
          warningString.substring(descriptionStartIndex, descriptionEndIndex + 1)
        }.getOrElse("")

      /*
      <warn-date>
      A date. This is optional. If more than one Warning header is sent, include a date that matches the Date header.
       */

      val dateStartIndex = warningString.indexOf("\"", descriptionEndIndex + 1)
      val dateEndIndex   = warningString.indexOf("\"", dateStartIndex + 1)
      val warningDate    = Try {
        val selectedDate = warningString.substring(dateStartIndex + 1, dateEndIndex)
        ZonedDateTime.parse(selectedDate, expectedDateFormat)
      }.toOption

      val fullWarning = Warning(warnCode, warnAgent, description, warningDate)

      /*
      The HTTP Warn Codes registry at iana.org defines the namespace for warning codes.
        Registry is available here: https://www.iana.org/assignments/http-warn-codes/http-warn-codes.xhtml
       */
      def isCodeValid(warningCode: Int): Boolean = {
        if (validCodes.contains(warningCode)) true
        else false
      }

      def isAgentMissing(text: String): Boolean = {
        val textBeforeDescription = text.toList.take(descriptionStartIndex)
        if (textBeforeDescription.length <= 4) {
          true
        } else false
      }

      /*
      Date should confirm to the pattern "EEE, dd MMM yyyy HH:mm:ss zzz"
      For example: Wed, 21 Oct 2015 07:28:00 GMT
       */
      def isDateInvalid(warningText: String, warningDate: Option[ZonedDateTime]): Boolean = {
        val trimmedWarningText         = warningText.trim
        val descriptionEndIndexNoSpace = trimmedWarningText.indexOf("\"", trimmedWarningText.indexOf("\"") + 1)
        if (warningDate.isEmpty && trimmedWarningText.length - descriptionEndIndexNoSpace > 1) true
        else false
      }

      if (isDateInvalid(warningString, warningDate)) {
        Left("Invalid date format")
      } else if (isAgentMissing(warningString)) {
        Left("Agent is missing")
      } else if (isCodeValid(fullWarning.code) && fullWarning.text.nonEmpty) {
        Right(fullWarning)
      } else {
        Left("Invalid warning")
      }

    }

    def render(warning: Warning): String =
      warning match {
        case Warning(code, agent, text, date) => {
          val formattedDate = date match {
            case Some(value) => value.format(expectedDateFormat)
            case None        => ""
          }
          if (formattedDate.isEmpty) {
            code.toString + " " + agent + " " + text
          } else {
            code.toString + " " + agent + " " + text + " " + '"' + formattedDate + '"'
          }
        }
      }
  }

  sealed trait WWWAuthenticate extends Header {
    override type Self = WWWAuthenticate
    override def self: Self                                    = this
    override def headerType: HeaderType.Typed[WWWAuthenticate] = WWWAuthenticate
  }

  object WWWAuthenticate extends HeaderType {
    override type HeaderValue = WWWAuthenticate

    override def name: String = "www-authenticate"

    final case class Basic(realm: Option[String] = None, charset: String = "UTF-8") extends WWWAuthenticate

    final case class Bearer(
      realm: String,
      scope: Option[String] = None,
      error: Option[String] = None,
      errorDescription: Option[String] = None,
    ) extends WWWAuthenticate

    final case class Digest(
      realm: Option[String],
      domain: Option[String] = None,
      nonce: Option[String] = None,
      opaque: Option[String] = None,
      stale: Option[Boolean] = None,
      algorithm: Option[String] = None,
      qop: Option[String] = None,
      charset: Option[String] = None,
      userhash: Option[Boolean] = None,
    ) extends WWWAuthenticate

    final case class HOBA(realm: Option[String], challenge: String, maxAge: Int) extends WWWAuthenticate

    final case class Mutual(realm: String, error: Option[String] = None, errorDescription: Option[String] = None) extends WWWAuthenticate

    final case class Negotiate(authData: Option[String] = None) extends WWWAuthenticate

    final case class SCRAM(
      realm: String,
      sid: String,
      data: String,
    ) extends WWWAuthenticate

    final case class `AWS4-HMAC-SHA256`(
      realm: String,
      credentials: Option[String] = None,
      signedHeaders: String,
      signature: String,
    ) extends WWWAuthenticate

    final case class Unknown(scheme: String, realm: String, params: Map[String, String]) extends WWWAuthenticate

    private val challengeRegEx  = """(\w+) (.*)""".r
    private val auth            = """(\w+)=(?:"([^"]+)"|([^,]+))""".r
    private val nonQuotedValues = Set("max_age", "stale", "userhash", "algorithm", "charset")

    def parse(value: String): Either[String, WWWAuthenticate] =
      Try {
        val challengeRegEx(scheme, challenge) = value
        val params                            = auth
          .findAllMatchIn(challenge)
          .map { m =>
            val key   = m.group(1)
            val value = Option(m.group(2)).getOrElse(m.group(3))
            key -> value
          }
          .toMap

        AuthenticationScheme.parse(scheme).map {
          case AuthenticationScheme.Basic              =>
            Basic(params.get("realm"), params.getOrElse("charset", "UTF-8"))
          case AuthenticationScheme.Bearer             =>
            Bearer(
              realm = params("realm"),
              scope = params.get("scope"),
              error = params.get("error"),
              errorDescription = params.get("error_description"),
            )
          case AuthenticationScheme.Digest             =>
            Digest(
              realm = params.get("realm"),
              domain = params.get("domain"),
              nonce = params.get("nonce"),
              opaque = params.get("opaque"),
              stale = params.get("stale").map(_.toBoolean),
              algorithm = params.get("algorithm"),
              qop = params.get("qop"),
              charset = params.get("charset"),
              userhash = params.get("userhash").map(_.toBoolean),
            )
          case AuthenticationScheme.HOBA               =>
            HOBA(
              realm = params.get("realm"),
              challenge = params("challenge"),
              maxAge = params("max_age").toInt,
            )
          case AuthenticationScheme.Mutual             =>
            Mutual(
              realm = params("realm"),
              error = params.get("error"),
              errorDescription = params.get("error_description"),
            )
          case AuthenticationScheme.Negotiate          =>
            Negotiate(Some(challenge))
          case AuthenticationScheme.Scram              =>
            SCRAM(
              realm = params("realm"),
              sid = params("sid"),
              data = params("data"),
            )
          case AuthenticationScheme.`AWS4-HMAC-SHA256` =>
            `AWS4-HMAC-SHA256`(
              realm = params("realm"),
              credentials = params.get("credentials"),
              signedHeaders = params("signedHeaders"),
              signature = params("signature"),
            )
          case _                                       =>
            Unknown(scheme, params("realm"), params)
        }
      }.toEither.left.map(_ => s"Invalid WWW-Authenticate header").flatMap {
        case Right(value) => Right(value)
        case Left(value)  => Left(value)
      }

    def render(wwwAuthenticate: WWWAuthenticate): String = {
      val (scheme, params) = wwwAuthenticate match {
        case Basic(realm, charset)                                                          =>
          "Basic" -> mutable.LinkedHashMap("realm" -> realm.getOrElse(""), charset -> charset)
        case Bearer(realm, scope, error, errorDescription)                                  =>
          "Bearer" -> mutable.LinkedHashMap(
            "realm"             -> realm,
            "scope"             -> scope.getOrElse(""),
            "error"             -> error.getOrElse(""),
            "error_description" -> errorDescription.getOrElse(""),
          )
        case Digest(realm, domain, nonce, opaque, stale, algorithm, qop, charset, userhash) =>
          "Digest" -> mutable.LinkedHashMap(
            "realm"     -> realm.getOrElse(""),
            "domain"    -> domain.getOrElse(""),
            "nonce"     -> nonce.getOrElse(""),
            "opaque"    -> opaque.getOrElse(""),
            "stale"     -> stale.getOrElse(false).toString,
            "algorithm" -> algorithm.getOrElse(""),
            "qop"       -> qop.getOrElse(""),
            "charset"   -> charset.getOrElse(""),
            "userhash"  -> userhash.getOrElse(false).toString,
          )
        case HOBA(realm, challenge, maxAge)                                                 =>
          "HOBA" -> mutable.LinkedHashMap(
            "realm"     -> realm.getOrElse(""),
            "challenge" -> challenge,
            "max_age"   -> maxAge.toString,
          )
        case Mutual(realm, error, errorDescription)                                         =>
          "Mutual" -> mutable.LinkedHashMap(
            "realm"             -> realm,
            "error"             -> error.getOrElse(""),
            "error_description" -> errorDescription.getOrElse(""),
          )
        case Negotiate(authData)                                                            =>
          "Negotiate" -> mutable.LinkedHashMap(
            "" -> authData.getOrElse(""),
          )
        case SCRAM(realm, sid, data)                                                        =>
          "SCRAM" -> mutable.LinkedHashMap(
            "realm" -> realm,
            "sid"   -> sid,
            "data"  -> data,
          )
        case `AWS4-HMAC-SHA256`(realm, credentials, signedHeaders, signature)               =>
          "AWS4-HMAC-SHA256" -> mutable.LinkedHashMap(
            "realm"         -> realm,
            "credentials"   -> credentials.getOrElse(""),
            "signedHeaders" -> signedHeaders,
            "signature"     -> signature,
          )
        case Unknown(scheme, _, params)                                                     =>
          scheme -> params
      }
      scheme + params.filter { case (_, v) => v.nonEmpty }.map { case (k, v) =>
        if (k.isEmpty) s"$v" else s"$k=${formatValue(k, v)}"
      }
        .mkString(" ", ", ", "")
    }

    private def formatValue(key: String, value: String): String = {
      if (nonQuotedValues.contains(key)) value else "\"" + value + "\""
    }
  }

  sealed trait XFrameOptions extends Header {
    override type Self = XFrameOptions
    override def self: Self                                  = this
    override def headerType: HeaderType.Typed[XFrameOptions] = XFrameOptions
  }

  object XFrameOptions extends HeaderType {
    override type HeaderValue = XFrameOptions

    override def name: String = "x-frame-options"

    case object Deny extends XFrameOptions

    case object SameOrigin extends XFrameOptions

    def parse(value: String): Either[String, XFrameOptions] = {
      value.trim.toUpperCase match {
        case "DENY"       => Right(Deny)
        case "SAMEORIGIN" => Right(SameOrigin)
        case _            => Left("Invalid X-Frame-Options header")
      }
    }

    def render(xFrameOptions: XFrameOptions): String =
      xFrameOptions match {
        case Deny       => "DENY"
        case SameOrigin => "SAMEORIGIN"
      }

  }

  final case class XRequestedWith(value: String) extends Header {
    override type Self = XRequestedWith
    override def self: Self                                   = this
    override def headerType: HeaderType.Typed[XRequestedWith] = XRequestedWith
  }

  object XRequestedWith extends HeaderType {
    override type HeaderValue = XRequestedWith
    override def name: String = "x-requested-with"

    def parse(value: String): Either[String, XRequestedWith] =
      Right(XRequestedWith(value))

    def render(xRequestedWith: XRequestedWith): String =
      xRequestedWith.value
  }

}
