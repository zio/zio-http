package zio.http.model.headers.values

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.util.Try

/**
 * The If-Range HTTP request header makes a range request conditional. Possible
 * values:
 *   - <day-name>, <day> <month> <year> <hour>:<minute>:<second> GMT
 *   - <etag> a string of ASCII characters placed between double quotes (Like
 *     "675af34563dc-tr34"). A weak entity tag (one prefixed by W/) must not be
 *     used in this header.
 */
sealed trait IfRange

object IfRange {
  private val webDateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  final case class ETagValue(value: String)            extends IfRange
  final case class DateTimeValue(value: ZonedDateTime) extends IfRange
  case object InvalidIfRangeValue                      extends IfRange

  def toIfRange(value: String): IfRange =
    value match {
      case s""""$etag"""" => ETagValue(etag)
      case dateTime       =>
        Try(DateTimeValue(ZonedDateTime.from(webDateTimeFormatter.parse(dateTime))))
          .getOrElse(InvalidIfRangeValue)
    }

  def fromIfRange(ifRange: IfRange): String =
    ifRange match {
      case DateTimeValue(value) => webDateTimeFormatter.format(value)
      case ETagValue(value)     => s""""$value""""
      case InvalidIfRangeValue  => ""
    }
}
