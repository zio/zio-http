package zio.http.model.headers.values

import java.time._
import java.time.format.DateTimeFormatter
import scala.util.Try

sealed trait RetryAfter

/**
 * The RetryAfter HTTP header contains the date/time after which to retry
 *
 * Invalid RetryAfter with value 0
 *
 * RetryAfter: <Date>
 *
 * Date: <day-name>, <day> <month> <year> <hour>:<minute>:<second> GMT
 *
 * Example:
 *
 * Expires: Wed, 21 Oct 2015 07:28:00 GMT
 *
 * Or RetryAfter the delay seconds.
 */
object RetryAfter {
  private val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  final case class RetryAfterByDate(date: ZonedDateTime) extends RetryAfter

  final case class RetryAfterByDuration(delay: Duration) extends RetryAfter

  case object InvalidRetryAfter extends RetryAfter

  def toRetryAfter(dateOrSeconds: String): RetryAfter = {
    (Try(dateOrSeconds.toLong) orElse Try(ZonedDateTime.parse(dateOrSeconds, formatter)) map {
      case long: Long if long > -1 => RetryAfterByDuration(Duration.ofSeconds(long))
      case date: ZonedDateTime     => RetryAfterByDate(date)
    } recover { case e: Throwable =>
      InvalidRetryAfter
    }).getOrElse(InvalidRetryAfter)
  }

  def fromRetryAfter(retryAfter: RetryAfter): String = retryAfter match {
    case RetryAfterByDate(date)         => formatter.format(date)
    case RetryAfterByDuration(duration) =>
      duration.getSeconds().toString
    case _                              => "0"
  }
}
