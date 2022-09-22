package zio.http.model.headers.values

import zio.Chunk

import scala.util.Try

sealed trait AcceptEncoding {
  def raw: String
}

object AcceptEncoding {
  case object InvalidEncoding extends AcceptEncoding {
    override def raw: String = "Invalid header value"
  }

  final case class BrEncoding(weight: Option[Double]) extends AcceptEncoding {
    override def raw: String = "br"
  }

  final case class CompressEncoding(weight: Option[Double]) extends AcceptEncoding {
    override def raw: String = "compress"
  }

  final case class DeflateEncoding(weight: Option[Double]) extends AcceptEncoding {
    override def raw: String = "deflate"
  }

  final case class GZipEncoding(weight: Option[Double]) extends AcceptEncoding {
    override def raw: String = "gzip"
  }

  final case class IdentityEncoding(weight: Option[Double]) extends AcceptEncoding {
    override def raw: String = "identity"
  }

  final case class MultipleEncodings(encodings: Chunk[AcceptEncoding]) extends AcceptEncoding {
    override def raw: String = encodings.map(_.raw).mkString(",")
  }

  final case class NoPreferenceEncoding(weight: Option[Double]) extends AcceptEncoding {
    override def raw: String = "*"
  }

  private def identifyEncodingFull(raw: String): Option[AcceptEncoding] = {
    raw.split(";q=") match {
      case Array(encoding)         => identifyEncoding(encoding)
      case Array(encoding, weight) => identifyEncoding(encoding, Try(weight.toDouble).toOption)
      case _                       => None
    }
  }

  private def identifyEncoding(raw: String, weight: Option[Double] = None): Option[AcceptEncoding] = {
    raw match {
      case "br"       => Some(BrEncoding(weight))
      case "compress" => Some(CompressEncoding(weight))
      case "deflate"  => Some(DeflateEncoding(weight))
      case "gzip"     => Some(GZipEncoding(weight))
      case "identity" => Some(IdentityEncoding(weight))
      case "*"        => Some(NoPreferenceEncoding(weight))
      case _          => None
    }
  }

  def toEncoding(value: String): AcceptEncoding = {
    value.split(',').flatMap(identifyEncodingFull) match {
      case ar @ Array(head, tail @ _*) =>
        if (tail.isEmpty) head
        else
          MultipleEncodings(Chunk.fromArray(ar))

      case _ => InvalidEncoding
    }
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
