package zio.http.model.headers.values

import java.time._
import java.time.format.DateTimeFormatter

sealed trait Expires {
  def value: ZonedDateTime
}

/**
 * The Expires HTTP header contains the date/time after which the response is
 * considered expired.
 *
 * Invalid expiration dates with value 0 represent a date in the past and mean
 * that the resource is already expired.
 *
 * Expires: <Date>
 *
 * Date: <day-name>, <day> <month> <year> <hour>:<minute>:<second> GMT
 *
 * Example:
 *
 * Expires: Wed, 21 Oct 2015 07:28:00 GMT
 */
object Expires {
  private val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  final case class ValidExpires(date: ZonedDateTime) extends Expires {
    override def value: ZonedDateTime = date
  }

  case object InvalidExpires extends Expires {
    override def value: ZonedDateTime = ZonedDateTime.of(0, 0, 0, 0, 0, 0, 0, ZoneId.systemDefault())

  }

  def toExpires(date: String): Expires =
    try {
      ValidExpires(ZonedDateTime.parse(date, formatter))
    } catch {
      case _: Exception => InvalidExpires
    }

  def fromExpires(expires: Expires): String = expires match {
    case ValidExpires(date) => formatter.format(date)
    case _                  => "0"
  }
}
