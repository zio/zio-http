package zio.http.model.headers.values

import zio.Chunk

/**
 * Represents an AcceptEncoding header value.
 */
sealed trait AcceptEncoding {
  val raw: String
}

object AcceptEncoding {

  /**
   * Signals an invalid value present in the header value.
   */
  case object InvalidEncoding extends AcceptEncoding {
    override val raw: String = "Invalid header value"
  }

  /**
   * A compression format that uses the Brotli algorithm.
   */
  final case class BrEncoding(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "br"
  }

  /**
   * A compression format that uses the Lempel-Ziv-Welch (LZW) algorithm.
   */
  final case class CompressEncoding(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "compress"
  }

  /**
   * A compression format that uses the zlib structure with the deflate
   * compression algorithm.
   */
  final case class DeflateEncoding(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "deflate"
  }

  /**
   * A compression format that uses the Lempel-Ziv coding (LZ77) with a 32-bit
   * CRC.
   */
  final case class GZipEncoding(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "gzip"
  }

  /**
   * Indicates the identity function (that is, without modification or
   * compression). This value is always considered as acceptable, even if
   * omitted.
   */
  final case class IdentityEncoding(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "identity"
  }

  /**
   * Maintains a chunk of AcceptEncoding values.
   */
  final case class MultipleEncodings(encodings: Chunk[AcceptEncoding]) extends AcceptEncoding {
    override val raw: String = encodings.map(_.raw).mkString(",")
  }

  /**
   * Matches any content encoding not already listed in the header. This is the
   * default value if the header is not present.
   */
  final case class NoPreferenceEncoding(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "*"
  }

  private def identifyEncoding(raw: String, weight: Option[Double] = None): AcceptEncoding = {
    raw.trim match {
      case "br"       => BrEncoding(weight)
      case "compress" => CompressEncoding(weight)
      case "deflate"  => DeflateEncoding(weight)
      case "gzip"     => GZipEncoding(weight)
      case "identity" => IdentityEncoding(weight)
      case "*"        => NoPreferenceEncoding(weight)
      case _          => InvalidEncoding
    }
  }

  def toAcceptEncoding(value: Chunk[(String, Option[Double])]): AcceptEncoding = {
    val encodings = value.map { case (raw, weight) => identifyEncoding(raw, weight) }
    if (encodings.nonEmpty)
      MultipleEncodings(encodings)
    else InvalidEncoding
  }

  def fromAcceptEncoding(encoding: AcceptEncoding): Chunk[(String, Option[Double])] = encoding match {
    case MultipleEncodings(encodings) =>
      encodings.map(e =>
        (
          e.raw,
          e match {
            case BrEncoding(weight)           => weight
            case CompressEncoding(weight)     => weight
            case DeflateEncoding(weight)      => weight
            case GZipEncoding(weight)         => weight
            case IdentityEncoding(weight)     => weight
            case NoPreferenceEncoding(weight) => weight
            case _                            => None
          },
        ),
      )
    case _                            => Chunk.empty
  }

}
