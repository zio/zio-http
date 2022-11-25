package zio.http.model.headers.values

import zio.Chunk

import scala.annotation.tailrec
import scala.util.Try

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

  private def identifyEncodingFull(raw: String): AcceptEncoding = {
    val index = raw.indexOf(";q=")
    if (index == -1)
      identifyEncoding(raw)
    else {
      identifyEncoding(raw.substring(0, index), Try(raw.substring(index + 3).toDouble).toOption)
    }
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

  def toAcceptEncoding(value: String): AcceptEncoding = {
    val index = value.indexOf(",")

    @tailrec def loop(value: String, index: Int, acc: MultipleEncodings): MultipleEncodings = {
      if (index == -1) acc.copy(encodings = acc.encodings ++ Chunk(identifyEncodingFull(value)))
      else {
        val valueChunk       = value.substring(0, index)
        val remaining        = value.substring(index + 1)
        val nextIndex        = remaining.indexOf(",")
        val acceptedEncoding = Chunk(identifyEncodingFull(valueChunk))
        loop(
          remaining,
          nextIndex,
          acc.copy(encodings = acc.encodings ++ acceptedEncoding),
        )
      }
    }

    if (index == -1)
      identifyEncodingFull(value)
    else
      loop(value, index, MultipleEncodings(Chunk.empty[AcceptEncoding]))

  }

  def fromAcceptEncoding(encoding: AcceptEncoding): String = encoding match {
    case b @ BrEncoding(weight)           => weight.fold(b.raw)(value => s"${b.raw};q=$value")
    case c @ CompressEncoding(weight)     => weight.fold(c.raw)(value => s"${c.raw};q=$value")
    case d @ DeflateEncoding(weight)      => weight.fold(d.raw)(value => s"${d.raw};q=$value")
    case g @ GZipEncoding(weight)         => weight.fold(g.raw)(value => s"${g.raw};q=$value")
    case i @ IdentityEncoding(weight)     => weight.fold(i.raw)(value => s"${i.raw};q=$value")
    case MultipleEncodings(encodings)     => encodings.map(fromAcceptEncoding).mkString(",")
    case n @ NoPreferenceEncoding(weight) => weight.fold(n.raw)(value => s"${n.raw};q=$value")
    case InvalidEncoding                  => ""
  }

}
