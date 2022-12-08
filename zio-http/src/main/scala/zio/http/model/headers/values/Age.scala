package zio.http.model.headers.values

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

  def fromAge(age: Age): Int =
    age match {
      case AgeValue(seconds) => seconds
      case InvalidAgeValue   => 0
    }

  def toAge(value: Int): Age =
    if (value > 0) AgeValue(value) else InvalidAgeValue
}
