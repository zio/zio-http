//package zio.http.model.headers.values
//
//sealed trait ContentTransferEncoding
//
//object ContentTransferEncoding {
//  case object SevenBit                       extends ContentTransferEncoding
//  case object EightBit                       extends ContentTransferEncoding
//  case object Binary                         extends ContentTransferEncoding
//  case object QuotedPrintable                extends ContentTransferEncoding
//  case object Base64                         extends ContentTransferEncoding
//  final case class XToken(token: String)     extends ContentTransferEncoding
//  case object InvalidContentTransferEncoding extends ContentTransferEncoding
//
//  val XRegex                                                              = "x-(.*)".r
//  def toContentTransferEncoding(s: CharSequence): ContentTransferEncoding =
//    s.toString.toLowerCase match {
//      case "7bit"             => SevenBit
//      case "8bit"             => EightBit
//      case "binary"           => Binary
//      case "quoted-printable" => QuotedPrintable
//      case "base64"           => Base64
//      case XRegex(token)      => XToken(token)
//      case _                  => InvalidContentTransferEncoding
//    }
//
//  def fromContentTransferEncoding(contentTransferEncoding: ContentTransferEncoding): String =
//    contentTransferEncoding match {
//      case SevenBit                       => "7bit"
//      case EightBit                       => "8bit"
//      case Binary                         => "binary"
//      case QuotedPrintable                => "quoted-printable"
//      case Base64                         => "base64"
//      case XToken(token)                  => s"x-$token"
//      case InvalidContentTransferEncoding => ""
//    }
//}
