package zio.http.model.headers.values

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.util.Try

sealed trait RetryAfter

object RetryAfter {
  // Wed, 21 Oct 2015 07:28:00 GMT
  private val webDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  final case class WebDate(date: ZonedDateTime) extends RetryAfter

  final case class DelaySeconds(seconds: Long) extends RetryAfter

  case object InvalidRetryAfter extends RetryAfter

  def toRetryAfter(value: String): RetryAfter = (value match {
    case maybePositiveSeconds if value.nonEmpty && maybePositiveSeconds.forall(Character.isDigit) =>
      Try(DelaySeconds(value.toLong))
    case date                                                                                     =>
      Try(WebDate(ZonedDateTime.from(webDateFormatter.parse(date))))
  }) getOrElse InvalidRetryAfter

  def fromRetryAfter(retryAfter: RetryAfter): String = retryAfter match {
    case WebDate(date)         => webDateFormatter.format(date)
    case DelaySeconds(seconds) => seconds.toString
    case InvalidRetryAfter     => ""
  }

}
