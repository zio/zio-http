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

package zio.http

import java.net.URI
import java.nio.charset.{Charset, UnsupportedCharsetException}
import java.time.ZonedDateTime
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

import scala.annotation.tailrec
import scala.collection.mutable
import scala.runtime.AbstractFunction11
import scala.util.control.NonFatal
import scala.util.matching.Regex
import scala.util.{Either, Failure, Success, Try}

import zio.Config.Secret
import zio._

import zio.schema.Schema
import zio.schema.codec.DecodeError
import zio.schema.codec.DecodeError.ReadError
import zio.schema.validation.ValidationError

import zio.http.codec.{HttpCodecError, RichTextCodec}
import zio.http.internal.{DateEncoding, ErrorConstructor, StringSchemaCodec}

/**
 * An HTTP header with a typed value. Each case class or trait in the `Header`
 * companion models one header, such as `Header.ContentType` or
 * `Header.Authorization`, and `Header.Custom` holds any other header as a name
 * and a value. A header gets its name and the text of its value from its
 * `headerType`.
 *
 * {{{
 * val header: Header = Header.ContentType(MediaType.application.json)
 * header.headerName    // content-type
 * header.renderedValue // application/json
 *
 * Headers(header, Header.Custom("X-Request-Id", "42")).get(Header.ContentType).map(_.mediaType.fullType)
 * // Some(application/json)
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/headers/ Headers]]
 */
sealed trait Header {

  /** The concrete type of this header, such as `Header.Host`. */
  type Self <: Header

  /** This header, typed as `Self`. */
  def self: Self

  /**
   * The header type of this header: its name, and how its value is parsed and
   * rendered.
   */
  def headerType: Header.HeaderType.Typed[Self]

  /**
   * The header name in lower case, such as `content-type`, taken from
   * `headerType`.
   */
  def headerName: String = headerType.name

  /** The header value as text, rendered by `headerType`. */
  def renderedValue: String = headerType.render(self)

  private[http] def headerNameAsCharSequence: CharSequence    = headerName
  private[http] def renderedValueAsCharSequence: CharSequence = renderedValue

  /**
   * Returns this header as a `Custom` header with the lower-case `headerName`
   * and the rendered value.
   */
  def untyped: Header.Custom = Header.Custom(headerName, renderedValue)
}

/**
 * The typed HTTP headers, one case class or trait per header with a companion
 * that is its `HeaderType`, and the header type abstractions `HeaderTypeBase`,
 * `HeaderType` and `SchemaHeaderType`.
 */
object Header {

  private[http] def validateHeaderCharSequence(cs: CharSequence, kind: String): Unit = {
    var i   = 0
    val len = cs.length()
    while (i < len) {
      val c = cs.charAt(i)
      if (c == '\r' || c == '\n') {
        throw new IllegalArgumentException(
          s"$kind contains prohibited character at index $i: 0x${c.toInt.toHexString}",
        )
      }
      i += 1
    }
  }

  /**
   * A description of a value that is read from and written to `Headers`. A
   * `HeaderType` handles one typed header, and a `SchemaHeaderType` handles a
   * value described by a zio-schema `Schema`.
   */
  sealed trait HeaderTypeBase {

    /** The type of the value that is read and written. */
    type HeaderValue

    /** The names of the headers that are read. */
    def names: Chunk[String]

    /**
     * Reads the value from `headers`. Returns `Left` with an error message when
     * a header is missing or its value cannot be parsed.
     */
    def fromHeaders(headers: Headers): Either[String, HeaderValue]

    private[http] def fromHeadersUnsafe(headers: Headers): HeaderValue

    /**
     * Writes the value as headers. By default, a value that is a `Header` gives
     * `Headers` with that one header, and any other value gives empty
     * `Headers`.
     */
    def toHeaders(value: HeaderValue): Headers =
      value match {
        case h: Header => Headers.fromIterable(h :: Nil)
        case _         => Headers.empty
      }
  }

  /** Type aliases for `HeaderTypeBase`. */
  object HeaderTypeBase {

    /** A `HeaderTypeBase` whose `HeaderValue` is `HV`. */
    type Typed[HV] = HeaderTypeBase { type HeaderValue = HV }
  }

  /**
   * A header type that reads and writes a value with its zio-schema `Schema`: a
   * value from one named header, or a record whose fields are headers named
   * after the fields.
   */
  sealed trait SchemaHeaderType extends HeaderTypeBase {

    /** The schema of the value. */
    def schema: Schema[HeaderValue]

    /**
     * A header type for `Option[HeaderValue]`. `fromHeaders` returns
     * `Right(None)` when the headers are absent, and `toHeaders(None)` writes
     * no header.
     */
    def optional: HeaderTypeBase.Typed[Option[HeaderValue]]
  }

  /** Constructors and type aliases for `SchemaHeaderType`. */
  object SchemaHeaderType {

    /** A `SchemaHeaderType` whose `HeaderValue` is `H`. */
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

    /**
     * Creates a header type for a record schema: each field of `H` is read from
     * and written to a header named after the field. `names` are the field
     * names. The schema must be a record; for a primitive schema, `names`
     * throws a `NullPointerException`.
     */
    def apply[H](implicit schema0: Schema[H]): SchemaHeaderType.Typed[H] = {
      new SchemaHeaderType {
        type HeaderValue = H
        val schema: Schema[H]                    =
          schema0
        val codec: StringSchemaCodec[H, Headers] =
          StringSchemaCodec.headerFromSchema(schema0, errorConstructor, null)

        override def names: Chunk[String] =
          codec.recordFields.map(_._1.fieldName)

        override def optional: SchemaHeaderType.Typed[Option[H]] =
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

    /**
     * Creates a header type that reads and writes `H` from the header `name`.
     * `fromHeaders` returns a `Left` such as `Left("Missing headers x-count")`
     * when the header is absent, and a `Left` that starts with
     * `Malformed header` when its value cannot be decoded.
     *
     * {{{
     * val count = Header.SchemaHeaderType[Int]("x-count")
     * count.fromHeaders(Headers("x-count" -> "3")) // Right(3)
     * count.toHeaders(5)                            // Headers((x-count,5))
     * }}}
     */
    def apply[H](name: String)(implicit schema0: Schema[H]): SchemaHeaderType.Typed[H] = {
      new SchemaHeaderType {
        type HeaderValue = H
        val schema: Schema[H]                    = schema0
        val codec: StringSchemaCodec[H, Headers] =
          StringSchemaCodec.headerFromSchema(schema, errorConstructor, name)

        override def names: Chunk[String] =
          codec.recordFields.map(_._1.fieldName)

        override def optional: SchemaHeaderType.Typed[Option[H]] =
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

  /**
   * A typed header: its lower-case `name`, how to `parse` its value from text,
   * and how to `render` it. The companion of each header in `Header`, such as
   * `Header.ContentType`, is a `HeaderType`, and `Headers.get` takes one to
   * read a typed header.
   *
   * {{{
   * Header.ContentType.name // content-type
   * Header.Accept.parse("text/html, application/json;q=0.9").map(_.mimeTypes.size) // Right(2)
   * Headers(Header.Host("example.com")).get(Header.Host) // Some(Host(example.com,None))
   * }}}
   */
  sealed trait HeaderType extends HeaderTypeBase {

    /**
     * The header class that this header type parses and renders, such as
     * `Header.Host`.
     */
    type HeaderValue <: Header

    /** Returns `name` as the only name. */
    def names: Chunk[String] = Chunk.single(name)

    /** The header name in lower case, such as `content-type`. */
    def name: String

    /**
     * Parses the text of a header value. Returns `Left` with an error message
     * when the text is not a valid value.
     */
    def parse(value: String): Either[String, HeaderValue]

    /** Renders a value as the text of the header. */
    def render(value: HeaderValue): String

    /**
     * Finds the header named `name` in `headers`, without regard to case, and
     * parses its value. Returns a `Left` such as
     * `Left("Header host not found")` for `Header.Host` when it is missing.
     */
    def fromHeaders(headers: Headers): Either[String, HeaderValue] =
      headers.getUnsafe(name) match {
        case null  => Left(s"Header $name not found")
        case value => parse(value)
      }

    private[http] def fromHeadersUnsafe(headers: Headers): HeaderValue =
      headers.getUnsafe(name) match {
        case null  => throw HttpCodecError.MissingHeader(name)
        case value =>
          parse(value) match {
            case Left(error) => throw HttpCodecError.DecodingErrorHeader(name, ReadError(Cause.empty, error))
            case Right(v)    => v
          }
      }

    override def toHeaders(value: HeaderValue): Headers =
      Headers.FromIterable(Iterable(value))

  }

  /** Type aliases for `HeaderType`. */
  object HeaderType {

    /** A `HeaderType` whose `HeaderValue` is `HV`. */
    type Typed[HV] = HeaderType { type HeaderValue = HV }
  }

  /**
   * A header with any name and value, kept as text: use it for headers without
   * their own case class. `headerName` is the name in lower case. Names compare
   * without regard to case and values with case; `hashCode` takes the case of
   * the name into account. `toString` gives the pair `(name,value)`.
   *
   * {{{
   * val header = Header.Custom("X-Request-Id", "42")
   * header.headerName                   // x-request-id
   * header.renderedValue                // 42
   * Headers(header).get("x-request-id") // Some(42)
   * }}}
   */
  final case class Custom(customName: CharSequence, value: CharSequence) extends Header {
    override type Self = Custom
    override def self: Self = this

    private[this] var _headerType: HeaderType.Typed[Custom] = _

    override def headerType: HeaderType.Typed[Custom] = {
      var ht = _headerType
      if (ht eq null) {
        ht = new Header.HeaderType {
          override type HeaderValue = Custom

          override def name: String = self.customName.toString.toLowerCase

          override def parse(value: String): Either[String, HeaderValue] = Right(Custom(self.customName, value))

          override def render(value: HeaderValue): String = value.value.toString
        }
        _headerType = ht
      }
      ht
    }

    private[http] override def headerNameAsCharSequence: CharSequence    = {
      Header.validateHeaderCharSequence(customName, "Header name")
      customName
    }
    private[http] override def renderedValueAsCharSequence: CharSequence = {
      Header.validateHeaderCharSequence(value, "Header value")
      value
    }

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
          def eqs(l: CharSequence, r: CharSequence, caseSensitive: Boolean): Boolean = {
            if (l.length() != r.length()) false
            else {
              var i     = 0
              var equal = true

              while (i < l.length()) {
                if (
                  (caseSensitive && l.charAt(i) != r
                    .charAt(i)) || (!caseSensitive && l.charAt(i).toLower != r.charAt(i).toLower)
                ) {
                  equal = false
                  i = l.length()
                }
                i = i + 1
              }
              equal
            }
          }

          eqs(self.customName, k, caseSensitive = false) && eqs(self.value, v, caseSensitive = true)

        case _ => false
      }
    }

    override def toString: String = (customName, value).toString()
  }

  /**
   * The `Accept` request header: the media types that the client accepts, each
   * with an optional quality factor `q`.
   *
   * {{{
   * Header.Accept(MediaType.application.json, MediaType.text.html).renderedValue
   * // application/json, text/html
   * Header.Accept.parse("text/html, application/json;q=0.9").map(_.mimeTypes.map(_.qFactor))
   * // Right(NonEmptyChunk(None, Some(0.9)))
   * }}}
   */
  final case class Accept(mimeTypes: NonEmptyChunk[Accept.MediaTypeWithQFactor]) extends Header {
    override type Self = Accept
    override def self: Self                           = this
    override def headerType: HeaderType.Typed[Accept] = Accept
  }

  /**
   * The header type of `Accept`, named `accept`: parses, renders and builds
   * `Accept` values.
   */
  object Accept extends HeaderType {
    override type HeaderValue = Accept

    override def name: String = "accept"

    /**
     * One media type of an `Accept` header, with its optional quality factor
     * `q`.
     */
    final case class MediaTypeWithQFactor(mediaType: MediaType, qFactor: Option[Double])

    /** The ordering of `MediaTypeWithQFactor` values. */
    object MediaTypeWithQFactor {

      /**
       * Orders by quality factor, highest first; a missing factor counts as
       * `1.0`.
       */
      implicit val ordering: Ordering[MediaTypeWithQFactor] =
        Ordering.by[MediaTypeWithQFactor, Double](_.qFactor.getOrElse(1.0)).reverse
    }

    /**
     * Creates an `Accept` header with one media type and an optional quality
     * factor.
     */
    def apply(mediaType: MediaType, qFactor: Option[Double]): Accept =
      Accept(NonEmptyChunk(MediaTypeWithQFactor(mediaType, qFactor)))

    /**
     * Creates an `Accept` header with these media types, without quality
     * factors.
     */
    def apply(first: MediaType, rest: MediaType*): Accept =
      Accept(NonEmptyChunk(first, rest: _*).map(MediaTypeWithQFactor(_, None)))

    /**
     * Creates an `Accept` header from media types with their quality factors.
     */
    def apply(first: MediaTypeWithQFactor, rest: MediaTypeWithQFactor*): Accept =
      Accept(NonEmptyChunk(first, rest: _*))

    /**
     * Parses a comma-separated list of media types, each trimmed, with optional
     * parameters such as `q=0.9`. The quality factor comes from the `q`
     * parameter, and is `None` when it is missing or not a number. Returns
     * `Left("Invalid Accept header")` when an entry is not a media type or the
     * value is empty.
     */
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

    /**
     * Renders each media type as `type/subtype`, followed by `;q=` and the
     * quality factor when one is set, separated by `, `. Other parameters are
     * not rendered.
     */
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

    /**
     * The coding name, such as `gzip`, or `*` for `NoPreference`. For
     * `Multiple`, the names joined with commas.
     */
    val raw: String
  }

  /**
   * The header type of `Accept-Encoding`, named `accept-encoding`: parses and
   * renders `AcceptEncoding` values.
   */
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
     * Several codings, rendered separated by `,`.
     */
    final case class Multiple(encodings: NonEmptyChunk[AcceptEncoding]) extends AcceptEncoding {
      override val raw: String = encodings.map(_.raw).mkString(",")
    }

    /**
     * The `*` value: matches any content coding not already listed in the
     * header.
     */
    final case class NoPreference(weight: Option[Double]) extends AcceptEncoding {
      override val raw: String = "*"
    }

    /**
     * A coding with any other name, kept as written. `parse` gives it for an
     * unknown name, and for a known name in another case, such as `GZIP`.
     */
    final case class Unknown(value: String, weight: Option[Double] = None) extends AcceptEncoding {
      override val raw: String = value
    }

    private def identifyEncodingFull(raw: String): AcceptEncoding = {
      val index = raw.indexOf(";q=")
      if (index == -1)
        identifyEncoding(raw)
      else {
        identifyEncoding(raw.substring(0, index), weight = Try(raw.substring(index + 3).toDouble).toOption)
      }
    }

    private def identifyEncoding(raw: String, weight: Option[Double] = None): AcceptEncoding = {
      raw.trim match {
        case "br"       => Br(weight)
        case "compress" => Compress(weight)
        case "deflate"  => Deflate(weight)
        case "gzip"     => GZip(weight)
        case "identity" => Identity(weight)
        case "*"        => NoPreference(weight)
        case other      => Unknown(other, weight)
      }
    }

    /** Creates a `Multiple` with these codings, also when there is only one. */
    def apply(first: AcceptEncoding, rest: AcceptEncoding*): AcceptEncoding =
      Multiple(NonEmptyChunk(first, rest: _*))

    /**
     * Parses a comma-separated list of codings, each optionally followed by a
     * weight such as `;q=0.8`, with no space before `q`. Known names, matched
     * case-sensitively, give their case, and other entries give `Unknown`. One
     * entry gives that value, several give `Multiple`. A weight that is not a
     * number is dropped. It returns `Right` for any input.
     */
    def parse(value: String): Either[String, AcceptEncoding] = {
      val index = value.indexOf(",")

      @tailrec def loop(
        value: String,
        index: Int,
        acc: Chunk[AcceptEncoding],
      ): Chunk[AcceptEncoding] = {
        if (index == -1) {
          acc :+ identifyEncodingFull(value)
        } else {
          val valueChunk = value.substring(0, index)
          val remaining  = value.substring(index + 1)
          val nextIndex  = remaining.indexOf(",")

          loop(
            remaining,
            nextIndex,
            acc :+ identifyEncodingFull(valueChunk),
          )
        }
      }

      if (index == -1)
        Right(identifyEncodingFull(value))
      else
        NonEmptyChunk.fromChunk(loop(value, index, Chunk.empty[AcceptEncoding])) match {
          case Some(value) => Right(Multiple(value))
          case None        => Left(s"Invalid accept encoding ($value)")
        }
    }

    /**
     * Renders each coding name, with a weight suffix such as `;q=0.8` when a
     * weight is set, and separates the entries of `Multiple` with `,`.
     */
    def render(encoding: AcceptEncoding): String =
      encoding match {
        case b @ Br(weight)           => weight.fold(b.raw)(value => s"${b.raw};q=$value")
        case c @ Compress(weight)     => weight.fold(c.raw)(value => s"${c.raw};q=$value")
        case d @ Deflate(weight)      => weight.fold(d.raw)(value => s"${d.raw};q=$value")
        case g @ GZip(weight)         => weight.fold(g.raw)(value => s"${g.raw};q=$value")
        case i @ Identity(weight)     => weight.fold(i.raw)(value => s"${i.raw};q=$value")
        case Multiple(encodings)      => encodings.map(render).mkString(",")
        case n @ NoPreference(weight) => weight.fold(n.raw)(value => s"${n.raw};q=$value")
        case Unknown(u, weight)       => weight.fold(u)(value => s"$u;q=$value")
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

  /**
   * The header type of `Accept-Language`, named `accept-language`: parses and
   * renders `AcceptLanguage` values.
   */
  object AcceptLanguage extends HeaderType {
    override type HeaderValue = AcceptLanguage

    override def name: String = "accept-language"

    /** One language tag, such as `en-US`, with an optional weight. */
    case class Single(language: String, weight: Option[Double]) extends AcceptLanguage

    /**
     * Several languages, rendered separated by `,`. `parse` returns this case
     * for any value other than `*`, even for one language.
     */
    case class Multiple(languages: NonEmptyChunk[AcceptLanguage]) extends AcceptLanguage

    /** The `*` value: any language. */
    case object Any extends AcceptLanguage

    /**
     * Parses `*` as `Any`, and other values as a `Multiple` of comma-separated,
     * trimmed languages, each with an optional weight such as `;q=0.9`; a
     * weight that is not a number from 0 to 1 is dropped. Returns
     * `Left("Accept-Language contains invalid characters")` when the value is
     * empty or has characters other than letters, digits, spaces and `*,-.;=`.
     */
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

    /**
     * Renders `*` for `Any`, each language as its tag, with a weight suffix
     * such as `;q=0.9` when set, and the languages of `Multiple` separated by
     * `,`.
     */
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

  /**
   * The header type of `Accept-Patch`, named `accept-patch`: parses and renders
   * `AcceptPatch` values.
   */
  object AcceptPatch extends HeaderType {
    override type HeaderValue = AcceptPatch

    override def name: String = "accept-patch"

    /**
     * Parses a comma-separated list of media types and drops the entries that
     * are not media types. The entries are not trimmed, so a space after a
     * comma becomes part of the next main type. Returns
     * `Left("Accept-Patch header cannot be empty")` for an empty value and
     * `Left("Invalid Accept-Patch header")` when no media type is left.
     */
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

    /** Renders the media types as `type/subtype`, separated by `,`. */
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

    /** The rendered value: `bytes` or `none`. */
    val encodedName: String
  }

  /**
   * The header type of `Accept-Ranges`, named `accept-ranges`: parses and
   * renders `AcceptRanges` values.
   */
  object AcceptRanges extends HeaderType {
    override type HeaderValue = AcceptRanges
    override def name: String = "accept-ranges"

    /** Range requests in bytes are supported. Rendered as `bytes`. */
    case object Bytes extends AcceptRanges {
      override val encodedName = "bytes"
    }

    /** Range requests are not supported. Rendered as `none`. */
    case object None extends AcceptRanges {
      override val encodedName = "none"
    }

    /**
     * Parses `bytes` or `none`, case-sensitively. Returns
     * `Left("Invalid Accept-Ranges header")` otherwise.
     */
    def parse(value: String): Either[String, AcceptRanges] =
      value match {
        case Bytes.encodedName => Right(Bytes)
        case None.encodedName  => Right(None)
        case _                 => Left("Invalid Accept-Ranges header")
      }

    /** Renders `encodedName`. */
    def render(acceptRangers: AcceptRanges): String =
      acceptRangers.encodedName
  }

  /**
   * The `Access-Control-Allow-Credentials` response header: whether the browser
   * may expose the response to a cross-origin request made with credentials.
   */
  sealed trait AccessControlAllowCredentials extends Header {
    override type Self = AccessControlAllowCredentials
    override def self: Self                                                  = this
    override def headerType: HeaderType.Typed[AccessControlAllowCredentials] = AccessControlAllowCredentials
  }

  /**
   * The header type of `Access-Control-Allow-Credentials`, named
   * `access-control-allow-credentials`: parses and renders
   * `AccessControlAllowCredentials` values.
   */
  object AccessControlAllowCredentials extends HeaderType {
    override type HeaderValue = AccessControlAllowCredentials

    override def name: String = "access-control-allow-credentials"

    /**
     * Credentials are allowed. Rendered as `true`. In response to a preflight
     * request, it tells whether the actual request can be made with
     * credentials.
     */
    case object Allow extends AccessControlAllowCredentials

    /**
     * Credentials are not allowed. Rendered as `false`; `parse` returns it for
     * any value other than `true`.
     */
    case object DoNotAllow extends AccessControlAllowCredentials

    /** Returns `Allow` for `true` and `DoNotAllow` for `false`. */
    def allow(value: Boolean): AccessControlAllowCredentials =
      value match {
        case true  => Allow
        case false => DoNotAllow
      }

    /**
     * Returns `Allow` for `true` and `DoNotAllow` for any other value, so it
     * does not fail.
     */
    def parse(value: String): Either[String, AccessControlAllowCredentials] =
      Right {
        value match {
          case "true"  => Allow
          case "false" => DoNotAllow
          case _       => DoNotAllow
        }
      }

    /** Renders `true` for `Allow` and `false` for `DoNotAllow`. */
    def render(
      accessControlAllowCredentials: AccessControlAllowCredentials,
    ): String =
      accessControlAllowCredentials match {
        case Allow      => "true"
        case DoNotAllow => "false"
      }
  }

  /**
   * The `Access-Control-Allow-Headers` response header: the request headers
   * that a cross-origin request may use.
   */
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

    /** A list of header names, rendered separated by `, `. */
    final case class Some(values: NonEmptyChunk[String]) extends AccessControlAllowHeaders

    /** The `*` wildcard. */
    case object All extends AccessControlAllowHeaders

    /** No header name, rendered as an empty value. */
    case object None extends AccessControlAllowHeaders

    /** Creates `Some` with these header names, or `None` when there is none. */
    def apply(headers: String*) =
      NonEmptyChunk.fromIterableOption(headers) match {
        case scala.Some(value) => Some(value)
        case scala.None        => None
      }

    /**
     * Parses an empty value as `None`, `*` as `All`, and other values as `Some`
     * of comma-separated, trimmed names. It does not fail.
     */
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

    /**
     * Renders the names separated by `, `, `*` for `All`, or an empty string
     * for `None`.
     */
    def render(accessControlAllowHeaders: AccessControlAllowHeaders): String =
      accessControlAllowHeaders match {
        case Some(value) => value.mkString(", ")
        case All         => "*"
        case None        => ""
      }

  }

  /**
   * The `Access-Control-Allow-Methods` response header: the methods that a
   * cross-origin request may use.
   */
  sealed trait AccessControlAllowMethods extends Header {
    override type Self = AccessControlAllowMethods
    override def self: Self                                              = this
    override def headerType: HeaderType.Typed[AccessControlAllowMethods] = AccessControlAllowMethods

    /**
     * Returns `true` when `method` is allowed: for `All` it is `true`, for
     * `None` it is `false`, and for `Some` it is whether the list has `method`.
     */
    def contains(method: Method): Boolean =
      this match {
        case AccessControlAllowMethods.All           => true
        case AccessControlAllowMethods.Some(methods) => methods.contains(method)
        case AccessControlAllowMethods.None          => false
      }
  }

  /**
   * The header type of `Access-Control-Allow-Methods`, named
   * `access-control-allow-methods`: parses and renders
   * `AccessControlAllowMethods` values.
   */
  object AccessControlAllowMethods extends HeaderType {
    override type HeaderValue = AccessControlAllowMethods

    override def name: String = "access-control-allow-methods"

    /** A list of methods, rendered separated by `, `. */
    final case class Some(methods: NonEmptyChunk[Method]) extends AccessControlAllowMethods

    /** The `*` wildcard. */
    case object All extends AccessControlAllowMethods

    /** No method, rendered as an empty value. */
    case object None extends AccessControlAllowMethods

    /** Creates `Some` with these methods, or `None` when there is none. */
    def apply(methods: Method*): AccessControlAllowMethods =
      NonEmptyChunk.fromIterableOption(methods) match {
        case scala.Some(value) => Some(value)
        case scala.None        => None
      }

    /**
     * Parses an empty value as `None`, `*` as `All`, and other values as `Some`
     * of comma-separated, trimmed methods; a name that is not a standard method
     * is kept as a custom method. It does not fail.
     */
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

    /**
     * Renders the method names separated by `, `, `*` for `All`, or an empty
     * string for `None`.
     */
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
   * An origin: specifies an origin. Only a single origin can be specified. If
   * the server supports clients from multiple origins, it must return the
   * origin for the specific client making the request.
   *
   * null Specifies the origin "null".
   */
  sealed trait AccessControlAllowOrigin extends Header {
    override type Self = AccessControlAllowOrigin
    override def self: Self                                             = this
    override def headerType: HeaderType.Typed[AccessControlAllowOrigin] = AccessControlAllowOrigin
  }

  /**
   * The header type of `Access-Control-Allow-Origin`, named
   * `access-control-allow-origin`: parses and renders
   * `AccessControlAllowOrigin` values.
   */
  object AccessControlAllowOrigin extends HeaderType {
    override type HeaderValue = AccessControlAllowOrigin

    override def name: String = "access-control-allow-origin"

    /** One origin, rendered as `Origin.render` does. */
    final case class Specific(origin: Origin) extends AccessControlAllowOrigin

    /** The `*` wildcard. */
    case object All extends AccessControlAllowOrigin

    /**
     * Creates a `Specific` origin from a scheme, a host and an optional port.
     */
    def apply(scheme: String, host: String, port: Option[Int] = None): AccessControlAllowOrigin =
      Specific(Origin(scheme, host, port))

    /**
     * Parses `*` as `All`, and other values as `Origin.parse` does, wrapped in
     * `Specific`. Returns `Left("Invalid Origin header")` when the origin is
     * not valid.
     */
    def parse(value: String): Either[String, AccessControlAllowOrigin] = {
      if (value == "*") {
        Right(AccessControlAllowOrigin.All)
      } else {
        Origin.parse(value).map { origin =>
          AccessControlAllowOrigin.Specific(origin)
        }
      }
    }

    /** Renders `*` for `All`, or the origin for `Specific`. */
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

  /**
   * The header type of `Access-Control-Expose-Headers`, named
   * `access-control-expose-headers`: parses and renders
   * `AccessControlExposeHeaders` values.
   */
  object AccessControlExposeHeaders extends HeaderType {
    override type HeaderValue = AccessControlExposeHeaders

    override def name: String = "access-control-expose-headers"

    /** A list of header names, rendered separated by `, `. */
    final case class Some(values: NonEmptyChunk[CharSequence]) extends AccessControlExposeHeaders

    /** The `*` wildcard. */
    case object All extends AccessControlExposeHeaders

    /** No header name, rendered as an empty value. */
    case object None extends AccessControlExposeHeaders

    /**
     * Parses an empty value as `None`, `*` as `All`, and other values as `Some`
     * of comma-separated, trimmed names. It does not fail.
     */
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

    /**
     * Renders the names separated by `, `, `*` for `All`, or an empty string
     * for `None`.
     */
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

  /**
   * The header type of `Access-Control-Max-Age`, named
   * `access-control-max-age`: parses and renders `AccessControlMaxAge` values.
   */
  object AccessControlMaxAge extends HeaderType {
    override type HeaderValue = AccessControlMaxAge

    override def name: String = "access-control-max-age"

    /**
     * Parses a non-negative whole number of seconds. Returns
     * `Left("Invalid Access-Control-Max-Age header value")` otherwise.
     */
    def parse(seconds: String): Either[String, AccessControlMaxAge] =
      Try(seconds.toLong).toOption.flatMap { long =>
        if (long > -1) Some(AccessControlMaxAge(long.seconds))
        else None
      }.toRight("Invalid Access-Control-Max-Age header value")

    /** Renders the duration in whole seconds. */
    def render(accessControlMaxAge: AccessControlMaxAge): String = {
      accessControlMaxAge.duration.getSeconds.toString
    }
  }

  /**
   * The `Access-Control-Request-Headers` request header that a browser sends in
   * a preflight request: the headers that the actual request will have.
   */
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

    /**
     * Trims the value and splits it on commas, dropping empty entries; the
     * entries themselves are not trimmed. Returns
     * `Left("AccessControlRequestHeaders cannot be empty")` when none is left.
     */
    def parse(values: String): Either[String, AccessControlRequestHeaders] = {
      NonEmptyChunk.fromChunk(Chunk.fromArray(values.trim().split(",")).filter(_.nonEmpty)) match {
        case None     => Left("AccessControlRequestHeaders cannot be empty")
        case Some(xs) => Right(AccessControlRequestHeaders(xs))
      }
    }

    /** Renders the header names separated by `,`. */
    def render(headers: AccessControlRequestHeaders): String =
      headers.values.mkString(",")
  }

  /**
   * The `Access-Control-Request-Method` request header that a browser sends in
   * a preflight request: the method of the actual request.
   */
  final case class AccessControlRequestMethod(method: Method) extends Header {
    override type Self = AccessControlRequestMethod
    override def self: Self                                               = this
    override def headerType: HeaderType.Typed[AccessControlRequestMethod] = AccessControlRequestMethod
  }

  /**
   * The header type of `Access-Control-Request-Method`, named
   * `access-control-request-method`: parses and renders
   * `AccessControlRequestMethod` values.
   */
  object AccessControlRequestMethod extends HeaderType {
    override type HeaderValue = AccessControlRequestMethod

    override def name: String = "access-control-request-method"

    /**
     * Parses a standard method name, such as `POST`; lower case is accepted.
     * Returns `Left("Invalid Access-Control-Request-Method")` for any other
     * name.
     */
    def parse(value: String): Either[String, AccessControlRequestMethod] = {
      val method = Method.fromString(value)
      if (method == Method.CUSTOM(value)) Left(s"Invalid Access-Control-Request-Method")
      else Right(AccessControlRequestMethod(method))
    }

    /** Renders the method name. */
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

  /** The header type of `Age`, named `age`: parses and renders `Age` values. */
  object Age extends HeaderType {
    override type HeaderValue = Age

    override def name: String = "age"

    /**
     * Parses a positive whole number of seconds, with surrounding whitespace
     * allowed. Returns `Left("Negative Age")` for zero or a negative number,
     * and `Left("Invalid Age")` for a value that is not an `Int`.
     */
    def parse(value: String): Either[String, Age] =
      Try(value.trim.toInt) match {
        case Failure(_)                  => Left(s"Invalid Age")
        case Success(value) if value > 0 => Right(Age(value.seconds))
        case Success(_)                  => Left(s"Negative Age")
      }

    /** Renders the duration in whole seconds. */
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

  /**
   * The header type of `Allow`, named `allow`: parses and renders `Allow`
   * values, with a constant for each standard method.
   */
  object Allow extends HeaderType {
    override type HeaderValue = Allow

    override def name: String = "allow"

    /** An `Allow` header with only `OPTIONS`. */
    val OPTIONS: Allow = Allow(NonEmptyChunk.single(Method.OPTIONS))

    /** An `Allow` header with only `GET`. */
    val GET: Allow = Allow(NonEmptyChunk.single(Method.GET))

    /** An `Allow` header with only `HEAD`. */
    val HEAD: Allow = Allow(NonEmptyChunk.single(Method.HEAD))

    /** An `Allow` header with only `POST`. */
    val POST: Allow = Allow(NonEmptyChunk.single(Method.POST))

    /** An `Allow` header with only `PUT`. */
    val PUT: Allow = Allow(NonEmptyChunk.single(Method.PUT))

    /** An `Allow` header with only `PATCH`. */
    val PATCH: Allow = Allow(NonEmptyChunk.single(Method.PATCH))

    /** An `Allow` header with only `DELETE`. */
    val DELETE: Allow = Allow(NonEmptyChunk.single(Method.DELETE))

    /** An `Allow` header with only `TRACE`. */
    val TRACE: Allow = Allow(NonEmptyChunk.single(Method.TRACE))

    /** An `Allow` header with only `CONNECT`. */
    val CONNECT: Allow = Allow(NonEmptyChunk.single(Method.CONNECT))

    /**
     * Parses a comma-separated list of standard method names, such as
     * `GET, POST`, trimming each one; lower-case names are accepted. Returns
     * `Left("Invalid Allow header: empty value")` for an empty value, and
     * `Left` with a message that starts with `Invalid Allow method: ` for a
     * name that is not a standard method or is empty between two commas. A
     * trailing comma gives `Left("Invalid Allow header: empty value")`.
     */
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

    /** Renders the method names separated by `, `. */
    def render(allow: Allow): String =
      allow.methods.map(_.name).mkString(", ")

  }

  /**
   * An HTTP authentication scheme, such as `Basic` or `Bearer`, used by
   * `ProxyAuthenticate`, `ProxyAuthorization` and `WWWAuthenticate`.
   */
  sealed trait AuthenticationScheme {

    /** The scheme name as it is rendered, such as `Basic`. */
    val name: String
  }

  /** The supported authentication schemes, and their parser and renderer. */
  object AuthenticationScheme {

    /** The `Basic` scheme. */
    case object Basic extends AuthenticationScheme {
      override val name: String = "Basic"
    }

    /** The `Bearer` scheme. */
    case object Bearer extends AuthenticationScheme {
      override val name: String = "Bearer"
    }

    /** The `Digest` scheme. */
    case object Digest extends AuthenticationScheme {
      override val name: String = "Digest"
    }

    /** The `HOBA` scheme. */
    case object HOBA extends AuthenticationScheme {
      override val name: String = "HOBA"
    }

    /** The `Mutual` scheme. */
    case object Mutual extends AuthenticationScheme {
      override val name: String = "Mutual"
    }

    /** The `Negotiate` scheme. */
    case object Negotiate extends AuthenticationScheme {
      override val name: String = "Negotiate"
    }

    /** The `OAuth` scheme. */
    case object OAuth extends AuthenticationScheme {
      override val name: String = "OAuth"
    }

    /** The `SCRAM` scheme. */
    case object Scram extends AuthenticationScheme {
      override val name: String = "SCRAM"
    }

    /** The `SCRAM-SHA-1` scheme. */
    case object ScramSha1 extends AuthenticationScheme {
      override val name: String = "SCRAM-SHA-1"
    }

    /** The `SCRAM-SHA-256` scheme. */
    case object ScramSha256 extends AuthenticationScheme {
      override val name: String = "SCRAM-SHA-256"
    }

    /** The `vapid` scheme. */
    case object Vapid extends AuthenticationScheme {
      override val name: String = "vapid"
    }

    /** The `AWS4-HMAC-SHA256` scheme. */
    case object `AWS4-HMAC-SHA256` extends AuthenticationScheme {
      override val name: String = "AWS4-HMAC-SHA256"
    }

    /**
     * Parses a scheme name, trimmed and without regard to case. Returns `Left`
     * with a message that starts with `Unsupported authentication scheme: ` for
     * an unknown name.
     */
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

    /** Returns the scheme `name`. */
    def render(authenticationScheme: AuthenticationScheme): String =
      authenticationScheme.name

  }

  /**
   * Authorization header value.
   *
   * The Authorization header value contains one of the auth schemes: `Basic`,
   * `Digest`, `Bearer`, or `Unparsed` for any other scheme. Passwords and
   * tokens are kept as `zio.Config.Secret`.
   *
   * {{{
   * Header.Authorization.Basic("user", "pass").renderedValue // Basic dXNlcjpwYXNz
   * Header.Authorization.Bearer("abc123").renderedValue      // Bearer abc123
   * Header.Authorization.parse("Basic dXNlcjpwYXNz")
   * // Right(Basic(user,Secret(<redacted>)))
   * }}}
   */
  sealed trait Authorization extends Header {
    override type Self = Authorization
    override def self: Self                                  = this
    override def headerType: HeaderType.Typed[Authorization] = Authorization
  }

  /**
   * The header type of `Authorization`, named `authorization`: parses and
   * renders `Authorization` values of every scheme.
   */
  object Authorization extends HeaderType {
    override type HeaderValue = Authorization

    override def name: String = "authorization"

    /** `Basic` credentials: a user name and a password. */
    final case class Basic(username: String, password: Secret) extends Authorization

    /**
     * Constructors for `Basic` credentials, and a header type, named
     * `authorization`, for `Basic` values only.
     */
    object Basic extends HeaderType {

      /** Creates `Basic` credentials from a plain-text password. */
      def apply(username: String, password: String): Basic = new Basic(username, Secret(password))

      override def name: String = "authorization"

      override type HeaderValue = Authorization.Basic

      /**
       * Decodes `value` as the Base64 text `username:password`, the part that
       * follows `Basic ` in the header. The user name must not be empty, and
       * the password is everything after the first `:`. Returns
       * `Left("Basic Authorization header value is not a valid base64 encoded string")`
       * or
       * `Left("Basic Authorization header value is not in the format username:password")`
       * otherwise.
       */
      def parse(value: String): Either[String, Authorization.Basic] = {
        parseBasic(value).asInstanceOf[Either[String, Authorization.Basic]]
      }

      /**
       * Renders a `Basic` header. The value of `renderedValue` is `Basic `
       * followed by the Base64 encoding of `username`, `:` and the password.
       */
      def render(header: Authorization.Basic): String = {
        val encoded = Base64.getEncoder.encodeToString((header.username + ":" + header.password.value).getBytes)
        s"Basic $encoded"
      }
    }

    /**
     * `Digest` credentials, with the parameters of the Digest scheme.
     */
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

    /**
     * Constructors for `Digest` credentials, and a header type, named
     * `authorization`, for `Digest` values only.
     */
    object Digest
        extends AbstractFunction11[
          String,
          String,
          String,
          URI,
          String,
          String,
          String,
          String,
          String,
          Int,
          Boolean,
          Digest,
        ]
        with HeaderType {

      /** Creates `Digest` credentials from their parameters. */
      def apply(
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
      ): Digest =
        new Digest(response, username, realm, uri, opaque, algorithm, qop, cnonce, nonce, nc, userhash)

      override def name: String = "authorization"

      override type HeaderValue = Authorization.Digest

      /**
       * Parses the comma-separated `key=value` parameters of Digest
       * credentials, the part that follows `Digest ` in the header. `response`,
       * `realm`, `uri`, `opaque`, `algorithm`, `qop`, `cnonce`, `nonce` and a
       * hexadecimal `nc` are required, with exactly one of `username` and
       * `username*` (the latter only when `userhash` is not `true`). Returns
       * `Left("Digest Authorization header value is not in the correct format")`
       * otherwise.
       */
      def parse(value: String): Either[String, Authorization.Digest] = {
        parseDigest(value).asInstanceOf[Either[String, Authorization.Digest]]
      }

      /**
       * Renders `Digest ` followed by all parameters, separated by `,` without
       * spaces; `response`, `username`, `realm`, `opaque`, `cnonce` and `nonce`
       * are quoted.
       */
      def render(header: Digest): String = {
        s"""Digest response="${header.response}",username="${header.username}",realm="${header.realm}",uri=${header.uri.toString},opaque="${header.opaque}",algorithm=${header.algorithm},""" +
          s"""qop=${header.qop},cnonce="${header.cnonce}",nonce="${header.nonce}",nc=${header.nc},userhash=${header.userhash.toString}"""
      }
    }

    /** `Bearer` credentials: a token. */
    final case class Bearer(token: Secret) extends Authorization

    /**
     * Constructors for `Bearer` credentials, and a header type, named
     * `authorization`, for `Bearer` values only.
     */
    object Bearer extends HeaderType {

      /** Creates `Bearer` credentials from a plain-text token. */
      def apply(token: String): Bearer = Bearer(Secret(token))

      override def name: String = "authorization"

      override type HeaderValue = Authorization.Bearer

      /**
       * Reads the token from a value made of two space-separated parts, such as
       * `Bearer abc123`. Returns
       * `Left("Invalid Bearer Authorization header value")` when there are not
       * exactly two parts.
       */
      def parse(value: String): Either[String, Authorization.Bearer] = {
        val parts = value.split(" ").filter(_.nonEmpty)
        if (parts.length == 2) {
          Right(Bearer(Secret(parts(1))))
        } else {
          Left("Invalid Bearer Authorization header value")
        }
      }

      /**
       * Renders a `Bearer` header. The value of `renderedValue` is `Bearer `
       * followed by the token.
       */
      def render(header: Authorization.Bearer): String = s"Bearer ${header.token.value}"
    }

    /**
     * Credentials of a scheme without its own case, kept as the scheme name and
     * the rest of the value. `authScheme` is empty when the header value has a
     * single word.
     */
    final case class Unparsed(authScheme: String, authParameters: Secret) extends Authorization

    /** Constructors for `Unparsed` credentials. */
    object Unparsed {

      /** Creates `Unparsed` credentials from plain-text parameters. */
      def apply(authScheme: String, authParameters: String): Unparsed = Unparsed(authScheme, Secret(authParameters))
    }

    /**
     * Parses the value by its scheme, matched without regard to case: `Basic`
     * and `Digest` as their companions' `parse` does, and `Bearer` with the
     * word that follows it. Another scheme gives `Unparsed` with the rest of
     * the value, and a single word gives `Unparsed` with an empty scheme.
     * Returns `Left` with a message that starts with
     * `Invalid Authorization header value: ` for an empty or blank value.
     */
    def parse(value: String): Either[String, Authorization] = {
      val parts  = value.split(" ").filter(_.nonEmpty)
      val nParts = parts.length
      if (nParts == 1) {
        Right(Unparsed("", parts(0)))
      } else if (nParts >= 2) {
        parts(0).toLowerCase match {
          case "basic"  => parseBasic(parts(1))
          case "digest" => parseDigest(parts.tail.mkString(" "))
          case "bearer" => Right(Bearer(parts(1)))
          case _        => Right(Unparsed(parts(0), parts.tail.mkString(" ")))
        }
      } else Left(s"Invalid Authorization header value: $value")
    }

    /**
     * Renders the credentials with their scheme, for example
     * `Basic dXNlcjpwYXNz` for the user `user` and the password `pass`, or
     * `Bearer` followed by the token. `Unparsed` renders as the scheme, a space
     * and the parameters, trimmed.
     */
    def render(header: Authorization): String = header match {
      case Basic(username, password) =>
        s"Basic ${Base64.getEncoder.encodeToString((s"$username:" ++: password.value).map(_.toByte).toArray)}"

      case Digest(response, username, realm, uri, opaque, algo, qop, cnonce, nonce, nc, userhash) =>
        s"""Digest response="$response",username="$username",realm="$realm",uri=${uri.toString},opaque="$opaque",algorithm=$algo,""" +
          s"""qop=$qop,cnonce="$cnonce",nonce="$nonce",nc=$nc,userhash=${userhash.toString}"""
      case Bearer(token)            => s"Bearer ${token.value.asString}"
      case Unparsed(scheme, params) => s"$scheme ${params.value.asString}".strip()
    }

    private def parseBasic(value: String): Either[String, Authorization] = {
      try {
        val decoded      = new String(Base64.getDecoder.decode(value))
        val indexOfColon = decoded.indexOf(":")

        if (indexOfColon > 0) {
          // Extract username as everything before the first ":", and password as everything after
          val username = decoded.substring(0, indexOfColon)
          val password = decoded.substring(indexOfColon + 1)
          Right(Basic(username, Secret(password)))
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
          nc            <- params.get("nc").flatMap(v => Try(Integer.parseInt(v, 16)).toOption)
        } yield Digest(response, usernameFinal, realm, uri, opaque, algo, qop, cnonce, nonce, nc, userhash)

        maybeDigest
          .toRight("Digest Authorization header value is not in the correct format")
      } catch {
        case _: IndexOutOfBoundsException =>
          Left("Digest Authorization header value is not in the correct format")
      }
  }

  /**
   * CacheControl header value: one directive, such as `NoCache` or `MaxAge`, or
   * several in a `Multiple`.
   *
   * {{{
   * import zio.NonEmptyChunk
   *
   * val header = Header.CacheControl.Multiple(
   *   NonEmptyChunk(Header.CacheControl.Public, Header.CacheControl.MaxAge(3600)),
   * )
   * header.renderedValue // public,max-age=3600
   *
   * Header.CacheControl.parse("no-cache, no-store")
   * // Right(Multiple(NonEmptyChunk(NoCache, NoStore)))
   * }}}
   */
  sealed trait CacheControl extends Header {
    override type Self = CacheControl
    override def self: Self                                 = this
    override def headerType: HeaderType.Typed[CacheControl] = CacheControl

    /**
     * The directive name, such as `max-age`. For `Multiple`, the names joined
     * with commas.
     */
    val raw: String
  }

  /**
   * The header type of `Cache-Control`, named `cache-control`: parses and
   * renders `CacheControl` values.
   */
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
     * Several directives, rendered separated by `,`.
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

    /**
     * Parses one directive, or several separated by commas into a `Multiple`.
     * Directive names are case-sensitive; a directive that takes seconds, such
     * as `max-age`, gets `0` when its value is missing or not a number. Returns
     * `Left` with a message that starts with `Unknown cache control value: `
     * for an unknown or empty directive.
     */
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

    /**
     * Renders each directive by its name, as `name=seconds` for directives with
     * seconds, and separates the directives of `Multiple` with `,` and no
     * space.
     */
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
   * The `Clear-Site-Data` response header: the kinds of browsing data, such as
   * cookies or cache, that the browser should clear for the site.
   */
  final case class ClearSiteData(directives: NonEmptyChunk[ClearSiteDataDirective]) extends Header {
    override type Self = ClearSiteData
    override def self: Self                                  = this
    override def headerType: HeaderType.Typed[ClearSiteData] = ClearSiteData
  }

  /**
   * The header type of `Clear-Site-Data`, named `clear-site-data`: parses and
   * renders `ClearSiteData` values.
   */
  object ClearSiteData extends HeaderType {
    override type HeaderValue = ClearSiteData

    override def name: String = "clear-site-data"

    /**
     * Parses a comma-separated list of quoted directives, such as
     * `"cache", "cookies"`. Quoted directives with unknown names are dropped.
     * Returns `Left("Invalid Clear-Site-Data header")` when a value is not
     * between double quotes or no known directive is left.
     */
    def parse(value: String): Either[String, ClearSiteData] = {
      val values     = value.split(",").map(_.trim)
      val directives = values.flatMap { directive =>
        directive match {
          case """"cache""""             => Some(ClearSiteDataDirective.Cache)
          case """"clientHints""""       => Some(ClearSiteDataDirective.ClientHints)
          case """"cookies""""           => Some(ClearSiteDataDirective.Cookies)
          case """"storage""""           => Some(ClearSiteDataDirective.Storage)
          case """"executionContexts"""" => Some(ClearSiteDataDirective.ExecutionContexts)
          case """"*""""                 => Some(ClearSiteDataDirective.All)
          case _                         => None
        }
      }

      if (values.exists(x => !x.headOption.contains('"') || !x.lastOption.contains('"')))
        Left("Invalid Clear-Site-Data header")
      else {
        NonEmptyChunk.fromIterableOption(directives) match {
          case Some(directives) => Right(ClearSiteData(directives))
          case None             => Left("Invalid Clear-Site-Data header")
        }
      }
    }

    /** Renders the directives between double quotes, separated by `, `. */
    def render(clearSiteData: ClearSiteData): String =
      clearSiteData.directives.map {
        case ClearSiteDataDirective.Cache             => """"cache""""
        case ClearSiteDataDirective.ClientHints       => """"clientHints""""
        case ClearSiteDataDirective.Cookies           => """"cookies""""
        case ClearSiteDataDirective.Storage           => """"storage""""
        case ClearSiteDataDirective.ExecutionContexts => """"executionContexts""""
        case ClearSiteDataDirective.All               => """"*""""
      }.mkString(", ")
  }

  /** A kind of data in a `Clear-Site-Data` header. */
  sealed trait ClearSiteDataDirective

  /** The `Clear-Site-Data` directives. */
  object ClearSiteDataDirective {

    /** The `"cache"` directive. */
    case object Cache extends ClearSiteDataDirective

    /** The `"clientHints"` directive. */
    case object ClientHints extends ClearSiteDataDirective

    /** The `"cookies"` directive. */
    case object Cookies extends ClearSiteDataDirective

    /** The `"storage"` directive. */
    case object Storage extends ClearSiteDataDirective

    /** The `"executionContexts"` directive. */
    case object ExecutionContexts extends ClearSiteDataDirective

    /** The `"*"` directive: all kinds of data. */
    case object All extends ClearSiteDataDirective
  }

  /**
   * Connection header value.
   */
  sealed trait Connection extends Header {
    override type Self = Connection
    override def self: Self                               = this
    override def headerType: HeaderType.Typed[Connection] = Connection

    /** The rendered value: `close` or `keep-alive`. */
    val value: String
  }

  /**
   * The header type of `Connection`, named `connection`: parses and renders
   * `Connection` values.
   */
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
     * The `keep-alive` value: the sender would like to keep the connection
     * open. Keeping a connection open is the default on HTTP/1.1 requests.
     */
    case object KeepAlive extends Connection {
      override val value: String = "keep-alive"
    }

    /**
     * Parses `close` or `keep-alive`, trimmed and without regard to case.
     * Returns `Left("Invalid Connection")` for any other value, including a
     * list.
     */
    def parse(connection: String): Either[String, Connection] = {
      connection.trim.toLowerCase() match {
        case Close.value     => Right(Close)
        case KeepAlive.value => Right(KeepAlive)
        case _               => Left("Invalid Connection")
      }
    }

    /** Renders `close` or `keep-alive`. */
    def render(connection: Connection): String = connection.value
  }

  /**
   * The `Content-Base` header: the base URI for resolving relative URIs in the
   * body.
   */
  final case class ContentBase(uri: URI) extends Header {
    override type Self = ContentBase
    override def self: Self                                = this
    override def headerType: HeaderType.Typed[ContentBase] = ContentBase
  }

  /**
   * The header type of `Content-Base`, named `content-base`: parses and renders
   * `ContentBase` values.
   */
  object ContentBase extends HeaderType {
    override type HeaderValue = ContentBase

    override def name: String = "content-base"

    /**
     * Parses the value as a `java.net.URI`. Returns
     * `Left("Invalid Content-Base header")` when it is not valid.
     */
    def parse(s: String): Either[String, ContentBase] =
      Try(ContentBase(new java.net.URI(s))).toEither.left.map(_ => "Invalid Content-Base header")

    /** Renders the URI with `toString`. */
    def render(cb: ContentBase): String =
      cb.uri.toString

    /** Creates a `ContentBase` from this URI. */
    def uri(uri: URI): ContentBase = ContentBase(uri)
  }

  /**
   * The `Content-Disposition` header: whether the body is shown inline or
   * downloaded as an attachment, or, in a multipart form, the name of the form
   * field.
   *
   * {{{
   * Header.ContentDisposition.attachment("report.pdf").renderedValue // attachment; filename=report.pdf
   * Header.ContentDisposition.parse("form-data; name=\"file\"; filename=\"a.txt\"")
   * // Right(FormField(file,Some(a.txt)))
   * }}}
   */
  sealed trait ContentDisposition extends Header {
    override type Self = ContentDisposition
    override def self: Self                                       = this
    override def headerType: HeaderType.Typed[ContentDisposition] = ContentDisposition
  }

  /**
   * The header type of `Content-Disposition`, named `content-disposition`:
   * parses, renders and builds `ContentDisposition` values.
   */
  object ContentDisposition extends HeaderType {
    override type HeaderValue = ContentDisposition

    override def name: String = "content-disposition"

    /**
     * The `attachment` disposition: the body should be downloaded, optionally
     * as `filename`.
     */
    final case class Attachment(filename: Option[String]) extends ContentDisposition

    /** The `inline` disposition: the body should be shown in the page. */
    final case class Inline(filename: Option[String]) extends ContentDisposition

    /**
     * The `form-data` disposition of a multipart part: the form field `name`
     * and, for a file, its `filename`.
     */
    final case class FormField(name: String, filename: Option[String]) extends ContentDisposition

    private val AttachmentRegex         = """attachment; filename="(.*)"""".r
    private val InlineRegex             = """inline; filename="(.*)"""".r
    private val FormDataRegex           = """form-data; name="(.*)"; filename="(.*)"""".r
    private val FormDataNoFileNameRegex = """form-data; name="(.*)"""".r

    /**
     * Parses `attachment`, `inline` or `form-data` values, case-sensitively. A
     * filename or field name is read only when it is between double quotes;
     * otherwise an attachment or inline value gets no filename. Returns
     * `Left("Invalid form-data content disposition")` for a `form-data` value
     * without a quoted name, and `Left("Invalid content disposition")` for any
     * other type.
     */
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
          case FormDataRegex(name, filename) => Right(FormField(name, Some(filename)))
          case FormDataNoFileNameRegex(name) => Right(FormField(name, None))
          case _                             => Left("Invalid form-data content disposition")
        }
      } else {
        Left("Invalid content disposition")
      }
    }

    /**
     * Renders the type followed by `; `, then `name=` and the field name
     * followed by `; ` for a form field, and `filename=` and the filename when
     * set, without quotes, such as `form-data; name=f; filename=a.txt`. The
     * `parse` needs quotes, so it returns a `Left` for a rendered form field
     * and drops a rendered filename.
     */
    def render(contentDisposition: ContentDisposition): String = {
      contentDisposition match {
        case Attachment(filename)      => s"attachment; ${filename.map("filename=" + _).getOrElse("")}"
        case Inline(filename)          => s"inline; ${filename.map("filename=" + _).getOrElse("")}"
        case FormField(name, filename) => s"form-data; name=$name; ${filename.map("filename=" + _).getOrElse("")}"
      }
    }

    /** An `inline` disposition without a filename. */
    val inline: ContentDisposition = Inline(None)

    /** An `attachment` disposition without a filename. */
    val attachment: ContentDisposition = Attachment(None)

    /** An `inline` disposition with this filename. */
    def inline(filename: String): ContentDisposition = Inline(Some(filename))

    /** An `attachment` disposition with this filename. */
    def attachment(filename: String): ContentDisposition = Attachment(Some(filename))

    /**
     * A `form-data` disposition for the form field `name`, without a filename.
     */
    def formData(name: String): ContentDisposition = FormField(name, None)

    /**
     * A `form-data` disposition for the form field `name` that carries the file
     * `filename`.
     */
    def formData(name: String, filename: String): ContentDisposition = FormField(name, Some(filename))
  }

  /**
   * The `Content-Encoding` header: the codings, such as `gzip`, that were
   * applied to the body.
   */
  sealed trait ContentEncoding extends Header {
    override type Self = ContentEncoding
    override def self: Self                                    = this
    override def headerType: HeaderType.Typed[ContentEncoding] = ContentEncoding

    /**
     * The coding name, such as `gzip`. For `Multiple`, the names joined with
     * commas.
     */
    val encoding: String
  }

  /**
   * The header type of `Content-Encoding`, named `content-encoding`: parses and
   * renders `ContentEncoding` values.
   */
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
     * recognize x-gzip as an alias, for compatibility purposes. `parse` does
     * not accept `x-gzip`.
     */
    case object GZip extends ContentEncoding {
      override val encoding: String = "gzip"
    }

    /**
     * Several content codings, in the order they were applied, rendered
     * separated by `,`.
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
     * Parses a comma-separated list of `br`, `compress`, `deflate` and `gzip`
     * (case-sensitive). Entries with other names are dropped. One known entry
     * gives that value, several give `Multiple`. Returns
     * `Left("Empty ContentEncoding")` when no known entry is left.
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

    /**
     * Renders `encoding`: the coding name, or for `Multiple` the names joined
     * with `,` and no space.
     */
    def render(value: ContentEncoding): String = value.encoding

  }

  /**
   * The `Content-Language` header: the language of the content, one of a fixed
   * list of languages.
   */
  sealed trait ContentLanguage extends Header {
    override type Self = ContentLanguage
    override def self: Self                                    = this
    override def headerType: HeaderType.Typed[ContentLanguage] = ContentLanguage
  }

  /**
   * The header type of `Content-Language`, named `content-language`: parses and
   * renders `ContentLanguage` values.
   */
  object ContentLanguage extends HeaderType {
    override type HeaderValue = ContentLanguage

    override def name: String = "content-language"

    /** Arabic, `ar`. */
    case object Arabic extends ContentLanguage

    /** Bulgarian, `bg`. */
    case object Bulgarian extends ContentLanguage

    /** Catalan, `ca`. */
    case object Catalan extends ContentLanguage

    /** Chinese, `zh`. */
    case object Chinese extends ContentLanguage

    /** Croatian, `hr`. */
    case object Croatian extends ContentLanguage

    /** Czech, `cs`. */
    case object Czech extends ContentLanguage

    /** Danish, `da`. */
    case object Danish extends ContentLanguage

    /** Dutch, `nl`. */
    case object Dutch extends ContentLanguage

    /** English, `en`. */
    case object English extends ContentLanguage

    /** Estonian, `et`. */
    case object Estonian extends ContentLanguage

    /** Finnish, `fi`. */
    case object Finnish extends ContentLanguage

    /** French, `fr`. */
    case object French extends ContentLanguage

    /** German, `de`. */
    case object German extends ContentLanguage

    /** Greek, `el`. */
    case object Greek extends ContentLanguage

    /** Hebrew, `he`. */
    case object Hebrew extends ContentLanguage

    /** Hindi, `hi`. */
    case object Hindi extends ContentLanguage

    /** Hungarian, `hu`. */
    case object Hungarian extends ContentLanguage

    /** Icelandic, `is`. */
    case object Icelandic extends ContentLanguage

    /** Indonesian, `id`. */
    case object Indonesian extends ContentLanguage

    /** Italian, `it`. */
    case object Italian extends ContentLanguage

    /** Japanese, `ja`. */
    case object Japanese extends ContentLanguage

    /** Korean, `ko`. */
    case object Korean extends ContentLanguage

    /** Latvian, `lv`. */
    case object Latvian extends ContentLanguage

    /** Lithuanian, `lt`. */
    case object Lithuanian extends ContentLanguage

    /** Norwegian. `parse` reads it from `nb`, and `render` writes `no`. */
    case object Norwegian extends ContentLanguage

    /** Polish, `pl`. */
    case object Polish extends ContentLanguage

    /** Portuguese, `pt`. */
    case object Portuguese extends ContentLanguage

    /** Romanian, `ro`. */
    case object Romanian extends ContentLanguage

    /** Russian, `ru`. */
    case object Russian extends ContentLanguage

    /** Serbian, `sr`. */
    case object Serbian extends ContentLanguage

    /** Slovak, `sk`. */
    case object Slovak extends ContentLanguage

    /** Slovenian, `sl`. */
    case object Slovenian extends ContentLanguage

    /** Spanish, `es`. */
    case object Spanish extends ContentLanguage

    /** Swedish, `sv`. */
    case object Swedish extends ContentLanguage

    /** Thai, `th`. */
    case object Thai extends ContentLanguage

    /** Turkish, `tr`. */
    case object Turkish extends ContentLanguage

    /** Ukrainian, `uk`. */
    case object Ukrainian extends ContentLanguage

    /** Vietnamese, `vi`. */
    case object Vietnamese extends ContentLanguage

    /**
     * Reads the language from the first two letters of the value, without
     * regard to case, so `en-US` gives `English`. Returns `Left` with a message
     * that starts with `Invalid ContentLanguage: ` when they are not a
     * supported language code.
     */
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

    /** Renders the two-letter language code, such as `en`. */
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
   *
   * {{{
   * Header.ContentLength(1024L).renderedValue // 1024
   * Header.ContentLength.parse(" 42 ")         // Right(ContentLength(42))
   * }}}
   */
  final case class ContentLength(length: Long) extends Header {
    override type Self = ContentLength
    override def self: Self                                  = this
    override def headerType: HeaderType.Typed[ContentLength] = ContentLength
  }

  /**
   * The header type of `Content-Length`, named `content-length`: parses and
   * renders `ContentLength` values.
   */
  object ContentLength extends HeaderType {
    override type HeaderValue = ContentLength

    override def name: String = "content-length"

    /**
     * Parses a non-negative `Long`, with surrounding whitespace allowed.
     * Returns `Left("Invalid Content-Length header")` otherwise.
     */
    def parse(value: String): Either[String, ContentLength] =
      Try(value.trim.toLong) match {
        case Failure(_)     => Left("Invalid Content-Length header")
        case Success(value) => fromLong(value)
      }

    /** Renders the length in bytes. */
    def render(contentLength: ContentLength): String =
      contentLength.length.toString

    private def fromLong(value: Long): Either[String, ContentLength] =
      if (value >= 0)
        Right(ContentLength(value))
      else
        Left("Invalid Content-Length header")

  }

  /**
   * The `Content-Location` header: the URI of the resource that the body
   * represents.
   */
  final case class ContentLocation(value: URI) extends Header {
    override type Self = ContentLocation
    override def self: Self                                    = this
    override def headerType: HeaderType.Typed[ContentLocation] = ContentLocation
  }

  /**
   * The header type of `Content-Location`, named `content-location`: parses and
   * renders `ContentLocation` values.
   */
  object ContentLocation extends HeaderType {
    override type HeaderValue = ContentLocation

    override def name: String = "content-location"

    /**
     * Parses the value as a `java.net.URI`, absolute or relative. Returns
     * `Left("Invalid Content-Location header")` when it is not valid.
     */
    def parse(value: String): Either[String, ContentLocation] =
      Try(ContentLocation(new URI(value))).toEither.left.map(_ => "Invalid Content-Location header")

    /** Renders the URI with `toString`. */
    def render(contentLocation: ContentLocation): String =
      contentLocation.value.toString
  }

  /** The `Content-MD5` header: an MD5 digest of the body. */
  final case class ContentMd5(value: String) extends Header {
    override type Self = ContentMd5
    override def self: Self                               = this
    override def headerType: HeaderType.Typed[ContentMd5] = ContentMd5
  }

  /**
   * The header type of `Content-MD5`, named `content-md5`: parses and renders
   * `ContentMd5` values.
   */
  object ContentMd5 extends HeaderType {
    override type HeaderValue = ContentMd5

    override def name: String = "content-md5"

    private val MD5Regex = """[A-Fa-f0-9]{32}""".r

    /**
     * Keeps the value when it is exactly 32 hexadecimal digits. Returns
     * `Left("Invalid Content-MD5 header")` otherwise, including for the Base64
     * form.
     */
    def parse(value: String): Either[String, ContentMd5] =
      value match {
        case MD5Regex() => Right(ContentMd5(value))
        case _          => Left("Invalid Content-MD5 header")
      }

    /** Renders the digest unchanged. */
    def render(contentMd5: ContentMd5): String =
      contentMd5.value
  }

  /**
   * The `Content-Range` response header: which part of the full resource a
   * partial body holds, and the full length.
   */
  sealed trait ContentRange extends Header {
    override type Self = ContentRange
    override def self: Self                                 = this
    override def headerType: HeaderType.Typed[ContentRange] = ContentRange

    /** The first byte position of the range, when known. */
    def start: Option[Int]

    /** The last byte position of the range, inclusive, when known. */
    def end: Option[Int]

    /** The total length of the resource, when known. */
    def total: Option[Int]

    /** The range unit, such as `bytes`. */
    def unit: String
  }

  /**
   * The header type of `Content-Range`, named `content-range`: parses and
   * renders `ContentRange` values.
   */
  object ContentRange extends HeaderType {
    override type HeaderValue = ContentRange

    override def name: String = "content-range"

    /**
     * A range with its start, end and the total length, rendered as
     * `unit s-e/t`.
     */
    final case class EndTotal(unit: String, s: Int, e: Int, t: Int) extends ContentRange {

      /** Returns `Some(s)`. */
      def start: Option[Int] = Some(s)

      /** Returns `Some(e)`. */
      def end: Option[Int] = Some(e)

      /** Returns `Some(t)`. */
      def total: Option[Int] = Some(t)
    }

    /**
     * A range with its start and end, of an unknown total length. It renders as
     * `unit s-e/` followed by an asterisk in place of the total.
     */
    final case class StartEnd(unit: String, s: Int, e: Int) extends ContentRange {

      /** Returns `Some(s)`. */
      def start: Option[Int] = Some(s)

      /** Returns `Some(e)`. */
      def end: Option[Int] = Some(e)

      /** Returns `None`: the total length is unknown. */
      def total: Option[Int] = None
    }

    /**
     * Only the total length. It renders as the unit, a space, an asterisk in
     * place of the range, and `/t`. In HTTP, this form goes with a
     * `416 Range Not Satisfiable` response.
     */
    final case class RangeTotal(unit: String, t: Int) extends ContentRange {

      /** Returns `None`. */
      def start: Option[Int] = None

      /** Returns `None`. */
      def end: Option[Int] = None

      /** Returns `Some(t)`. */
      def total: Option[Int] = Some(t)
    }

    private val contentRangeStartEndTotalRegex = """(\w+) (\d+)-(\d+)/(\d+)""".r
    private val contentRangeStartEndRegex      = """(\w+) (\d+)-(\d+)/\*""".r
    private val contentRangeTotalRegex         = """(\w+) \*/(\d+)""".r

    /**
     * Parses `unit start-end/total`, the same with an asterisk in place of the
     * total, or the unit followed by an asterisk in place of `start-end`, with
     * exactly one space after the unit. Returns `Left("Invalid content range")`
     * for other shapes.
     */
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

    /**
     * Renders the range in the form that `parse` reads, such as
     * `bytes 0-499/1000`.
     */
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
  /**
   * The `Content-Security-Policy` response header: directives that tell the
   * browser which sources it may load content from.
   *
   * {{{
   * import zio.http.Header.ContentSecurityPolicy
   *
   * val csp = ContentSecurityPolicy.combined(
   *   ContentSecurityPolicy.defaultSrc(ContentSecurityPolicy.Source.Self),
   *   ContentSecurityPolicy.UpgradeInsecureRequests,
   * )
   * csp.renderedValue // default-src 'self'; upgrade-insecure-requests
   * }}}
   */
  sealed trait ContentSecurityPolicy extends Header {
    override type Self = ContentSecurityPolicy
    override def self: Self                                          = this
    override def headerType: HeaderType.Typed[ContentSecurityPolicy] = ContentSecurityPolicy
  }

  // TODO: Should we make deprecated types deprecated in code?
  /**
   * The header type of `Content-Security-Policy`, named
   * `content-security-policy`: parses and renders `ContentSecurityPolicy`
   * values, and builds source directives.
   */
  object ContentSecurityPolicy extends HeaderType {
    override type HeaderValue = ContentSecurityPolicy

    override def name: String = "content-security-policy"

    /**
     * A fetch directive, such as `script-src`, with its sources. Rendered as
     * the directive name, a space and the sources, such as `script-src 'self'`.
     */
    final case class SourcePolicy(srcType: SourcePolicyType, src: Source) extends ContentSecurityPolicy

    /** The `block-all-mixed-content` directive. */
    case object BlockAllMixedContent extends ContentSecurityPolicy

    // TODO: Deprecated and only Safari supports this and it is non-standard. Should we remove it?
    /** The `plugin-types` directive, with its value kept as text. */
    final case class PluginTypes(value: String) extends ContentSecurityPolicy

    // TODO: no modern browser supports this. Should we remove it?
    /** The `referrer` directive, with a referrer policy. */
    final case class Referrer(referrer: ReferrerPolicy) extends ContentSecurityPolicy

    /** The `report-to` directive, with the name of the reporting group. */
    final case class ReportTo(groupName: String) extends ContentSecurityPolicy

    /**
     * The `report-uri` directive, with the URI that violation reports are sent
     * to.
     */
    final case class ReportUri(uri: URI) extends ContentSecurityPolicy

    /**
     * The `require-sri-for` directive: the resource kinds that must use
     * Subresource Integrity.
     */
    final case class RequireSriFor(requirement: RequireSriForValue) extends ContentSecurityPolicy

    /** The `sandbox` directive, with its flags. */
    final case class Sandbox(value: SandboxValue) extends ContentSecurityPolicy

    /** The `trusted-types` directive, with its policy names and keywords. */
    final case class TrustedTypes(value: TrustedTypesValue) extends ContentSecurityPolicy

    /** The `upgrade-insecure-requests` directive. */
    case object UpgradeInsecureRequests extends ContentSecurityPolicy

    /** Several directives, rendered separated by `; `. */
    final case class Combined(directives: Chunk[ContentSecurityPolicy]) extends ContentSecurityPolicy

    /**
     * Combines directives into one policy. Returns `first` when `rest` is
     * empty, and a `Combined` of all of them otherwise.
     */
    def combined(first: ContentSecurityPolicy, rest: ContentSecurityPolicy*): ContentSecurityPolicy =
      if (rest.isEmpty) first
      else Combined(Chunk(first) ++ Chunk.fromIterable(rest))

    /**
     * The name of a fetch directive, such as `script-src`. Each case object is
     * named after the directive it renders.
     */
    sealed trait SourcePolicyType

    /** The fetch directive names, and their parser and renderer. */
    object SourcePolicyType {

      /** The `base-uri` directive. */
      case object `base-uri` extends SourcePolicyType

      /** The `child-src` directive. */
      case object `child-src` extends SourcePolicyType

      /** The `connect-src` directive. */
      case object `connect-src` extends SourcePolicyType

      /** The `default-src` directive. */
      case object `default-src` extends SourcePolicyType

      /** The `font-src` directive. */
      case object `font-src` extends SourcePolicyType

      /** The `form-action` directive. */
      case object `form-action` extends SourcePolicyType

      /** The `frame-ancestors` directive. */
      case object `frame-ancestors` extends SourcePolicyType

      /** The `frame-src` directive. */
      case object `frame-src` extends SourcePolicyType

      /** The `img-src` directive. */
      case object `img-src` extends SourcePolicyType

      /** The `manifest-src` directive. */
      case object `manifest-src` extends SourcePolicyType

      /** The `media-src` directive. */
      case object `media-src` extends SourcePolicyType

      /** The `object-src` directive. */
      case object `object-src` extends SourcePolicyType

      /** The `prefetch-src` directive. */
      case object `prefetch-src` extends SourcePolicyType

      /** The `script-src` directive. */
      case object `script-src` extends SourcePolicyType

      /** The `script-src-attr` directive. */
      case object `script-src-attr` extends SourcePolicyType

      /** The `script-src-elem` directive. */
      case object `script-src-elem` extends SourcePolicyType

      /** The `style-src` directive. */
      case object `style-src` extends SourcePolicyType

      /** The `style-src-attr` directive. */
      case object `style-src-attr` extends SourcePolicyType

      /** The `style-src-elem` directive. */
      case object `style-src-elem` extends SourcePolicyType

      /** The `upgrade-insecure-requests` name used as a source directive. */
      case object `upgrade-insecure-requests` extends SourcePolicyType

      /** The `worker-src` directive. */
      case object `worker-src` extends SourcePolicyType

      /**
       * Returns the directive with this exact (case-sensitive) name, or `None`.
       */
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

      /** Returns the directive name, such as `script-src`. */
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

    /**
     * A source in a fetch directive, such as `'self'`, a host or a nonce.
     * Combine sources with `&&`.
     */
    sealed trait Source {
      self =>

      /**
       * Combines this source with `other` into a `Sequence`, rendered separated
       * by a space. Returns this source unchanged when `other` is
       * `Source.none`.
       */
      def &&(other: Source): Source =
        if (other == Source.none) self else Source.Sequence(self, other)
    }

    /** The kinds of `Source`, their parser and renderer, and constructors. */
    object Source {

      /**
       * The `'none'` source. `none && other` returns `other`, so it acts as the
       * empty start of a combination.
       */
      case object none extends Source {
        override def &&(other: Source): Source = other
      }

      /**
       * A host or URL source, such as `https://example.com`, rendered with
       * `URI.toString`.
       */
      final case class Host(uri: URI) extends Source

      /**
       * A scheme source, rendered as `scheme:`, such as `https:`. `parse` does
       * not produce it.
       */
      final case class Scheme(scheme: String) extends Source

      /** The `'self'` source. */
      case object Self extends Source

      /** The `'unsafe-eval'` source. */
      case object UnsafeEval extends Source

      /** The source that is parsed from and rendered as `'wasm-eval'`. */
      case object WasmUnsafeEval extends Source

      /** The `'unsafe-hashes'` source. */
      case object UnsafeHashes extends Source

      /** The `'unsafe-inline'` source. */
      case object UnsafeInline extends Source

      /** A nonce source; `Nonce("abc")` renders as `'nonce-abc'`. */
      final case class Nonce(value: String) extends Source

      /**
       * A hash source. `parse` reads values such as `'sha256-abc'` (also with
       * `sha384` and `sha512`), and `render` writes the algorithm with its
       * case-object name, such as `'Sha256-abc'`.
       */
      final case class Hash(algorithm: HashAlgorithm, value: String) extends Source

      /** The `'strict-dynamic'` source. */
      case object StrictDynamic extends Source

      /** The `'report-sample'` source. */
      case object ReportSample extends Source

      /** Two sources, rendered separated by a space. `&&` builds it. */
      final case class Sequence(left: Source, right: Source) extends Source

      /** The hash algorithm of a `Hash` source. */
      sealed trait HashAlgorithm

      /** The supported hash algorithms. */
      object HashAlgorithm {

        /** SHA-256. */
        case object Sha256 extends HashAlgorithm

        /** SHA-384. */
        case object Sha384 extends HashAlgorithm

        /** SHA-512. */
        case object Sha512 extends HashAlgorithm

        /**
         * Parses `sha256`, `sha384` or `sha512` (lower case). Returns `None`
         * otherwise.
         */
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

      /**
       * Parses one source: a quoted keyword such as `'self'`, a nonce such as
       * `'nonce-abc'`, a hash such as `'sha256-abc'`, or else any text that
       * `java.net.URI.create` accepts, as a `Host`. Returns `None` otherwise,
       * for example for several sources separated by spaces.
       */
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

      /**
       * Renders the source as it appears in a directive; a `Sequence` renders
       * its sources separated by spaces.
       */
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

      /** Creates a `Host` source. */
      def host(uri: URI): Source = Host(uri)

      /** Creates a `Scheme` source, rendered as `scheme:`. */
      def scheme(scheme: String): Source = Scheme(scheme)

      /** Creates a `Nonce` source; `nonce("abc")` renders as `'nonce-abc'`. */
      def nonce(value: String): Source = Nonce(value)

      /** Creates a `Hash` source. */
      def hash(algorithm: HashAlgorithm, value: String): Source = Hash(algorithm, value)
    }

    /**
     * The flags of a `sandbox` directive, such as `allow-scripts`. Combine
     * flags with `&&`.
     */
    sealed trait SandboxValue {
      self =>

      /**
       * Combines this flag with `other` into a `Sequence`, rendered separated
       * by a space. Returns this value unchanged when `other` is `Empty`.
       */
      def &&(other: SandboxValue): SandboxValue =
        if (other == SandboxValue.Empty) self else SandboxValue.Sequence(self, other)
    }

    /** The sandbox flags, and their parser and renderer. */
    object SandboxValue {

      /**
       * No flag: the most restrictive sandbox. Rendered as an empty string;
       * `Empty && other` returns `other`.
       */
      case object Empty extends SandboxValue {
        override def &&(other: SandboxValue): SandboxValue = other
      }

      /** The `allow-forms` flag. */
      case object AllowForms extends SandboxValue

      /** The `allow-same-origin` flag. */
      case object AllowSameOrigin extends SandboxValue

      /** The `allow-scripts` flag. */
      case object AllowScripts extends SandboxValue

      /** The `allow-popups` flag. */
      case object AllowPopups extends SandboxValue

      /** The `allow-modals` flag. */
      case object AllowModals extends SandboxValue

      /** The `allow-orientation-lock` flag. */
      case object AllowOrientationLock extends SandboxValue

      /** The `allow-pointer-lock` flag. */
      case object AllowPointerLock extends SandboxValue

      /** The `allow-presentation` flag. */
      case object AllowPresentation extends SandboxValue

      /** The `allow-popups-to-escape-sandbox` flag. */
      case object AllowPopupsToEscapeSandbox extends SandboxValue

      /** The `allow-top-navigation` flag. */
      case object AllowTopNavigation extends SandboxValue

      /** Two flags, rendered separated by a space. `&&` builds it. */
      final case class Sequence(left: SandboxValue, right: SandboxValue) extends SandboxValue

      /**
       * Parses flags separated by single spaces, such as
       * `allow-forms allow-scripts`; an empty string gives `Empty`. Returns
       * `None` when a flag is unknown.
       */
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

      /** Renders the flags separated by spaces. */
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

    /**
     * The value of a `trusted-types` directive: policy names and keywords.
     * Combine them with `&&`.
     */
    sealed trait TrustedTypesValue extends scala.Product with Serializable {
      self =>

      /**
       * Combines this value with `other` into a `Sequence`, rendered separated
       * by a space. Returns this value unchanged when `other` is
       * `TrustedTypesValue.none`.
       */
      def &&(other: TrustedTypesValue): TrustedTypesValue =
        if (other == TrustedTypesValue.none) self else TrustedTypesValue.Sequence(self, other)
    }

    /** The kinds of `TrustedTypesValue`, and their parser and renderer. */
    object TrustedTypesValue {

      /** The `'none'` keyword. `none && other` returns `other`. */
      case object none extends TrustedTypesValue {
        override def &&(other: TrustedTypesValue): TrustedTypesValue = other
      }

      /** A policy name, rendered as it is. */
      final case class PolicyName(value: String) extends TrustedTypesValue

      /** The `'allow-duplicates'` keyword. */
      case object `allow-duplicates` extends TrustedTypesValue

      /** The `*` wildcard. */
      case object Wildcard extends TrustedTypesValue

      /** Two values, rendered separated by a space. `&&` builds it. */
      final case class Sequence(left: TrustedTypesValue, right: TrustedTypesValue) extends TrustedTypesValue

      private val PolicyNameRegex = """\*|[a-zA-Z0-9-#=_/@.%]+|'allow-duplicates'|'none'""".r

      /**
       * Finds the policy names, `*`, `'none'` and `'allow-duplicates'` in the
       * value and combines them with `&&`. Returns `None` when none is found.
       */
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

      /**
       * Renders the value: `'none'`, `*`, `'allow-duplicates'` or the policy
       * name, with the parts of a `Sequence` separated by spaces.
       */
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

    /**
     * The policy of a `referrer` directive. Each case object is named after the
     * value it renders.
     */
    sealed trait ReferrerPolicy extends scala.Product with Serializable

    /** The referrer policies, and their parser and renderer. */
    object ReferrerPolicy {

      /** The `no-referrer` policy. */
      case object `no-referrer` extends ReferrerPolicy

      /** The `none-when-downgrade` policy. */
      case object `none-when-downgrade` extends ReferrerPolicy

      /** The `origin` policy. */
      case object `origin` extends ReferrerPolicy

      /** The `origin-when-cross-origin` policy. */
      case object `origin-when-cross-origin` extends ReferrerPolicy

      /** The `unsafe-url` policy. */
      case object `unsafe-url` extends ReferrerPolicy

      /** Returns the policy with this exact name, or `None`. */
      def parse(referrer: String): Option[ReferrerPolicy] =
        referrer match {
          case "no-referrer"              => Some(`no-referrer`)
          case "none-when-downgrade"      => Some(`none-when-downgrade`)
          case "origin"                   => Some(`origin`)
          case "origin-when-cross-origin" => Some(`origin-when-cross-origin`)
          case "unsafe-url"               => Some(`unsafe-url`)
          case _                          => None
        }

      /** Returns the policy name, such as `no-referrer`. */
      def render(referrer: ReferrerPolicy): String = referrer.productPrefix
    }

    /**
     * The value of a `require-sri-for` directive: `script`, `style`, or both.
     */
    sealed trait RequireSriForValue extends scala.Product with Serializable

    /** The `require-sri-for` values, and their parser and renderer. */
    object RequireSriForValue {

      /** Scripts must use Subresource Integrity. Rendered as `script`. */
      case object Script extends RequireSriForValue

      /** Style sheets must use Subresource Integrity. Rendered as `style`. */
      case object Style extends RequireSriForValue

      /**
       * Scripts and style sheets must use Subresource Integrity. Rendered as
       * `script style`.
       */
      case object ScriptStyle extends RequireSriForValue

      /**
       * Parses `script`, `style` or `script style`. Returns `None` otherwise.
       */
      def parse(value: String): Option[RequireSriForValue] =
        value match {
          case "script"       => Some(Script)
          case "style"        => Some(Style)
          case "script style" => Some(ScriptStyle)
          case _              => None
        }

      /** Renders `script`, `style` or `script style`. */
      def fromRequireSriForValue(value: RequireSriForValue): String =
        value match {
          case Script      => "script"
          case Style       => "style"
          case ScriptStyle => "script style"
        }
    }

    /**
     * Creates a `default-src` directive with these sources. Without a source,
     * it renders as `default-src 'none'`.
     */
    def defaultSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`default-src`, src.foldLeft[Source](Source.none)(_ && _))

    /**
     * Creates a `script-src` directive with these sources. Without a source, it
     * renders as `script-src 'none'`.
     */
    def scriptSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`script-src`, src.foldLeft[Source](Source.none)(_ && _))

    /**
     * Creates a `style-src` directive with these sources. Without a source, it
     * renders as `style-src 'none'`.
     */
    def styleSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`style-src`, src.foldLeft[Source](Source.none)(_ && _))

    /**
     * Creates an `img-src` directive with these sources. Without a source, it
     * renders as `img-src 'none'`.
     */
    def imgSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`img-src`, src.foldLeft[Source](Source.none)(_ && _))

    /**
     * Creates a `media-src` directive with these sources. Without a source, it
     * renders as `media-src 'none'`.
     */
    def mediaSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`media-src`, src.foldLeft[Source](Source.none)(_ && _))

    /**
     * Creates a `frame-src` directive with these sources. Without a source, it
     * renders as `frame-src 'none'`.
     */
    def frameSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`frame-src`, src.foldLeft[Source](Source.none)(_ && _))

    /**
     * Creates a `font-src` directive with these sources. Without a source, it
     * renders as `font-src 'none'`.
     */
    def fontSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`font-src`, src.foldLeft[Source](Source.none)(_ && _))

    /**
     * Creates a `connect-src` directive with these sources. Without a source,
     * it renders as `connect-src 'none'`.
     */
    def connectSrc(src: Source*): SourcePolicy =
      SourcePolicy(SourcePolicyType.`connect-src`, src.foldLeft[Source](Source.none)(_ && _))

    /**
     * Creates an `object-src` directive with these sources. Without a source,
     * it renders as `object-src 'none'`.
     */
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

    /**
     * Parses one directive, or several separated by `;` into a `Combined`.
     * Directive names are case-sensitive, and a source directive such as
     * `default-src 'self'` is read with one source. Returns
     * `Left("Invalid Content-Security-Policy")` for an unknown directive or
     * source, a more specific message such as `Invalid sandbox value` for a bad
     * value, and, when several directives fail, their messages joined with
     * `; `.
     */
    def parse(value: String): Either[String, ContentSecurityPolicy] = {
      val parts = value.split(';').map(_.trim).filter(_.nonEmpty)
      if (parts.length > 1) {
        val results = parts.map(parseSingle)
        val errors  = results.collect { case Left(e) => e }
        if (errors.nonEmpty) Left(errors.mkString("; "))
        else Right(Combined(Chunk.fromArray(results.collect { case Right(v) => v })))
      } else parseSingle(value)
    }

    private def parseSingle(value: String): Either[String, ContentSecurityPolicy] =
      value.trim match {
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

    /**
     * Renders each directive as its name followed by its value, and the
     * directives of a `Combined` separated by `; `.
     */
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
        case ContentSecurityPolicy.Combined(directives)    => directives.map(render).mkString("; ")
      }

    /**
     * Creates a `SourcePolicy` from a directive name, such as `script-src`, and
     * the text of one source. Returns `Left("Invalid Content-Security-Policy")`
     * when either does not parse.
     */
    def fromTypeAndPolicy(policyType: String, policy: String): Either[String, ContentSecurityPolicy] =
      SourcePolicyType
        .parse(policyType)
        .flatMap(policyType => Source.parse(policy).map(SourcePolicy(policyType, _)))
        .toRight("Invalid Content-Security-Policy")

  }

  /**
   * The `Content-Security-Policy-Report-Only` response header: a policy that
   * the browser reports violations of without enforcing it.
   */
  final case class ContentSecurityPolicyReportOnly(policy: ContentSecurityPolicy) extends Header {
    override type Self = ContentSecurityPolicyReportOnly
    override def self: Self                                                    = this
    override def headerType: HeaderType.Typed[ContentSecurityPolicyReportOnly] = ContentSecurityPolicyReportOnly
  }

  /**
   * The header type of `Content-Security-Policy-Report-Only`, named
   * `content-security-policy-report-only`: parses and renders
   * `ContentSecurityPolicyReportOnly` values.
   */
  object ContentSecurityPolicyReportOnly extends HeaderType {
    override type HeaderValue = ContentSecurityPolicyReportOnly

    override def name: String = "content-security-policy-report-only"

    /** Parses the value as `ContentSecurityPolicy.parse` does. */
    def parse(value: String): Either[String, ContentSecurityPolicyReportOnly] =
      ContentSecurityPolicy.parse(value).map(ContentSecurityPolicyReportOnly(_))

    /** Renders the policy as `ContentSecurityPolicy.render` does. */
    def render(cspro: ContentSecurityPolicyReportOnly): String =
      ContentSecurityPolicy.render(cspro.policy)
  }

  /**
   * The `Content-Transfer-Encoding` header of MIME parts: how the body is
   * encoded, such as `base64`.
   */
  sealed trait ContentTransferEncoding extends Header {
    override type Self = ContentTransferEncoding
    override def self: Self                                            = this
    override def headerType: HeaderType.Typed[ContentTransferEncoding] = ContentTransferEncoding
  }

  /**
   * The header type of `Content-Transfer-Encoding`, named
   * `content-transfer-encoding`: parses and renders `ContentTransferEncoding`
   * values.
   */
  object ContentTransferEncoding extends HeaderType {
    override type HeaderValue = ContentTransferEncoding

    override def name: String = "content-transfer-encoding"

    /** The `7bit` encoding. */
    case object SevenBit extends ContentTransferEncoding

    /** The `8bit` encoding. */
    case object EightBit extends ContentTransferEncoding

    /** The `binary` encoding. */
    case object Binary extends ContentTransferEncoding

    /** The `quoted-printable` encoding. */
    case object QuotedPrintable extends ContentTransferEncoding

    /** The `base64` encoding. */
    case object Base64 extends ContentTransferEncoding

    /** A custom `x-` encoding. `token` is the part after `x-`. */
    final case class XToken(token: String) extends ContentTransferEncoding

    private val XRegex = "x-(.*)".r

    /**
     * Lower-cases the value and parses `7bit`, `8bit`, `binary`,
     * `quoted-printable`, `base64`, or `x-` followed by a token. Returns
     * `Left("Invalid Content-Transfer-Encoding header")` otherwise.
     */
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

    /**
     * Renders the encoding name, or `x-` followed by the token for an `XToken`.
     */
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

  /**
   * The `Content-Type` header: the media type of the body, with an optional
   * `boundary` for multipart bodies and an optional `charset`.
   *
   * {{{
   * import java.nio.charset.StandardCharsets
   *
   * Header.ContentType(MediaType.application.json).renderedValue
   * // application/json
   * Header.ContentType(MediaType.text.plain, charset = Some(StandardCharsets.UTF_8)).renderedValue
   * // text/plain; charset=utf-8
   * Header.ContentType.parse("text/html; charset=UTF-8").map(_.charset)
   * // Right(Some(UTF-8))
   * }}}
   */
  final case class ContentType(mediaType: MediaType, boundary: Option[Boundary] = None, charset: Option[Charset] = None) extends Header {
    override type Self = ContentType

    override def self: Self = this

    override def headerType: HeaderType.Typed[ContentType] = ContentType

    override lazy val renderedValue: String = ContentType.render(this)
  }

  /**
   * The header type of `Content-Type`, named `content-type`: parses and renders
   * `ContentType` values.
   */
  object ContentType extends HeaderType {
    private final val CacheInitialSize = 64
    private final val CacheMaxSize     = 8192

    private val cache: ConcurrentHashMap[String, Either[String, ContentType]] =
      new ConcurrentHashMap[String, Either[String, ContentType]](CacheInitialSize)

    override type HeaderValue = ContentType

    override def name: String = "content-type"

    /**
     * Parses a media type with optional parameters, such as
     * `multipart/form-data; boundary=abc`. It keeps the `charset` and
     * `boundary` parameters and drops the others. The main type is
     * case-sensitive and must be a known one or start with `x-` (in any case).
     * A `charset` that names an unsupported charset, such as `charset=foo`, is
     * dropped. Returns a `Left` with the decoding error otherwise.
     */
    def parse(s: String): Either[String, ContentType] = {
      // Guard against malicious registering of invalid content types
      if (cache.size >= CacheMaxSize) cache.clear()
      cache.computeIfAbsent(s, parseFn)
    }

    /**
     * Renders the media type, followed by `; charset=` and the charset name in
     * lower case, and `; boundary=` and the boundary, when they are set, such
     * as `text/plain; charset=utf-8`.
     */
    def render(contentType: ContentType): String = codec.encode(contentType).toOption.get

    private val codec: RichTextCodec[ContentType] = {

      // char `.` according to BNF not allowed as `token`, but here tolerated
      val token       = RichTextCodec.charsNot(' ', '(', ')', '<', '>', '@', ',', ';', ':', '\\', '"', '/', '[', ']', '?', '=')
      val tokenString = token.repeat.string

      val tokenStringQuoted = RichTextCodec.charsNot(' ', '"').repeat.string

      val type1        = RichTextCodec.string.collectOrFail("unsupported main type") {
        case value if MediaType.mainTypeMap.contains(value) => value
      }
      val type1x       = (RichTextCodec.literalCI("x-") ~ tokenString).transform[String](in => s"${in._1}${in._2}")(in => ("x-", s"${in.substring(2)}"))
      val codecType1   = type1 | type1x
      val forwardSlash = RichTextCodec.char('/').const('/')
      val codecType    = (codecType1 <~ forwardSlash) ~ tokenString
      val quote        = RichTextCodec.char('"')
      val valueQuoted  = quote ~ tokenStringQuoted ~ quote
      val value        = valueQuoted | tokenString

      val unitChunk   = Chunk.single(())
      val whitespaces = RichTextCodec.whitespaceChar.repeat.transform[Char](_ => ' ')(_ => unitChunk).const(' ')
      val param       = ((
        RichTextCodec.char(';').const(';') ~>
          whitespaces ~>
          tokenString <~
          RichTextCodec.char('=').const('=')
      ) ~ value)
        .transformOrFailLeft[ContentType.Parameter](in => ContentType.Parameter.fromCodec(in))(in => in.toCodec)
      val params      = param.repeat

      (codecType ~ params).transform[ContentType] { case (mainType, subType, params) =>
        ContentType(
          MediaType.forContentType(s"$mainType/$subType").get,
          params.collectFirst { case p if p.key == ContentType.Parameter.Boundary.name => zio.http.Boundary(p.value) },
          params.collectFirst { case p if p.key == ContentType.Parameter.Charset.name => java.nio.charset.Charset.forName(p.value) },
        )
      }(in =>
        (
          in.mediaType.mainType,
          in.mediaType.subType, {
            val charset  = in.charset.map(in => Parameter.Charset(Parameter.Payload(Parameter.Charset.name, in, false)))
            val boundary = in.boundary.map(in => Parameter.Boundary(Parameter.Payload(Parameter.Boundary.name, in, false)))
            (charset, boundary) match {
              case (Some(c), Some(b)) => Chunk(c, b)
              case (Some(c), _)       => Chunk.single(c)
              case (_, Some(b))       => Chunk.single(b)
              case _                  => Chunk.empty
            }
          },
        ),
      )
    }

    private val parseFn: java.util.function.Function[String, Either[String, ContentType]] =
      codec.decode(_)

    private[Header] sealed trait Parameter {
      self =>

      /** Whether the value was between double quotes. */
      def isQuoted: Boolean = self match {
        case Parameter.Boundary(payload) => payload.isQuoted
        case Parameter.Charset(payload)  => payload.isQuoted
        case Parameter.Value(payload)    => payload.isQuoted
      }

      /** The parameter name. */
      def key: String = self match {
        case Parameter.Value(payload)    => payload.key
        case Parameter.Charset(payload)  => payload.key
        case Parameter.Boundary(payload) => payload.key
      }

      /** The parameter value; a charset name is given in lower case. */
      def value: String = self match {
        case Parameter.Value(payload)    => payload.value
        case Parameter.Boundary(payload) => payload.value.id
        case Parameter.Charset(payload)  => payload.value.toString.toLowerCase
      }

      /**
       * The name and the value, quoted (`Left`) or not (`Right`), in the form
       * the codec writes.
       */
      def toCodec: (String, Either[(Char, String, Char), String]) =
        (self.key, if (self.isQuoted) Left(('"', self.value, '"')) else Right(self.value))
    }

    private[Header] object Parameter {
      case class Payload[A](key: String, value: A, isQuoted: Boolean)

      case class Boundary(payload: Payload[zio.http.Boundary]) extends Parameter

      object Boundary {
        val name = "boundary"
      }

      case class Charset(payload: Payload[java.nio.charset.Charset]) extends Parameter

      object Charset {
        val name = "charset"
      }

      case class Value(payload: Payload[String]) extends Parameter

      def make(key: String, value: String, isQuoted: Boolean): Either[String, Parameter] = {
        if (key == Parameter.Charset.name) {
          try Right(Parameter.Charset(Payload(key, java.nio.charset.Charset.forName(value), isQuoted)))
          catch {
            case _: UnsupportedCharsetException =>
              Left(s"Invalid charset in Content-Type header: $value")
          }
        } else if (key == Parameter.Boundary.name) Right(Parameter.Boundary(Payload(key, zio.http.Boundary(value), isQuoted)))
        else Right(Parameter.Value(Payload(key, value, isQuoted)))
      }

      def fromCodec(parse: (String, Either[(Char, String, Char), String])): Either[String, Parameter] =
        Parameter.make(parse._1, parse._2.fold(a => a._2, identity), parse._2.isLeft)
    }
  }

  /** The `Date` header: the date and time at which the message was created. */
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

    /**
     * Parses an RFC 1123 date in GMT, such as `Wed, 21 Oct 2015 07:28:00 GMT`.
     * Returns `Left("Invalid Date header")` for other formats.
     */
    def parse(value: String): Either[String, Date] =
      DateEncoding.decodeDate(value).toRight("Invalid Date header").map(Date(_))

    /** Renders the date in RFC 1123 format, converted to GMT. */
    def render(date: Date): String =
      DateEncoding.encodeDate(date.value)
  }

  /**
   * The `DNT` (Do Not Track) request header: whether the user allows tracking.
   */
  sealed trait DNT extends Header {
    override type Self = DNT
    override def self: Self                        = this
    override def headerType: HeaderType.Typed[DNT] = DNT
  }

  /** The header type of `DNT`, named `dnt`: parses and renders `DNT` values. */
  object DNT extends HeaderType {
    override type HeaderValue = DNT

    override def name: String = "dnt"

    /** The user allows tracking. Rendered as `0`. */
    case object TrackingAllowed extends DNT

    /** The user does not allow tracking. Rendered as `1`. */
    case object TrackingNotAllowed extends DNT

    /** The user has not stated a preference. Rendered as `null`. */
    case object NotSpecified extends DNT

    /**
     * Parses `1`, `0` or `null`. Returns `Left("Invalid DNT header")` for any
     * other value.
     */
    def parse(value: String): Either[String, DNT] = {
      value match {
        case "null" => Right(NotSpecified)
        case "1"    => Right(TrackingNotAllowed)
        case "0"    => Right(TrackingAllowed)
        case _      => Left("Invalid DNT header")
      }
    }

    /** Renders `0`, `1` or `null`. */
    def render(dnt: DNT): String =
      dnt match {
        case NotSpecified       => "null"
        case TrackingAllowed    => "0"
        case TrackingNotAllowed => "1"
      }
  }

  /**
   * The `ETag` response header: an entity tag that identifies a version of a
   * resource, either strong or weak.
   *
   * {{{
   * Header.ETag.parse("W/\"v42\"")    // Right(Weak(v42))
   * Header.ETag.Strong("v42").renderedValue // "v42"
   * }}}
   */
  sealed trait ETag extends Header {
    override type Self = ETag
    override def self: Self                         = this
    override def headerType: HeaderType.Typed[ETag] = ETag
  }

  /**
   * The header type of `ETag`, named `etag`: parses and renders `ETag` values.
   */
  object ETag extends HeaderType {
    override type HeaderValue = ETag

    override def name: String = "etag"

    /**
     * A strong entity tag. `validator` is stored without quotes and rendered
     * between double quotes.
     */
    final case class Strong(validator: String) extends ETag

    /**
     * A weak entity tag. `validator` is stored without quotes and rendered as
     * `W/"validator"`.
     */
    final case class Weak(validator: String) extends ETag

    /**
     * Parses `"validator"` as `Strong` and `W/"validator"` or `w/"validator"`
     * as `Weak`, removing the quotes. Returns `Left("Invalid ETag header")` for
     * an unquoted value.
     */
    def parse(value: String): Either[String, ETag] = {
      value match {
        case str if str.startsWith("w/\"") && str.endsWith("\"") => Right(Weak(str.drop(3).dropRight(1)))
        case str if str.startsWith("W/\"") && str.endsWith("\"") => Right(Weak(str.drop(3).dropRight(1)))
        case str if str.startsWith("\"") && str.endsWith("\"")   => Right(Strong(str.drop(1).dropRight(1)))
        case _                                                   => Left("Invalid ETag header")
      }
    }

    /** Renders `"validator"` for `Strong` and `W/"validator"` for `Weak`. */
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

    /** The rendered value, such as `100-continue`. */
    val value: String
  }

  /**
   * The header type of `Expect`, named `expect`: parses and renders `Expect`
   * values.
   */
  object Expect extends HeaderType {
    override type HeaderValue = Expect

    override def name: String = "expect"

    /**
     * The `100-continue` expectation: the client waits for a `100 Continue`
     * response before it sends the body.
     */
    case object `100-continue` extends Expect {

      /** The rendered value: `100-continue`. */
      val value = "100-continue"
    }

    /**
     * Parses `100-continue`, matched case-sensitively. Returns
     * `Left("Invalid Expect header")` otherwise.
     */
    def parse(value: String): Either[String, Expect] =
      value match {
        case `100-continue`.value => Right(`100-continue`)
        case _                    => Left("Invalid Expect header")
      }

    /** Renders `value`. */
    def render(expect: Expect): String =
      expect.value
  }

  /**
   * The `Expires` response header: the date and time after which the response
   * is stale.
   */
  final case class Expires(value: ZonedDateTime) extends Header {
    override type Self = Expires
    override def self: Self                            = this
    override def headerType: HeaderType.Typed[Expires] = Expires
  }

  /**
   * The header type of `Expires`, named `expires`. The value is an HTTP date,
   * such as `Expires: Wed, 21 Oct 2015 07:28:00 GMT`.
   *
   * HTTP treats an invalid date, such as `0`, as a date in the past, which
   * means that the response is already stale. `parse` returns a `Left` for such
   * values.
   */
  object Expires extends HeaderType {
    override type HeaderValue = Expires

    override def name: String = "expires"

    /**
     * Parses an RFC 1123 date in GMT, such as `Wed, 21 Oct 2015 07:28:00 GMT`.
     * Returns `Left("Invalid Expires header")` for other values, including `0`.
     */
    def parse(date: String): Either[String, Expires] =
      DateEncoding.decodeDate(date).toRight("Invalid Expires header").map(Expires(_))

    /** Renders the date in RFC 1123 format, converted to GMT. */
    def render(expires: Expires): String =
      DateEncoding.encodeDate(expires.value)
  }

  /**
   * The `Forwarded` request header: information that proxies add about the
   * client and the proxies, from the `by`, `for`, `host` and `proto`
   * directives.
   */
  final case class Forwarded(by: Option[String] = None, forValues: List[String] = Nil, host: Option[String] = None, proto: Option[String] = None) extends Header {
    override type Self = Forwarded
    override def self: Self                              = this
    override def headerType: HeaderType.Typed[Forwarded] = Forwarded
  }

  /**
   * The header type of `Forwarded`, named `forwarded`: parses and renders
   * `Forwarded` values.
   */
  object Forwarded extends HeaderType {
    override type HeaderValue = Forwarded

    override def name: String = "forwarded"

    /**
     * Reads the `by=`, `for=`, `host=` and `proto=` directives from a
     * `;`-separated value, such as `for=192.0.2.60;proto=http;by=203.0.113.43`.
     * A `for` directive can list several comma-separated `for=` values. A
     * directive is found only when its part starts with the name, so
     * `for=a; proto=http` gives no `proto`. It returns `Right` for any input;
     * missing directives are left empty.
     */
    def parse(forwarded: String): Either[String, Forwarded] = {
      val parts    = forwarded.split(";")
      val by       = parts.collectFirst { case s if s.startsWith("by=") => s.drop(3) }.map(_.trim)
      val forValue = parts.collectFirst { case s if s.startsWith("for=") => s.split(',') }.map(_.map(_.trim.drop(4).trim).toList).getOrElse(Nil)
      val host     = parts.collectFirst { case s if s.startsWith("host=") => s.drop(5) }.map(_.trim)
      val proto    = parts.collectFirst { case s if s.startsWith("proto=") => s.drop(6) }.map(_.trim)
      Right(Forwarded(by, forValue, host, proto))
    }

    /**
     * Renders each set directive followed by `;`, in the order `by`, `for`,
     * `host`, `proto`, with the `for` values as `for=a,for=b;`.
     */
    def render(forwarded: Forwarded): String = {
      def formatDirective(directiveName: String, directiveValue: Option[String]) =
        directiveValue.map(v => s"${directiveName}=${v};").getOrElse("")

      val forValues = if (forwarded.forValues.nonEmpty) forwarded.forValues.mkString("for=", ",for=", ";") else ""

      s"${formatDirective("by", forwarded.by)}${forValues}${formatDirective("host", forwarded.host)}${formatDirective("proto", forwarded.proto)}"
    }
  }

  /** From header value. */
  final case class From(email: String) extends Header {
    override type Self = From
    override def self: Self                         = this
    override def headerType: HeaderType.Typed[From] = From
  }

  /**
   * The header type of `From`, named `from`: parses and renders `From` values.
   */
  object From extends HeaderType {
    override type HeaderValue = From

    override def name: String = "from"

    // Regex that does veery loose validation of email.
    private val emailRegex = "([^ ]+@[^ ]+[.][^ ]+)".r

    /**
     * Keeps the value when it looks like an email address: text without spaces
     * that contains `@` and, after it, a `.`. Returns
     * `Left("Invalid From header")` otherwise.
     */
    def parse(fromHeader: String): Either[String, From] =
      fromHeader match {
        case emailRegex(_) => Right(From(fromHeader))
        case _             => Left("Invalid From header")
      }

    /** Renders the email address. */
    def render(from: From): String =
      from.email
  }

  /**
   * The `Host` request header: the host name and optional port of the server
   * the request is sent to.
   *
   * {{{
   * Header.Host("example.com", 8080).renderedValue // example.com:8080
   * Header.Host.parse("example.com")              // Right(Host(example.com,None))
   * }}}
   */
  final case class Host(hostAddress: String, port: Option[Int] = None) extends Header {
    override type Self = Host
    override def self: Self                         = this
    override def headerType: HeaderType.Typed[Host] = Host
  }

  /**
   * The header type of `Host`, named `host`: parses and renders `Host` values.
   */
  object Host extends HeaderType {
    override type HeaderValue = Host

    override def name: String = "host"

    /** Creates a `Host` with this host name and port. */
    def apply(hostAddress: String, port: Int): Host = Host(hostAddress, Some(port))

    /**
     * Parses `host` or `host:port`. Returns `Left("Invalid Host header")` for
     * an empty value, a port that is not a number, or a value with more than
     * one `:`, such as an IPv6 address.
     */
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

    /** Renders `host`, or `host:port` when a port is set. */
    def render(host: Host): String =
      host match {
        case Host(address, None)       => address
        case Host(address, Some(port)) => s"$address:$port"
      }
  }

  /**
   * The `If-Match` request header: the request applies only when the resource
   * matches one of the entity tags, or any version for `*`.
   */
  sealed trait IfMatch extends Header {
    override type Self = IfMatch
    override def self: Self                            = this
    override def headerType: HeaderType.Typed[IfMatch] = IfMatch
  }

  /**
   * The header type of `If-Match`, named `if-match`: parses and renders
   * `IfMatch` values.
   */
  object IfMatch extends HeaderType {
    override type HeaderValue = IfMatch

    override def name: String = "if-match"

    /** The value `*`: any current version of the resource matches. */
    case object Any extends IfMatch

    /**
     * A list of entity tags, stored as they appear in the header, quotes
     * included.
     */
    final case class ETags(etags: NonEmptyChunk[String]) extends IfMatch

    /**
     * Splits the value on commas and trims each entity tag, dropping empty
     * ones. A lone `*` gives `Any`. Returns `Left("Invalid If-Match header")`
     * when no tag is left.
     */
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

    /**
     * Renders `*` for `Any`, or the entity tags separated by `,` with no space.
     */
    def render(ifMatch: IfMatch): String = ifMatch match {
      case Any          => "*"
      case ETags(etags) => etags.mkString(",")
    }

  }

  /**
   * The `If-Modified-Since` request header: the server sends the resource only
   * when it has changed after this date.
   */
  final case class IfModifiedSince(value: ZonedDateTime) extends Header {
    override type Self = IfModifiedSince
    override def self: Self                                    = this
    override def headerType: HeaderType.Typed[IfModifiedSince] = IfModifiedSince
  }

  /**
   * The header type of `If-Modified-Since`, named `if-modified-since`: parses
   * and renders `IfModifiedSince` values.
   */
  object IfModifiedSince extends HeaderType {
    override type HeaderValue = IfModifiedSince

    override def name: String = "if-modified-since"

    /**
     * Parses an RFC 1123 date in GMT. Returns
     * `Left("Invalid If-Modified-Since header")` for other formats.
     */
    def parse(value: String): Either[String, IfModifiedSince] =
      DateEncoding.decodeDate(value).toRight("Invalid If-Modified-Since header").map(IfModifiedSince(_))

    /** Renders the date in RFC 1123 format, converted to GMT. */
    def render(ifModifiedSince: IfModifiedSince): String =
      DateEncoding.encodeDate(ifModifiedSince.value)
  }

  /**
   * The `If-None-Match` request header: the request applies only when the
   * resource matches none of the entity tags, or when it does not exist for
   * `*`.
   */
  sealed trait IfNoneMatch extends Header {
    override type Self = IfNoneMatch
    override def self: Self                                = this
    override def headerType: HeaderType.Typed[IfNoneMatch] = IfNoneMatch
  }

  /**
   * The header type of `If-None-Match`, named `if-none-match`: parses and
   * renders `IfNoneMatch` values.
   */
  object IfNoneMatch extends HeaderType {
    override type HeaderValue = IfNoneMatch

    override def name: String = "if-none-match"

    /** The value `*`: matches any current version of the resource. */
    case object Any extends IfNoneMatch

    /**
     * A list of entity tags, stored as they appear in the header, quotes
     * included.
     */
    final case class ETags(etags: NonEmptyChunk[String]) extends IfNoneMatch

    /**
     * Splits the value on commas and trims each entity tag, dropping empty
     * ones. A lone `*` gives `Any`. Returns
     * `Left("Invalid If-None-Match header")` when no tag is left.
     */
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

    /**
     * Renders `*` for `Any`, or the entity tags separated by `,` with no space.
     */
    def render(ifMatch: IfNoneMatch): String = ifMatch match {
      case Any          => "*"
      case ETags(etags) => etags.mkString(",")
    }
  }

  /**
   * The If-Range HTTP request header makes a range request conditional.
   * Possible values:
   *   - A date in HTTP date format, such as `Wed, 21 Oct 2015 07:28:00 GMT`.
   *   - An entity tag: a string of ASCII characters placed between double
   *     quotes (Like "675af34563dc-tr34"). A weak entity tag (one prefixed by
   *     W/) must not be used in this header.
   */
  sealed trait IfRange extends Header {
    override type Self = IfRange
    override def self: Self                            = this
    override def headerType: HeaderType.Typed[IfRange] = IfRange
  }

  /**
   * The header type of `If-Range`, named `if-range`: parses and renders
   * `IfRange` values.
   */
  object IfRange extends HeaderType {
    override type HeaderValue = IfRange

    override def name: String = "if-range"

    /**
     * An entity tag, stored without quotes and rendered between double quotes.
     */
    final case class ETag(value: String) extends IfRange

    /** A date, rendered in RFC 1123 format. */
    final case class DateTime(value: ZonedDateTime) extends IfRange

    /**
     * Parses a value between double quotes as an `ETag` and any other value as
     * an RFC 1123 date. Returns `Left("Invalid If-Range header")` when the date
     * does not parse, for example for a weak tag `W/"x"`.
     */
    def parse(value: String): Either[String, IfRange] =
      value match {
        case value if value.startsWith("\"") && value.endsWith("\"") =>
          Right(ETag(value.drop(1).dropRight(1)))
        case dateTime                                                =>
          DateEncoding.decodeDate(dateTime).toRight("Invalid If-Range header").map(DateTime(_))
      }

    /**
     * Renders the entity tag between double quotes, or the date in RFC 1123
     * format.
     */
    def render(ifRange: IfRange): String =
      ifRange match {
        case DateTime(value) => DateEncoding.encodeDate(value)
        case ETag(value)     => s""""$value""""
      }
  }

  /**
   * The `If-Unmodified-Since` request header, with the date after which the
   * resource must not have changed.
   */
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

    /**
     * Parses an RFC 1123 date in GMT. Returns
     * `Left("Invalid If-Unmodified-Since header")` for other formats.
     */
    def parse(value: String): Either[String, IfUnmodifiedSince] =
      DateEncoding.decodeDate(value).toRight("Invalid If-Unmodified-Since header").map(IfUnmodifiedSince(_))

    /** Renders the date in RFC 1123 format, converted to GMT. */
    def render(ifModifiedSince: IfUnmodifiedSince): String =
      DateEncoding.encodeDate(ifModifiedSince.value)

  }

  /**
   * The `Last-Modified` response header: the date and time at which the
   * resource last changed.
   */
  final case class LastModified(value: ZonedDateTime) extends Header {
    override type Self = LastModified
    override def self: Self                                 = this
    override def headerType: HeaderType.Typed[LastModified] = LastModified
  }

  /**
   * The header type of `Last-Modified`, named `last-modified`: parses and
   * renders `LastModified` values.
   */
  object LastModified extends HeaderType {
    override type HeaderValue = LastModified

    override def name: String = "last-modified"

    /**
     * Parses an RFC 1123 date in GMT. Returns
     * `Left("Invalid Last-Modified header")` for other formats.
     */
    def parse(value: String): Either[String, LastModified] =
      DateEncoding.decodeDate(value).toRight("Invalid Last-Modified header").map(LastModified(_))

    /** Renders the date in RFC 1123 format, converted to GMT. */
    def render(lastModified: LastModified): String =
      DateEncoding.encodeDate(lastModified.value)
  }

  /**
   * The `Link` header: a link to a related resource, with parameters such as
   * `rel`.
   */
  final case class Link(uri: URL, params: Map[String, String]) extends Header {
    override type Self = Link
    override def self: Self                         = this
    override def headerType: HeaderType.Typed[Link] = Link
  }

  /**
   * The header type of `Link`, named `link`: parses and renders `Link` values.
   */
  object Link extends HeaderType {
    override type HeaderValue = Link

    override def name: String = "link"

    /**
     * Parses one link: a URL between angle brackets, followed by `;`-separated
     * `key=value` parameters such as `rel="next"`, removing the quotes around
     * values. Returns `Left("Invalid Link header")` when there is no parameter,
     * the URL is not between angle brackets or is not valid, a parameter is not
     * `key=value`, or a key appears twice.
     */
    def parse(value: String): Either[String, Link] = {
      val parts = value.split(";").map(_.trim).filter(_.nonEmpty)
      if (parts.length < 2) Left("Invalid Link header")
      else if (!parts(0).startsWith("<") || !parts(0).endsWith(">")) Left("Invalid Link header")
      else {
        val uri = parts(0).substring(1, parts(0).length - 1)
        URL.decode(uri) match {
          case Left(_)    => Left("Invalid Link header")
          case Right(url) =>
            val params    = parts.drop(1).map { part =>
              val keyValue = part.split("=").map(_.trim).filter(_.nonEmpty)
              if (keyValue.length != 2) Left("Invalid Link header")
              else {
                val (key, value) = (keyValue(0), keyValue(1))
                val unquoted     =
                  if (value.startsWith("\"") && value.endsWith("\"")) value.substring(1, value.length - 1)
                  else value
                Right(key -> unquoted)
              }

            }
            val paramsMap = params.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) {
              case (Left(error), _)                  => Left(error)
              case (Right(map), Right((key, value))) =>
                if (map.contains(key)) Left("Invalid Link header")
                else Right(map + (key -> value))
              case _                                 => Left("Invalid Link header")
            }
            paramsMap.map(Link(url, _))
        }
      }
    }

    /**
     * Renders the URL between angle brackets, then `; ` and the parameters as
     * `key="value"`, separated by `; `.
     */
    def render(link: Link): String = {
      val params = link.params.map { case (key, value) => s"""$key="$value"""" }.mkString("; ")
      s"""<${link.uri.encode}>; $params"""
    }
  }

  /**
   * Location header value: the URL that a redirect or a `201 Created` response
   * points to.
   *
   * {{{
   * Header.Location(URL.root / "login").renderedValue // /login
   * Header.Location.parse("https://example.com/next").map(_.url.host)
   * // Right(Some(example.com))
   * }}}
   */
  final case class Location(url: URL) extends Header {
    override type Self = Location
    override def self: Self                             = this
    override def headerType: HeaderType.Typed[Location] = Location
  }

  /**
   * The header type of `Location`, named `location`: parses and renders
   * `Location` values.
   */
  object Location extends HeaderType {
    override type HeaderValue = Location

    override def name: String = "location"

    /**
     * Parses the value as a URL, absolute or relative. Returns
     * `Left("Invalid Location header (empty)")` for an empty value, and `Left`
     * with a message that starts with `Invalid Location header: ` when the URL
     * is not valid.
     */
    def parse(value: String): Either[String, Location] = {
      if (value == "") Left("Invalid Location header (empty)")
      else
        URL
          .decode(value)
          .left
          .map(error => s"Invalid Location header: $error")
          .map(url => Location(url))
    }

    /** Renders the encoded URL. */
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

  /**
   * The header type of `Max-Forwards`, named `max-forwards`: parses and renders
   * `MaxForwards` values.
   */
  object MaxForwards extends HeaderType {
    override type HeaderValue = MaxForwards

    override def name: String = "max-forwards"

    /**
     * Parses a non-negative `Int`. Returns
     * `Left("Invalid Max-Forwards header")` otherwise.
     */
    def parse(value: String): Either[String, MaxForwards] = {
      Try(value.toInt) match {
        case Success(value) if value >= 0L => Right(MaxForwards(value))
        case _                             => Left("Invalid Max-Forwards header")
      }
    }

    /** Renders the number. */
    def render(maxForwards: MaxForwards): String =
      maxForwards.value.toString
  }

  /** Origin header value. */
  sealed trait Origin extends Header {
    override type Self = Origin
    override def self: Self                           = this
    override def headerType: HeaderType.Typed[Origin] = Origin
  }

  /**
   * The header type of `Origin`, named `origin`: parses and renders `Origin`
   * values.
   */
  object Origin extends HeaderType {
    override type HeaderValue = Origin

    override def name: String = "origin"

    /** The Origin header value is privacy sensitive or is an opaque origin. */
    case object Null extends Origin

    /** The Origin header value contains scheme, host and maybe port. */
    final case class Value(scheme: String, host: String, port: Option[Int] = None) extends Origin

    /**
     * Creates an `Origin.Value` from a scheme, a host and an optional port.
     *
     * {{{
     * Header.Origin("https", "example.com", Some(8443)).renderedValue // https://example.com:8443
     * }}}
     */
    def apply(scheme: String, host: String, port: Option[Int] = None): Origin =
      Value(scheme, host, port)

    /**
     * Parses `null` as `Null`, or an absolute URL as a `Value` with its scheme,
     * host and port; a path or query in the URL is dropped. Returns
     * `Left("Invalid Origin header")` when the URL is not valid or has no
     * scheme or host.
     */
    def parse(value: String): Either[String, Origin] =
      if (value == "null") Right(Null)
      else
        URL.decode(value) match {
          case Left(_)                                              => Left("Invalid Origin header")
          case Right(url) if url.host.isEmpty || url.scheme.isEmpty => Left("Invalid Origin header")
          case Right(url)                                           => Right(Value(url.scheme.get.encode, url.host.get, url.port))
        }

    /**
     * Renders `null`, or `scheme://host` followed by `:port` when a port is
     * set.
     */
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

  /**
   * The header type of `Pragma`, named `pragma`: parses and renders `Pragma`
   * values.
   */
  object Pragma extends HeaderType {
    override type HeaderValue = Pragma

    override def name: String = "pragma"

    /** Pragma no-cache value. */
    case object NoCache extends Pragma

    /**
     * Parses `no-cache`, without regard to case. Returns
     * `Left("Invalid Pragma header")` for any other value.
     */
    def parse(value: String): Either[String, Pragma] =
      value.toLowerCase match {
        case "no-cache" => Right(NoCache)
        case _          => Left("Invalid Pragma header")
      }

    /** Renders `no-cache`. */
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

  /**
   * The header type of `Proxy-Authenticate`, named `proxy-authenticate`: parses
   * and renders `ProxyAuthenticate` values.
   */
  object ProxyAuthenticate extends HeaderType {
    override type HeaderValue = ProxyAuthenticate

    override def name: String = "proxy-authenticate"

    /**
     * Parses a scheme name, optionally followed by ` realm=` and the realm; the
     * realm is kept as written, quotes included. The scheme name is matched
     * without regard to case. Returns `Left` with a message that starts with
     * `Unsupported authentication scheme: ` for an unknown scheme.
     */
    def parse(value: String): Either[String, ProxyAuthenticate] = {
      val parts = value.split(" realm=").map(_.trim).filter(_.nonEmpty)
      parts match {
        case Array(authScheme, realm) => toProxyAuthenticate(authScheme, Some(realm))
        case Array(authScheme)        => toProxyAuthenticate(authScheme, None)
        case _                        => Left("Invalid Proxy-Authenticate header")
      }
    }

    /**
     * Renders the scheme name, followed by ` realm=` and the realm when a realm
     * is set.
     */
    def render(proxyAuthenticate: ProxyAuthenticate): String = proxyAuthenticate match {
      case ProxyAuthenticate(scheme, Some(realm)) => s"${scheme.name} realm=$realm"
      case ProxyAuthenticate(scheme, None)        => s"${scheme.name}"
    }

    private def toProxyAuthenticate(authScheme: String, realm: Option[String]): Either[String, ProxyAuthenticate] =
      AuthenticationScheme.parse(authScheme).map(ProxyAuthenticate(_, realm))
  }

  /**
   * The `Proxy-Authorization` request header: an authentication scheme and the
   * credential for a proxy, such as
   * `Proxy-Authorization: Basic YWxhZGRpbjpvcGVuc2VzYW1l`. The credential is
   * kept and rendered as given; for `Basic` it is the Base64 encoding of
   * `user:password`, which the caller supplies already encoded.
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

    /**
     * Parses a scheme name and a credential, two parts separated by whitespace,
     * such as `Basic YWxh`. The scheme name is matched without regard to case.
     * Returns `Left("Invalid Proxy-Authorization header")` when there are not
     * exactly two parts, and `Left` with a message that starts with
     * `Unsupported authentication scheme: ` for an unknown scheme.
     */
    def parse(value: String): Either[String, ProxyAuthorization] = {
      value.split("\\s+") match {
        case Array(authorization, credential) if authorization.nonEmpty && credential.nonEmpty =>
          AuthenticationScheme.parse(authorization).map { authenticationScheme =>
            ProxyAuthorization(authenticationScheme, credential)
          }
        case _                                                                                 => Left("Invalid Proxy-Authorization header")
      }
    }

    /** Renders the scheme name, a space and the credential. */
    def render(proxyAuthorization: ProxyAuthorization): String =
      s"${proxyAuthorization.authenticationScheme.name} ${proxyAuthorization.credential}"
  }

  /**
   * The `Range` request header: the parts of a resource that the client asks
   * for, in a unit such as `bytes`.
   *
   * {{{
   * Header.Range.parse("bytes=0-499")             // Right(Single(bytes,0,Some(499)))
   * Header.Range.Suffix("bytes", 500).renderedValue // bytes=-500
   * }}}
   */
  sealed trait Range extends Header {
    override type Self = Range
    override def self: Self                          = this
    override def headerType: HeaderType.Typed[Range] = Range
  }

  /**
   * The header type of `Range`, named `range`: parses and renders `Range`
   * values.
   */
  object Range extends HeaderType {
    override type HeaderValue = Range

    override def name: String = "range"

    /**
     * One range from `start` to `end`, both inclusive. Without an end it runs
     * to the end of the resource and renders as `unit=start-`.
     */
    final case class Single(unit: String, start: Long, end: Option[Long]) extends Range

    /**
     * Several ranges in one unit, each a start with an optional end, rendered
     * separated by `,`.
     */
    final case class Multiple(unit: String, ranges: List[(Long, Option[Long])]) extends Range

    /** The last `value` units of the resource, rendered as `unit=-value`. */
    final case class Suffix(unit: String, value: Long) extends Range

    /**
     * The range from `value` to the end of the resource, rendered as
     * `unit=value-`. `parse` returns `Single` with no end for this form
     * instead.
     */
    final case class Prefix(unit: String, value: Long) extends Range

    /**
     * Parses `unit=start-end`, `unit=start-` (both as `Single`), `unit=-length`
     * (as `Suffix`) and comma-separated lists of `start-end` or `start-` (as
     * `Multiple`). A value without `-` after the `=`, such as `bytes=500`, also
     * gives a `Suffix`. Returns `Left("Invalid Range header")` when there is
     * not exactly one `=` or a number does not parse.
     */
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

    /**
     * Renders `unit=` followed by the range in the forms that `parse` reads.
     */
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
   * Referrer-Policy for more information and examples. `render` writes the URL
   * as it is, so a fragment in `url` is kept.
   */
  final case class Referer(url: URL) extends Header {
    override type Self = Referer
    override def self: Self                            = this
    override def headerType: HeaderType.Typed[Referer] = Referer
  }

  /**
   * The header type of `Referer`, named `referer`: parses and renders `Referer`
   * values.
   */
  object Referer extends HeaderType {
    override type HeaderValue = Referer

    override def name: String = "referer"

    /**
     * Parses a `Referer` value as a URL. Returns
     * `Left("Invalid Referer header")` when the value is not a valid URL or has
     * no scheme or host, so relative URLs are rejected.
     */
    def parse(value: String): Either[String, Referer] = {
      URL.decode(value) match {
        case Left(_)                                              => Left("Invalid Referer header")
        case Right(url) if url.host.isEmpty || url.scheme.isEmpty => Left("Invalid Referer header")
        case Right(url)                                           => Right(Referer(url))
      }
    }

    /** Renders the encoded URL, or an empty string when the URL is relative. */
    def render(referer: Referer): String =
      if (referer.url.kind == URL.Location.Relative) "" else referer.url.encode
  }

  /**
   * The `Cookie` request header: one or more request cookies (name and content)
   * that the client sends back to the server.
   *
   * {{{
   * import zio.NonEmptyChunk
   *
   * val header = Header.Cookie(NonEmptyChunk(Cookie.Request("session", "abc123"), Cookie.Request("theme", "dark")))
   * header.renderedValue // session=abc123; theme=dark
   * }}}
   */
  final case class Cookie(value: NonEmptyChunk[zio.http.Cookie.Request]) extends Header {
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

    /**
     * Parses a `Cookie` value such as `a=1; b=2` into request cookies. Returns
     * `Left("Invalid Cookie header")` when the value contains no cookie, and
     * `Left` with a message that starts with `Invalid Cookie header: ` when
     * decoding throws.
     */
    def parse(value: String): Either[String, Cookie] =
      zio.http.Cookie.Request.decode(value) match {
        case Left(value)  => Left(s"Invalid Cookie header: ${value.getMessage}")
        case Right(value) =>
          NonEmptyChunk.fromChunk(value) match {
            case Some(value) => Right(Cookie(value))
            case None        => Left("Invalid Cookie header")
          }
      }

    /**
     * Renders the encoded cookies separated by `; `. A cookie that fails to
     * encode renders as an empty string.
     */
    def render(cookie: Cookie): String =
      cookie.value.map(_.encode.getOrElse("")).mkString("; ")
  }

  /**
   * The `Set-Cookie` response header: one response cookie, with its attributes
   * (domain, path, max age, `Secure`, `HttpOnly`, `SameSite`), that the server
   * asks the client to store.
   *
   * {{{
   * val header = Header.SetCookie(Cookie.Response("session", "abc123", isHttpOnly = true))
   * header.renderedValue // session=abc123; HTTPOnly
   * }}}
   */
  final case class SetCookie(value: zio.http.Cookie.Response) extends Header {
    override type Self = SetCookie
    override def self: Self                              = this
    override def headerType: HeaderType.Typed[SetCookie] = SetCookie
  }

  /**
   * The header type of `Set-Cookie`, named `set-cookie`: parses and renders
   * `SetCookie` values.
   */
  object SetCookie extends HeaderType {
    override type HeaderValue = SetCookie

    override def name: String = "set-cookie"

    /**
     * Parses a `Set-Cookie` value, such as `id=abc; Path=/; HttpOnly`, into a
     * response cookie. Returns `Left` with a message that starts with
     * `Invalid Cookie header: ` when decoding fails, for example for an empty
     * value.
     */
    def parse(value: String): Either[String, SetCookie] =
      zio.http.Cookie.Response.decode(value) match {
        case Left(value)  => Left(s"Invalid Cookie header: ${value.getMessage}")
        case Right(value) => Right(SetCookie(value))
      }

    /**
     * Renders the encoded response cookie with its attributes, or an empty
     * string when encoding fails.
     */
    def render(cookie: SetCookie): String =
      cookie.value.encode.getOrElse("")
  }

  /**
   * The `Retry-After` response header: how long the client should wait before
   * its next request, either as a date (`ByDate`) or as a delay (`ByDuration`).
   */
  sealed trait RetryAfter extends Header {
    override type Self = RetryAfter
    override def self: Self                               = this
    override def headerType: HeaderType.Typed[RetryAfter] = RetryAfter
  }

  /**
   * The header type of `Retry-After`, named `retry-after`. The value is either
   * an HTTP date, such as `Retry-After: Wed, 21 Oct 2015 07:28:00 GMT`, or a
   * delay in seconds, such as `Retry-After: 120`.
   */
  object RetryAfter extends HeaderType {
    override type HeaderValue = RetryAfter

    override def name: String = "retry-after"

    /** A `Retry-After` value that is a date and time. */
    final case class ByDate(date: ZonedDateTime) extends RetryAfter

    /**
     * A `Retry-After` value that is a delay. It is rendered in whole seconds.
     */
    final case class ByDuration(delay: Duration) extends RetryAfter

    /**
     * Parses a `Retry-After` value: a non-negative whole number gives a
     * `ByDuration` of that many seconds, and an RFC 1123 date such as
     * `Wed, 21 Oct 2015 07:28:00 GMT` gives a `ByDate`. Returns
     * `Left("Invalid RetryAfter")` otherwise, for example for a negative
     * number.
     */
    def parse(dateOrSeconds: String): Either[String, RetryAfter] =
      Try(dateOrSeconds.toLong) match {
        case Failure(_)     =>
          DateEncoding.decodeDate(dateOrSeconds) match {
            case Some(value) => Right(ByDate(value))
            case None        => Left("Invalid RetryAfter")
          }
        case Success(value) =>
          if (value >= 0)
            Right(ByDuration(value.seconds))
          else
            Left("Invalid RetryAfter")
      }

    /**
     * Renders a `ByDate` as an RFC 1123 date and a `ByDuration` as its whole
     * number of seconds.
     */
    def render(retryAfter: RetryAfter): String =
      retryAfter match {
        case ByDate(date)         => DateEncoding.encodeDate(date)
        case ByDuration(duration) =>
          duration.getSeconds.toString
      }
  }

  /**
   * The `Sec-WebSocket-Accept` header that a server sends in the WebSocket
   * opening handshake. `hashedKey` is the value derived from the client's
   * `Sec-WebSocket-Key`.
   */
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

    /**
     * Keeps the value as it is. Returns
     * `Left("Invalid Sec-WebSocket-Accept header")` when the value is empty or
     * only whitespace.
     */
    def parse(value: String): Either[String, SecWebSocketAccept] =
      if (value.trim.isEmpty) Left("Invalid Sec-WebSocket-Accept header")
      else Right(SecWebSocketAccept(value))

    /** Renders `hashedKey` unchanged. */
    def render(secWebSocketAccept: SecWebSocketAccept): String =
      secWebSocketAccept.hashedKey
  }

  /**
   * The `Sec-WebSocket-Extensions` header: the WebSocket extensions, with their
   * parameters, that the client offers or the server accepts.
   */
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

    /**
     * One part of a WebSocket extension: its name, or a parameter with or
     * without a value.
     */
    sealed trait Extension

    /** The two kinds of `Extension` part. */
    object Extension {

      /** A part with a value, rendered as `name=value`. */
      final case class Parameter(name: String, value: String) extends Extension

      /**
       * A part without a value: an extension name or a parameter name, rendered
       * as it is.
       */
      final case class TokenParam(name: String) extends Extension
    }

    /** One extension: its parts, rendered separated by `; `. */
    final case class Token(extension: Chunk[Extension]) extends SecWebSocketExtensions

    /**
     * A list of extensions, rendered separated by `, `. `parse` returns this
     * case.
     */
    final case class Extensions(extensions: Chunk[Token]) extends SecWebSocketExtensions

    /**
     * Parses a comma-separated list of extensions, each made of `;`-separated
     * parts, into `Extensions`. In an extension with several parts, a part with
     * `=` becomes a `Parameter`; an extension with one part becomes a
     * `TokenParam`, even if it contains `=`. Returns
     * `Left("Invalid Sec-WebSocket-Extensions header")` for an empty or blank
     * value.
     */
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

    /**
     * Renders the extensions separated by `, ` and the parts of each extension
     * separated by `; `.
     */
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

  /**
   * The `Sec-WebSocket-Key` header that a client sends in the WebSocket opening
   * handshake: a Base64-encoded 16-byte value.
   */
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

    /**
     * Keeps the key when it is valid Base64 that decodes to 16 bytes. Returns
     * `Left("Invalid Sec-WebSocket-Key header")` otherwise.
     */
    def parse(key: String): Either[String, SecWebSocketKey] = {
      try {
        val decodedKey = java.util.Base64.getDecoder.decode(key)
        if (decodedKey.length == 16) Right(SecWebSocketKey(key))
        else Left("Invalid Sec-WebSocket-Key header")
      } catch {
        case NonFatal(_) => Left("Invalid Sec-WebSocket-Key header")
      }

    }

    /** Renders the Base64 key unchanged. */
    def render(secWebSocketKey: SecWebSocketKey): String =
      secWebSocketKey.base64EncodedKey
  }

  /** The `Sec-WebSocket-Location` header: the URL of the WebSocket endpoint. */
  final case class SecWebSocketLocation(url: URL) extends Header {
    override type Self = SecWebSocketLocation
    override def self: Self                                         = this
    override def headerType: HeaderType.Typed[SecWebSocketLocation] = SecWebSocketLocation
  }

  /**
   * The header type of `Sec-WebSocket-Location`, named
   * `sec-websocket-location`: parses and renders `SecWebSocketLocation` values.
   */
  object SecWebSocketLocation extends HeaderType {
    override type HeaderValue = SecWebSocketLocation

    override def name: String = "sec-websocket-location"

    /**
     * Parses the value as a URL; a relative URL is accepted. Returns
     * `Left("Invalid Sec-WebSocket-Location header: empty value")` for a blank
     * value and `Left("Invalid Sec-WebSocket-Location header: invalid URL")`
     * when it is not a valid URL.
     */
    def parse(value: String): Either[String, SecWebSocketLocation] = {
      if (value.trim == "") Left("Invalid Sec-WebSocket-Location header: empty value")
      else
        URL
          .decode(value)
          .left
          .map(_ => "Invalid Sec-WebSocket-Location header: invalid URL")
          .map(url => SecWebSocketLocation(url))
    }

    /** Renders the encoded URL. */
    def render(secWebSocketLocation: SecWebSocketLocation): String =
      secWebSocketLocation.url.encode
  }

  /**
   * The `Sec-WebSocket-Origin` header: the origin of the script that opens the
   * WebSocket connection.
   */
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

    /**
     * Parses the value as a URL. Returns
     * `Left("Invalid Sec-WebSocket-Origin header: empty value")` for a blank
     * value and `Left("Invalid Sec-WebSocket-Origin header: invalid URL")` when
     * it is not a valid URL.
     */
    def parse(value: String): Either[String, SecWebSocketOrigin] = {
      if (value.trim == "") Left("Invalid Sec-WebSocket-Origin header: empty value")
      else
        URL
          .decode(value)
          .left
          .map(_ => "Invalid Sec-WebSocket-Origin header: invalid URL")
          .map(url => SecWebSocketOrigin(url))
    }

    /** Renders the encoded URL. */
    def render(secWebSocketOrigin: SecWebSocketOrigin): String = {
      secWebSocketOrigin.url.encode

    }
  }

  /**
   * The `Sec-WebSocket-Protocol` header: the WebSocket subprotocols that the
   * client offers, or the one that the server selects.
   */
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

    /**
     * Splits the value on commas and trims each subprotocol, dropping empty
     * ones. Returns `Left("Invalid Sec-WebSocket-Protocol header")` when none
     * is left.
     */
    def parse(subProtocols: String): Either[String, SecWebSocketProtocol] = {
      NonEmptyChunk.fromChunk(Chunk.fromArray(subProtocols.split(",")).map(_.trim).filter(_.nonEmpty)) match {
        case Some(value) => Right(SecWebSocketProtocol(value))
        case None        => Left("Invalid Sec-WebSocket-Protocol header")
      }
    }

    /** Renders the subprotocols separated by `, `. */
    def render(secWebSocketProtocol: SecWebSocketProtocol): String =
      secWebSocketProtocol.subProtocols.mkString(", ")
  }

  /**
   * The `Sec-WebSocket-Version` header: the WebSocket protocol version that the
   * client wants to use.
   */
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

    /**
     * Parses a version number from 0 to 13. Returns
     * `Left("Invalid Sec-WebSocket-Version header")` for another number or a
     * value that is not a number.
     */
    def parse(version: String): Either[String, SecWebSocketVersion] =
      try {
        val v = version.toInt
        if (v >= 0 && v <= 13) Right(SecWebSocketVersion(v))
        else Left("Invalid Sec-WebSocket-Version header")
      } catch {
        case NonFatal(_) => Left("Invalid Sec-WebSocket-Version header")
      }

    /** Renders the version number. */
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

  /**
   * The header type of `Server`, named `server`: parses and renders `Server`
   * values.
   */
  object Server extends HeaderType {
    override type HeaderValue = Server

    override def name: String = "server"

    /**
     * Trims the value and keeps it as the server name. Returns
     * `Left("Invalid Server header: empty value")` when nothing is left.
     */
    def parse(value: String): Either[String, Server] = {
      val trimmedValue = value.trim
      if (trimmedValue.isEmpty)
        Left("Invalid Server header: empty value")
      else Right(Server(trimmedValue))
    }

    /** Renders the server name. */
    def render(server: Server): String =
      server.name
  }

  /**
   * The Strict-Transport-Security response header informs browsers that the
   * site should only be accessed using HTTPS, and that any future attempts to
   * access it using HTTP should automatically be converted to HTTPS.
   *
   * See:
   * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Strict-Transport-Security
   */
  final case class StrictTransportSecurity(
    maxAge: Long,
    includeSubDomains: Boolean = false,
    preload: Boolean = false,
  ) extends Header {
    override type Self = StrictTransportSecurity
    override def self: Self                                            = this
    override def headerType: HeaderType.Typed[StrictTransportSecurity] = StrictTransportSecurity
  }

  /**
   * The header type of `Strict-Transport-Security`, named
   * `strict-transport-security`: parses and renders `StrictTransportSecurity`
   * values.
   */
  object StrictTransportSecurity extends HeaderType {
    override type HeaderValue = StrictTransportSecurity

    override def name: String = "strict-transport-security"

    /**
     * Parses the `;`-separated directives, with directive names matched without
     * regard to case. `max-age` must be present with a non-negative whole
     * number; `includeSubDomains` and `preload` set their flags, and other
     * directives are ignored. Returns
     * `Left("Invalid Strict-Transport-Security header")` otherwise.
     */
    def parse(value: String): Either[String, StrictTransportSecurity] = {
      val parts             = value.split(";").map(_.trim).filter(_.nonEmpty)
      var maxAge: Long      = -1
      var includeSubDomains = false
      var preload           = false
      var valid             = true

      parts.foreach { part =>
        val lower = part.toLowerCase
        if (lower.startsWith("max-age=")) {
          try {
            val v = part.substring(8).trim.toLong
            if (v >= 0) maxAge = v
            else valid = false
          } catch {
            case NonFatal(_) => valid = false
          }
        } else if (lower == "includesubdomains") {
          includeSubDomains = true
        } else if (lower == "preload") {
          preload = true
        }
      }

      if (valid && maxAge >= 0)
        Right(StrictTransportSecurity(maxAge, includeSubDomains, preload))
      else
        Left("Invalid Strict-Transport-Security header")
    }

    /**
     * Renders `max-age=` and the number of seconds, followed by
     * `; includeSubDomains` and `; preload` when those flags are set.
     */
    def render(sts: StrictTransportSecurity): String = {
      val sb = new StringBuilder
      sb.append("max-age=")
      sb.append(sts.maxAge)
      if (sts.includeSubDomains) sb.append("; includeSubDomains")
      if (sts.preload) sb.append("; preload")
      sb.toString
    }
  }

  /**
   * The `TE` request header: the transfer codings the client accepts, each with
   * an optional quality weight, and whether it accepts trailer fields.
   */
  sealed trait Te extends Header {
    override type Self = Te
    override def self: Self                       = this
    override def headerType: HeaderType.Typed[Te] = Te

    /**
     * The coding name, such as `gzip`. For `Multiple`, the `toString` of each
     * entry joined with commas.
     */
    def raw: String
  }

  /** The header type of `TE`, named `te`: parses and renders `Te` values. */
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
     * The `trailers` keyword: the client accepts trailer fields in a chunked
     * response. It has no weight.
     */
    case object Trailers extends Te {
      override def raw: String = "trailers"
    }

    /**
     * Several `TE` entries, rendered separated by `, `.
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

    /**
     * Parses a comma-separated list of `compress`, `deflate`, `gzip` and
     * `trailers`, each optionally followed by a weight such as `;q=0.5`; the
     * names are case-sensitive. One entry gives that value, several give
     * `Multiple`. Returns `Left("Invalid TE header")` when any entry is not one
     * of these.
     */
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

    /**
     * Renders each coding name, with a weight suffix such as `;q=0.5` when a
     * weight is set, and separates the entries of `Multiple` with `, `.
     */
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

  /**
   * The header type of `Trailer`, named `trailer`: parses and renders `Trailer`
   * values.
   */
  object Trailer extends HeaderType {
    override type HeaderValue = Trailer

    override def name: String = "trailer"

    private val headerRegex = "([a-z-_]*)".r

    /**
     * Lower-cases the value and keeps it as the header name when it contains
     * only letters, `-` and `_`; an empty value is accepted. Returns
     * `Left("Invalid Trailer header")` otherwise.
     */
    def parse(value: String): Either[String, Trailer] =
      value.toLowerCase match {
        case headerRegex(value) => Right(Trailer(value))
        case _                  => Left("Invalid Trailer header")
      }

    /** Renders the header name. */
    def render(trailer: Trailer): String =
      trailer.header
  }

  /**
   * The `Transfer-Encoding` header: the codings applied to the message body to
   * transfer it, such as `chunked` or `gzip`.
   */
  sealed trait TransferEncoding extends Header {
    override type Self = TransferEncoding
    override def self: Self                                     = this
    override def headerType: HeaderType.Typed[TransferEncoding] = TransferEncoding

    /**
     * The coding name, such as `chunked`. For `Multiple`, the names joined with
     * commas.
     */
    val encoding: String
  }

  /**
   * The header type of `Transfer-Encoding`, named `transfer-encoding`: parses
   * and renders `TransferEncoding` values.
   */
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
     * recognize x-gzip as an alias, for compatibility purposes. `parse` does
     * not accept `x-gzip`.
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
     * Parses a comma-separated list of `chunked`, `compress`, `deflate` and
     * `gzip` (case-sensitive). Entries with other names are dropped. One known
     * entry gives that value, several give `Multiple`. Returns
     * `Left("Empty TransferEncoding")` when no known entry is left.
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

    /**
     * Renders `encoding`: the coding name, or for `Multiple` the names joined
     * with `,` and no space.
     */
    def render(value: TransferEncoding): String = value.encoding

  }

  /**
   * The `Upgrade` header: the protocols that the sender wants to switch the
   * connection to, such as `websocket`.
   */
  sealed trait Upgrade extends Header {
    override type Self = Upgrade
    override def self: Self                            = this
    override def headerType: HeaderType.Typed[Upgrade] = Upgrade
  }

  /**
   * The header type of `Upgrade`, named `upgrade`: parses and renders `Upgrade`
   * values.
   */
  object Upgrade extends HeaderType {
    override type HeaderValue = Upgrade

    override def name: String = "upgrade"

    /** Several protocols, rendered separated by `, `. */
    final case class Multiple(protocols: NonEmptyChunk[Protocol]) extends Upgrade

    /**
     * One protocol with its version, rendered as `protocol/version`.
     * `Protocol("websocket", "")` renders as `websocket`.
     */
    final case class Protocol(protocol: String, version: String) extends Upgrade

    /**
     * Parses a comma-separated list of `name/version` entries; `websocket`
     * (lower case) is also accepted without a version. One entry gives a
     * `Protocol`, several give `Multiple`. Returns
     * `Left("Invalid Upgrade header")` when an entry has another shape, for
     * example `h2c`.
     */
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

    /**
     * Renders each protocol as `protocol/version`, or `websocket` for the
     * WebSocket protocol without a version, separated by `, `.
     */
    def render(upgrade: Upgrade): String =
      upgrade match {
        case Multiple(protocols)         => protocols.map(render).mkString(", ")
        case Protocol("websocket", "")   => "websocket"
        case Protocol(protocol, version) => s"$protocol/$version"
      }

    private def parseProtocol(value: String): Either[String, Protocol] =
      Chunk.fromArray(value.split("/")).map(_.trim) match {
        case Chunk("websocket")       => Right(Protocol("websocket", ""))
        case Chunk(protocol, version) => Right(Protocol(protocol, version))
        case _                        => Left("Invalid Upgrade header")
      }
  }

  /** The `Upgrade-Insecure-Requests` request header, always rendered as `1`. */
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

    /**
     * Accepts `1`, with surrounding whitespace allowed. Returns
     * `Left("Invalid Upgrade-Insecure-Requests header")` otherwise.
     */
    def parse(value: String): Either[String, UpgradeInsecureRequests] =
      if (value.trim == "1") Right(UpgradeInsecureRequests())
      else Left("Invalid Upgrade-Insecure-Requests header")

    /** Renders `1`. */
    def render(upgradeInsecureRequests: UpgradeInsecureRequests): String =
      "1"
  }

  /**
   * The `User-Agent` request header: a first product with an optional version,
   * followed by more products and comments.
   *
   * {{{
   * val header = Header.UserAgent(Header.UserAgent.ProductOrComment.Product("zio-http", Some("3.12.0")))
   * header.renderedValue // zio-http/3.12.0
   *
   * Header.UserAgent.parse("Mozilla/5.0 (X11; Linux x86_64) Firefox/42.0").map(_.parts)
   * // Right(List(Comment(X11; Linux x86_64), Product(Firefox,Some(42.0))))
   * }}}
   */
  final case class UserAgent(
    product: UserAgent.ProductOrComment.Product,
    parts: List[UserAgent.ProductOrComment] = Nil,
  ) extends Header {
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

    /**
     * A part of a `User-Agent` value: a product token or a comment in
     * parentheses.
     */
    sealed trait ProductOrComment

    /** The two kinds of `User-Agent` part. */
    object ProductOrComment {

      /**
       * A product, rendered as `name/version`, or `name` when there is no
       * version.
       */
      final case class Product(name: String, version: Option[String]) extends ProductOrComment

      /**
       * A comment, stored without its parentheses and rendered as `(comment)`.
       */
      final case class Comment(comment: String) extends ProductOrComment
    }

    private val tokenPattern          = "[!#$%&'*+\\-.^_`|~\\w]"
    private val commentPattern        = "\\((.*?)\\)"
    private val productPattern        = s"($tokenPattern+)(?:/($tokenPattern+))?"
    private val productRegex          = s"(?i)$productPattern\\s*?(.*)".r
    private val commentRegex          = s"(?i)$commentPattern".r
    private val productOrCommentRegex = s"(?i)(?:(?:$productPattern)|(?:$commentPattern))".r

    private def hasBalancedParentheses(s: String): Boolean = {
      val (balance, isValid) = s.foldLeft((0, true)) {
        case ((balance, true), '(') => (balance + 1, true)
        case ((balance, true), ')') =>
          if (balance > 0) (balance - 1, true)
          else (balance, false)
        case (state, _)             => state
      }
      isValid && balance == 0
    }

    /**
     * Parses a product, such as `Mozilla/5.0`, followed by products and
     * parenthesized comments. Returns `Left("Invalid User-Agent header")` when
     * the value does not start with a product or its parentheses are not
     * balanced.
     */
    def parse(userAgent: String): Either[String, UserAgent] = {
      userAgent match {
        case productRegex(name, version, tail) if hasBalancedParentheses(tail) =>
          val product = ProductOrComment.Product(name, Option(version))
          val parts   = productOrCommentRegex.findAllIn(tail).toList.collect {
            case productRegex(name, version, _) => ProductOrComment.Product(name, Option(version))
            case commentRegex(comment)          => ProductOrComment.Comment(comment)
          }
          Right(UserAgent(product, parts))
        case _                                                                 =>
          Left("Invalid User-Agent header")
      }
    }

    /** Renders the product followed by the other parts, separated by spaces. */
    def render(userAgent: UserAgent): String =
      (userAgent.product :: userAgent.parts).map(fromProductOrComment).mkString(" ")

    private def fromProductOrComment(value: ProductOrComment): String = value match {
      case ProductOrComment.Product(name, version) =>
        s"""$name${version.map("/" + _).getOrElse("")}"""
      case ProductOrComment.Comment(comment)       =>
        s"($comment)"
    }

  }

  /** Vary header value. */
  sealed trait Vary extends Header {
    override type Self = Vary
    override def self: Self                         = this
    override def headerType: HeaderType.Typed[Vary] = Vary
  }

  /**
   * The header type of `Vary`, named `vary`: parses and renders `Vary` values.
   */
  object Vary extends HeaderType {
    override type HeaderValue = Vary

    override def name: String = "vary"

    /** The names of the request headers that the response varies on. */
    case class Headers(headers: NonEmptyChunk[String]) extends Vary

    /** The value `*`: the response varies on more than request headers. */
    case object Star extends Vary

    /** Creates a `Vary` value that lists these header names. */
    def apply(first: String, rest: String*): Vary = Headers(NonEmptyChunk(first, rest: _*))

    /**
     * Lower-cases the value and splits it on commas and spaces. A lone `*`
     * gives `Star`, other values give `Headers`. Returns
     * `Left("Invalid Vary header")` for an empty value.
     */
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

    /** Renders `*` for `Star`, or the header names separated by `, `. */
    def render(vary: Vary): String = {
      vary match {
        case Star          => "*"
        case Headers(list) => list.mkString(", ")
      }
    }
  }

  /**
   * The `Via` header: the proxies that the message passed through, each with
   * the protocol it was received with and an optional comment.
   */
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

    /**
     * The protocol a proxy received the message with: a version alone, or a
     * protocol name and version.
     */
    sealed trait ReceivedProtocol

    /** The two forms of `ReceivedProtocol`. */
    object ReceivedProtocol {

      /** A version without a protocol name, such as `1.1`. */
      final case class Version(version: String) extends ReceivedProtocol

      /** A protocol name with a version, such as `HTTP/1.1`. */
      final case class ProtocolVersion(protocol: String, version: String) extends ReceivedProtocol
    }

    /**
     * One proxy entry: the received protocol, the host or pseudonym of the
     * proxy, and an optional comment. The comment is kept with its parentheses.
     */
    final case class Detailed(receivedProtocol: ReceivedProtocol, receivedBy: String, comment: Option[String]) extends Via

    /** Several proxy entries, rendered separated by `, `. */
    final case class Multiple(values: NonEmptyChunk[Via]) extends Via

    /**
     * Parses a comma-separated list of entries, each made of two or three
     * space-separated parts: the received protocol, the receiver and an
     * optional comment without spaces. One entry gives `Detailed`, several give
     * `Multiple`. Returns `Left("Invalid Via header")`, or
     * `Left("Invalid received protocol")`, when an entry has another shape.
     */
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

    /**
     * Renders each entry as the received protocol, the receiver and the
     * comment, separated by spaces, and separates the entries with `, `. An
     * entry without a comment ends with a space.
     */
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

  /**
   * The `WWW-Authenticate` response header: the authentication scheme, and its
   * parameters, that the client must use to get access to a resource.
   */
  sealed trait WWWAuthenticate extends Header {
    override type Self = WWWAuthenticate
    override def self: Self                                    = this
    override def headerType: HeaderType.Typed[WWWAuthenticate] = WWWAuthenticate
  }

  /**
   * The header type of `WWW-Authenticate`, named `www-authenticate`: parses and
   * renders `WWWAuthenticate` values.
   */
  object WWWAuthenticate extends HeaderType {
    override type HeaderValue = WWWAuthenticate

    override def name: String = "www-authenticate"

    /**
     * A challenge of the `Basic` scheme, with an optional realm and a charset
     * (`UTF-8` by default). `render` writes the charset as a parameter named
     * after its own value, for example `UTF-8="UTF-8"`.
     */
    final case class Basic(realm: Option[String] = None, charset: String = "UTF-8") extends WWWAuthenticate

    /**
     * A challenge of the `Bearer` scheme. `parse` reads `error_description`
     * into `errorDescription` and fails when `realm` is missing.
     */
    final case class Bearer(
      realm: String,
      scope: Option[String] = None,
      error: Option[String] = None,
      errorDescription: Option[String] = None,
    ) extends WWWAuthenticate

    /**
     * A challenge of the `Digest` scheme. `render` writes `stale` and
     * `userhash` as `false` when they are not set.
     */
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

    /**
     * A challenge of the `HOBA` scheme. `maxAge` is the `max_age` parameter.
     */
    final case class HOBA(realm: Option[String], challenge: String, maxAge: Int) extends WWWAuthenticate

    /** A challenge of the `Mutual` scheme. */
    final case class Mutual(realm: String, error: Option[String] = None, errorDescription: Option[String] = None) extends WWWAuthenticate

    /**
     * A challenge of the `Negotiate` scheme. `authData` is the text after the
     * scheme name, kept as it is.
     */
    final case class Negotiate(authData: Option[String] = None) extends WWWAuthenticate

    /** A challenge of the `SCRAM` scheme. */
    final case class SCRAM(
      realm: String,
      sid: String,
      data: String,
    ) extends WWWAuthenticate

    /**
     * A challenge of the `AWS4-HMAC-SHA256` scheme. `parse` does not produce
     * it, because it reads only a scheme name made of letters, digits and `_`.
     */
    final case class `AWS4-HMAC-SHA256`(
      realm: String,
      credentials: Option[String] = None,
      signedHeaders: String,
      signature: String,
    ) extends WWWAuthenticate

    /**
     * A challenge of a scheme without its own case, such as `OAuth`, with all
     * of its parameters. `parse` produces it for `OAuth` and `vapid` challenges
     * that have a `realm`.
     */
    final case class Unknown(scheme: String, realm: String, params: Map[String, String]) extends WWWAuthenticate

    private val challengeRegEx  = """(\w+) (.*)""".r
    private val auth            = """(\w+)=(?:"([^"]+)"|([^,]+))""".r
    private val nonQuotedValues = Set("max_age", "stale", "userhash", "algorithm", "charset")

    /**
     * Parses a challenge such as `Bearer realm="api", scope="read"`: a scheme
     * name, a space, and comma-separated `key=value` or `key="value"`
     * parameters. The scheme name is matched without regard to case. Returns
     * `Left("Invalid WWW-Authenticate header")` when the shape is wrong or a
     * required parameter is missing, and `Left` with a message that starts with
     * `Unsupported authentication scheme: ` for an unknown scheme.
     */
    def parse(value: String): Either[String, WWWAuthenticate] =
      Try {
        val challengeRegEx(scheme, challenge) = value: @unchecked
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

    /**
     * Renders the scheme name followed by the non-empty parameters as
     * `key="value"`, separated by `, `. The parameters `max_age`, `stale`,
     * `userhash`, `algorithm` and `charset` are written without quotes, and
     * `Negotiate` data is written as it is.
     */
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

  /**
   * The `X-Frame-Options` response header: whether a browser may show the page
   * in a frame (`Deny` or `SameOrigin`).
   */
  sealed trait XFrameOptions extends Header {
    override type Self = XFrameOptions
    override def self: Self                                  = this
    override def headerType: HeaderType.Typed[XFrameOptions] = XFrameOptions
  }

  /**
   * The header type of `X-Frame-Options`, named `x-frame-options`: parses and
   * renders `XFrameOptions` values.
   */
  object XFrameOptions extends HeaderType {
    override type HeaderValue = XFrameOptions

    override def name: String = "x-frame-options"

    /** The page must not be shown in a frame. Rendered as `DENY`. */
    case object Deny extends XFrameOptions

    /**
     * The page may be shown in a frame on the same origin only. Rendered as
     * `SAMEORIGIN`.
     */
    case object SameOrigin extends XFrameOptions

    /**
     * Parses `DENY` or `SAMEORIGIN`, trimmed and without regard to case.
     * Returns `Left("Invalid X-Frame-Options header")` otherwise.
     */
    def parse(value: String): Either[String, XFrameOptions] = {
      value.trim.toUpperCase match {
        case "DENY"       => Right(Deny)
        case "SAMEORIGIN" => Right(SameOrigin)
        case _            => Left("Invalid X-Frame-Options header")
      }
    }

    /** Renders `DENY` or `SAMEORIGIN`. */
    def render(xFrameOptions: XFrameOptions): String =
      xFrameOptions match {
        case Deny       => "DENY"
        case SameOrigin => "SAMEORIGIN"
      }

  }

  /**
   * The `X-Requested-With` request header, such as `XMLHttpRequest`, which some
   * clients send to mark script-made requests.
   */
  final case class XRequestedWith(value: String) extends Header {
    override type Self = XRequestedWith
    override def self: Self                                   = this
    override def headerType: HeaderType.Typed[XRequestedWith] = XRequestedWith
  }

  /**
   * The header type of `X-Requested-With`, named `x-requested-with`: parses and
   * renders `XRequestedWith` values.
   */
  object XRequestedWith extends HeaderType {
    override type HeaderValue = XRequestedWith
    override def name: String = "x-requested-with"

    /** Keeps any value unchanged; it never fails. */
    def parse(value: String): Either[String, XRequestedWith] =
      Right(XRequestedWith(value))

    /** Renders the value unchanged. */
    def render(xRequestedWith: XRequestedWith): String =
      xRequestedWith.value
  }

}
