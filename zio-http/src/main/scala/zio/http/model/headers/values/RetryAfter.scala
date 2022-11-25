package zio.http.model.headers.values

<<<<<<< HEAD
import java.time._
=======
import java.time.ZonedDateTime
>>>>>>> 03329814136dd3565dd7fea12dc7771b14f5b0d7
import java.time.format.DateTimeFormatter
import scala.util.Try

sealed trait RetryAfter

<<<<<<< HEAD
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
=======
object RetryAfter {
  // Wed, 21 Oct 2015 07:28:00 GMT
  private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  final case class WebDate(date: ZonedDateTime) extends RetryAfter

  final case class DelaySeconds(seconds: Long) extends RetryAfter

  case object InvalidRetryAfter extends RetryAfter

  def toRetryAfter(value: String): RetryAfter = (value match {
    case maybePositiveSeconds if value.nonEmpty && maybePositiveSeconds.forall(Character.isDigit) =>
      Try(DelaySeconds(value.toLong))
    case date                                                                                     =>
      Try(WebDate(ZonedDateTime.from(formatter.parse(date))))
  }) getOrElse InvalidRetryAfter

  def fromRetryAfter(retryAfter: RetryAfter): String = retryAfter match {
    case WebDate(date)         => formatter.format(date)
    case DelaySeconds(seconds) => seconds.toString
    case InvalidRetryAfter     => ""
  }

>>>>>>> 03329814136dd3565dd7fea12dc7771b14f5b0d7
}
