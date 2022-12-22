//package zio.http.model.headers.values
//
//import zio.Chunk
//
//sealed trait ContentEncoding {
//  val encoding: String
//}
//
//object ContentEncoding {
//
//  /**
//   * InvalidEncoding is represented with ""
//   */
//  case object InvalidEncoding extends ContentEncoding {
//    override val encoding: String = ""
//  }
//
//  /**
//   * A format using the Brotli algorithm.
//   */
//  case object BrEncoding extends ContentEncoding {
//    override val encoding: String = "br"
//  }
//
//  /**
//   * A format using the Lempel-Ziv-Welch (LZW) algorithm. The value name was
//   * taken from the UNIX compress program, which implemented this algorithm.
//   * Like the compress program, which has disappeared from most UNIX
//   * distributions, this content-encoding is not used by many browsers today,
//   * partly because of a patent issue (it expired in 2003).
//   */
//  case object CompressEncoding extends ContentEncoding {
//    override val encoding: String = "compress"
//  }
//
//  /**
//   * Using the zlib structure (defined in RFC 1950) with the deflate compression
//   * algorithm (defined in RFC 1951).
//   */
//  case object DeflateEncoding extends ContentEncoding {
//    override val encoding: String = "deflate"
//  }
//
//  /**
//   * A format using the Lempel-Ziv coding (LZ77), with a 32-bit CRC. This is the
//   * original format of the UNIX gzip program. The HTTP/1.1 standard also
//   * recommends that the servers supporting this content-encoding should
//   * recognize x-gzip as an alias, for compatibility purposes.
//   */
//  case object GZipEncoding extends ContentEncoding {
//    override val encoding: String = "gzip"
//  }
//
//  /**
//   * Maintains a list of ContentEncoding values.
//   */
//  final case class MultipleEncodings(encodings: Chunk[ContentEncoding]) extends ContentEncoding {
//    override val encoding: String = encodings.map(_.encoding).mkString(",")
//  }
//
//  private def findEncoding(value: String): ContentEncoding = {
//    value.trim match {
//      case "br"       => BrEncoding
//      case "compress" => CompressEncoding
//      case "deflate"  => DeflateEncoding
//      case "gzip"     => GZipEncoding
//      case _          => InvalidEncoding
//    }
//  }
//
//  /**
//   * @param value
//   *   of string , seperated for multiple values
//   * @return
//   *   ContentEncoding
//   *
//   * Note: This implementation ignores the invalid string that might occur in
//   * MultipleEncodings case.
//   */
//  def toContentEncoding(value: CharSequence): ContentEncoding = {
//    val array = value.toString.split(",")
//    array.foldLeft[ContentEncoding](InvalidEncoding)((accum, elem) => {
//      val encoding = findEncoding(elem)
//      (accum, encoding) match {
//        case (InvalidEncoding, InvalidEncoding)              => InvalidEncoding
//        case (InvalidEncoding, other)                        => other
//        case (MultipleEncodings(encodings), InvalidEncoding) => MultipleEncodings(encodings)
//        case (MultipleEncodings(encodings), other)           => MultipleEncodings(encodings ++ Chunk(other))
//        case (other, InvalidEncoding)                        => other
//        case (other, other1)                                 => MultipleEncodings(Chunk(other, other1))
//      }
//    })
//  }
//
//  def fromContentEncoding(value: ContentEncoding): String = value.encoding
//
//}
