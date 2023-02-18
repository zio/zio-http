package zio.http.forms

import zio.Chunk

import zio.http.model.Headers

/**
 * Represents the Content-Transfer-Encoding header.
 * @SEE:
 *   https://www.w3.org/Protocols/rfc1341/5_Content-Transfer-Encoding.html
 */
sealed trait ContentTransferEncoding { self =>
  val name: String = self match {
    case ContentTransferEncoding.Binary          => "binary"
    case ContentTransferEncoding.QuotedPrintable => "quoted-printable"
    case ContentTransferEncoding.Base64          => "base64"
    case ContentTransferEncoding.`7bit`          => "7bit"
    case ContentTransferEncoding.`8bit`          => "8bit"
  }

  def asHeaders: Headers = Headers.contentTransferEncoding(name)
}

object ContentTransferEncoding {

  def parse(value: String): Option[ContentTransferEncoding] = value.toLowerCase match {
    case "binary"           => Some(Binary)
    case "7bit"             => Some(`7bit`)
    case "8bit"             => Some(`8bit`)
    case "quoted-printable" => Some(QuotedPrintable)
    case "base64"           => Some(Base64)
    case _                  => None
  }

  case object Binary          extends ContentTransferEncoding
  case object `7bit`          extends ContentTransferEncoding
  case object `8bit`          extends ContentTransferEncoding
  case object QuotedPrintable extends ContentTransferEncoding
  case object Base64          extends ContentTransferEncoding
}
