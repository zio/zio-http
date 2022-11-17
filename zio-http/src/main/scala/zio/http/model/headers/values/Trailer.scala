package zio.http.model.headers.values

/** Trailer header value. */
sealed trait Trailer

object Trailer {
  lazy val headerRegex = "([a-z-_]*)".r
  case class TrailerValue(header: String) extends Trailer

  /** Invalid Trailer value. */
  case object InvalidTrailerValue extends Trailer

  def toTrailer(value: String): Trailer =
    value.toLowerCase match {
      case headerRegex(value) => TrailerValue(value)
      case _ => InvalidTrailerValue
    }

  def fromTrailer(trailer: Trailer): String =
    trailer match {
      case TrailerValue(header) => header
      case InvalidTrailerValue  => ""
    }
}
