package zio.http.model.headers.values

sealed trait AcceptCharset {
  val raw: String
}

/**
 * The Accept-Charset request HTTP header was a header that advertised a
 * client's supported character encodings. It is no longer widely used. UTF-8 is
 * well-supported and the overwhelmingly preferred choice for character
 * encoding. To guarantee better privacy through less configuration-based
 * entropy, all browsers omit the Accept-Charset header. Chrome, Firefox,
 * Internet Explorer, Opera, and Safari abandoned this header.
 */
object AcceptCharset {

  /**
   * Seven-bit ASCII, a.k.a. ISO646-US, a.k.a. the Basic Latin block of the
   * Unicode character set
   */
  object `US-ASCII` extends AcceptCharset {
    override val raw: String = "US-ASCII"
  }

  /**
   * ISO Latin Alphabet No. 1, a.k.a. ISO-LATIN-1
   */
  object `ISO-8859-1` extends AcceptCharset {
    override val raw: String = "ISO-8859-1"
  }

  /**
   * Eight-bit UCS Transformation Format
   */
  object `UTF-8` extends AcceptCharset {
    override val raw: String = "UTF-8"
  }

  /**
   * Sixteen-bit UCS Transformation Format, big-endian byte order
   */
  object `UTF-16BE` extends AcceptCharset {
    override val raw: String = "UTF-16BE"
  }

  /**
   * Sixteen-bit UCS Transformation Format, little-endian byte order
   */
  object `UTF-16LE` extends AcceptCharset {
    override val raw: String = "UTF-16LE"
  }

  /**
   * Sixteen-bit UCS Transformation Format, byte order identified by an optional
   * byte-order mark
   */
  object `UTF-16` extends AcceptCharset {
    override val raw: String = "UTF-16"
  }

  def toCharset(value: String): AcceptCharset = value match {
    case `UTF-8`.raw      => `UTF-8`
    case `US-ASCII`.raw   => `US-ASCII`
    case `ISO-8859-1`.raw => `ISO-8859-1`
    case `UTF-16BE`.raw   => `UTF-16BE`
    case `UTF-16LE`.raw   => `UTF-16LE`
    case `UTF-16`.raw     => `UTF-16`
    case _                => `UTF-8`
  }

  def fromCharset(charset: AcceptCharset): String = charset.raw
}
