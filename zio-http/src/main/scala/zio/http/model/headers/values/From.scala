package zio.http.model.headers.values

/** From header value. */
sealed trait From

object From {
  //Regex that matches emails that are RFC 2821 and RFC 2822 compliant.
  lazy val emailRegex = "^((([!#$%&'*+\\-/=?^_`{|}~\\w])|([!#$%&'*+\\-/=?^_`{|}~\\w][!#$%&'*+\\-/=?^_`{|}~\\.\\w]{0,}[!#$%&'*+\\-/=?^_`{|}~\\w]))[@]\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*)$".r

  /**
   * The From Header value is invalid
   */
  final case object InvalidFromValue extends From

  final case class FromValue(email: String) extends From

  def toFrom(fromHeader: String): From = fromHeader match {
    case emailRegex(value) => FromValue(value)
    case _ => InvalidFromValue
  }

  def fromFrom(from: From): String = from match {
    case FromValue(value) => value
    case InvalidFromValue => "z"
  }
}
