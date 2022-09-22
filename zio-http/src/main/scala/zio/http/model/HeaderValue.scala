package zio.http.model

import zio.Chunk

import scala.util.Try

object HeaderValue {

  sealed trait Encoding {
    def raw: String
  }

  object Encoding {
    case object InvalidEncoding extends Encoding {
      override def raw: String = "Invalid header value"
    }

    final case class BrEncoding(weight: Option[Double]) extends Encoding {
      override def raw: String = "br"
    }

    final case class CompressEncoding(weight: Option[Double]) extends Encoding {
      override def raw: String = "compress"
    }

    final case class DeflateEncoding(weight: Option[Double]) extends Encoding {
      override def raw: String = "deflate"
    }

    final case class GZipEncoding(weight: Option[Double]) extends Encoding {
      override def raw: String = "gzip"
    }

    final case class IdentityEncoding(weight: Option[Double]) extends Encoding {
      override def raw: String = "identity"
    }

    final case class MultipleEncodings(encodings: Chunk[Encoding]) extends Encoding {
      override def raw: String = encodings.map(_.raw).mkString(",")
    }

    final case class NoPreferenceEncoding(weight: Option[Double]) extends Encoding {
      override def raw: String = "*"
    }

    private def identifyEncodingFull(raw: String): Option[Encoding] = {
      raw.split(";q=") match {
        case Array(encoding)         => identifyEncoding(encoding)
        case Array(encoding, weight) => identifyEncoding(encoding, Try(weight.toDouble).toOption)
        case _                       => None
      }
    }

    private def identifyEncoding(raw: String, weight: Option[Double] = None): Option[Encoding] = {
      raw match {
        case "br"       => Some(Encoding.BrEncoding(weight))
        case "compress" => Some(Encoding.CompressEncoding(weight))
        case "deflate"  => Some(Encoding.DeflateEncoding(weight))
        case "gzip"     => Some(Encoding.GZipEncoding(weight))
        case "identity" => Some(Encoding.IdentityEncoding(weight))
        case "*"        => Some(Encoding.NoPreferenceEncoding(weight))
        case _          => None
      }
    }

    def toEncoding(value: String): Encoding = {
      value.split(",").flatMap(Encoding.identifyEncodingFull) match {
        case ar @ Array(head, tail @ _*) =>
          if (tail.isEmpty) head
          else
            MultipleEncodings(Chunk.fromArray(ar))

        case _ => Encoding.InvalidEncoding
      }
    }

    def fromEncoding(encoding: Encoding): String = encoding match {
      case b @ Encoding.BrEncoding(weight)           => weight.fold(b.raw)(value => s"${b.raw};q=$value")
      case c @ Encoding.CompressEncoding(weight)     => weight.fold(c.raw)(value => s"${c.raw};q=$value")
      case d @ Encoding.DeflateEncoding(weight)      => weight.fold(d.raw)(value => s"${d.raw};q=$value")
      case g @ Encoding.GZipEncoding(weight)         => weight.fold(g.raw)(value => s"${g.raw};q=$value")
      case i @ Encoding.IdentityEncoding(weight)     => weight.fold(i.raw)(value => s"${i.raw};q=$value")
      case Encoding.MultipleEncodings(encodings)     => encodings.map(fromEncoding).mkString(",")
      case n @ Encoding.NoPreferenceEncoding(weight) => weight.fold(n.raw)(value => s"${n.raw};q=$value")
      case InvalidEncoding                           => ""
    }

  }

}
