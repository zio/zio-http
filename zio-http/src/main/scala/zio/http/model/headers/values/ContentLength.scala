package zio.http.model.headers.values

import scala.util.Try

/**
 * ContentLength header value
 */
sealed trait ContentLength

object ContentLength {

  /**
   * The Content-Length header indicates the size of the message body, in bytes,
   * sent to the recipient.
   */
  final case class ContentLengthValue(length: Long) extends ContentLength

  /**
   * The ContentLength header value is invalid.
   */
  case object InvalidContentLengthValue extends ContentLength

  def fromContentLength(contentLength: ContentLength): String =
    contentLength match {
      case ContentLengthValue(length) => length.toString
      case InvalidContentLengthValue  => ""
    }

  def toContentLength(value: String): ContentLength =
    Try(value.trim.toLong).fold(
      _ => InvalidContentLengthValue,
      value => if (value > 0) ContentLengthValue(value) else InvalidContentLengthValue,
    )

}
