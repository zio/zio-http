package zio.http.model.headers.values

import zio.Chunk

import scala.annotation.tailrec
import scala.util.Try

sealed trait AcceptEncoding {
  val raw: String
}

object AcceptEncoding {
  case object InvalidEncoding extends AcceptEncoding {
    override val raw: String = "Invalid header value"
  }

  final case class BrEncoding(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "br"
  }

  final case class CompressEncoding(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "compress"
  }

  final case class DeflateEncoding(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "deflate"
  }

  final case class GZipEncoding(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "gzip"
  }

  final case class IdentityEncoding(weight: Option[Double]) extends AcceptEncoding {
    override val raw: String = "identity"
  }

  final case class MultipleEncodings(encodings: Chunk[AcceptEncoding]) extends AcceptEncoding {
    override val raw: String = encodings.map(_.raw).mkString(",")
  }

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
    raw match {
      case "br"       => BrEncoding(weight)
      case "compress" => CompressEncoding(weight)
      case "deflate"  => DeflateEncoding(weight)
      case "gzip"     => GZipEncoding(weight)
      case "identity" => IdentityEncoding(weight)
      case "*"        => NoPreferenceEncoding(weight)
      case _          => InvalidEncoding
    }
  }

  def toEncoding(value: String): AcceptEncoding = {
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

  def fromEncoding(encoding: AcceptEncoding): String = encoding match {
    case b @ BrEncoding(weight)           => weight.fold(b.raw)(value => s"${b.raw};q=$value")
    case c @ CompressEncoding(weight)     => weight.fold(c.raw)(value => s"${c.raw};q=$value")
    case d @ DeflateEncoding(weight)      => weight.fold(d.raw)(value => s"${d.raw};q=$value")
    case g @ GZipEncoding(weight)         => weight.fold(g.raw)(value => s"${g.raw};q=$value")
    case i @ IdentityEncoding(weight)     => weight.fold(i.raw)(value => s"${i.raw};q=$value")
    case MultipleEncodings(encodings)     => encodings.map(fromEncoding).mkString(",")
    case n @ NoPreferenceEncoding(weight) => weight.fold(n.raw)(value => s"${n.raw};q=$value")
    case InvalidEncoding                  => ""
  }

}
