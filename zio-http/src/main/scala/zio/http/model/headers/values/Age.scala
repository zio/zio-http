package zio.http.model.headers.values

import scala.util.Try

/**
 * Age header value.
 */
sealed trait Age

object Age {

  /**
   * The Age header contains the time in seconds the object was in a proxy
   * cache.
   */
  final case class AgeValue(seconds: Int) extends Age

  /**
   * The Age header value is invalid.
   */
  case object InvalidAgeValue extends Age

  def fromAge(age: Age): String =
    age match {
      case AgeValue(seconds) => seconds.toString
      case InvalidAgeValue   => ""
    }

  def toAge(value: String): Age =
    Try(value.trim.toInt).fold(_ => InvalidAgeValue, value => if (value > 0) AgeValue(value) else InvalidAgeValue)
}
