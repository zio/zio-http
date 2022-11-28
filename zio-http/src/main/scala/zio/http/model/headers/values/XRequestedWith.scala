package zio.http.model.headers.values

sealed trait XRequestedWith

object XRequestedWith {
  final case class XMLHttpRequest(value: String) extends XRequestedWith

  def fromXRequestedWith(xRequestedWith: XRequestedWith): String =
    xRequestedWith match {
      case XMLHttpRequest(value) => value
    }

  def toXRequestedWith(value: String): XRequestedWith =
    XMLHttpRequest(value)
}
