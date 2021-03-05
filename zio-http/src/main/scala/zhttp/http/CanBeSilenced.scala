package zhttp.http

trait CanBeSilenced[-E, +A] {
  def silent(e: E): A
}

object CanBeSilenced {
  implicit object SilenceHttpError extends CanBeSilenced[HttpError, Response] {
    override def silent(e: HttpError): Response = e.toResponse
  }
}
