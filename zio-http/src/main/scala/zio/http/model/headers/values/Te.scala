package zio.http.model.headers.values

import scala.annotation.tailrec
import scala.util.Try

import zio.Chunk

sealed trait Te {
  def raw: String
}

object Te {

  /**
   * Signals an invalid value present in the header value.
   */
  case object InvalidEncoding extends Te {
    override def raw: String = "Invalid encoding"
  }

  /**
   * A compression format that uses the Lempel-Ziv-Welch (LZW) algorithm.
   */
  final case class CompressEncoding(weight: Option[Double]) extends Te {
    override def raw: String = "compress"
  }

  /**
   * A compression format that uses the zlib structure with the deflate
   * compression algorithm.
   */
  final case class DeflateEncoding(weight: Option[Double]) extends Te {
    override def raw: String = "deflate"
  }

  /**
   * A compression format that uses the Lempel-Ziv coding (LZ77) with a 32-bit
   * CRC.
   */
  final case class GZipEncoding(weight: Option[Double]) extends Te {
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
  final case class MultipleEncodings(encodings: Chunk[Te]) extends Te {
    override def raw: String = encodings.mkString(",")
  }

  private def identifyTeFull(raw: String): Te = {
    val index = raw.indexOf(";q=")
    if (index == -1)
      identifyTe(raw)
    else {
      identifyTe(raw.substring(0, index), Try(raw.substring(index + 3).toDouble).toOption)
    }
  }

  private def identifyTe(raw: String, weight: Option[Double] = None): Te = {
    raw.trim match {
      case "compress" => CompressEncoding(weight)
      case "deflate"  => DeflateEncoding(weight)
      case "gzip"     => GZipEncoding(weight)
      case "trailers" => Trailers
      case _          => InvalidEncoding
    }
  }

  def toTe(value: String): Te = {
    val index = value.indexOf(",")

    @tailrec def loop(value: String, index: Int, acc: MultipleEncodings): MultipleEncodings = {
      if (index == -1) acc.copy(encodings = acc.encodings ++ Chunk(identifyTeFull(value)))
      else {
        val valueChunk = value.substring(0, index)
        val remaining  = value.substring(index + 1)
        val nextIndex  = remaining.indexOf(",")
        val te         = Chunk(identifyTeFull(valueChunk))
        loop(
          remaining,
          nextIndex,
          acc.copy(encodings = acc.encodings ++ te),
        )
      }
    }

    if (index == -1)
      identifyTeFull(value)
    else
      loop(value, index, MultipleEncodings(Chunk.empty[Te]))

  }

  def fromTe(encoding: Te): String = encoding match {
    case c @ CompressEncoding(weight) => weight.fold(c.raw)(value => s"${c.raw};q=$value")
    case d @ DeflateEncoding(weight)  => weight.fold(d.raw)(value => s"${d.raw};q=$value")
    case g @ GZipEncoding(weight)     => weight.fold(g.raw)(value => s"${g.raw};q=$value")
    case MultipleEncodings(encodings) => encodings.map(fromTe).mkString(", ")
    case Trailers                     => Trailers.raw
    case InvalidEncoding              => ""
  }

}
