package zhttp.http

trait CanBeSilenced[-E, +A] {
  def silent(e: E): A
}

object CanBeSilenced {
  implicit object SilenceHttpError extends CanBeSilenced[Throwable, Response] {
    override def silent(e: Throwable): Response = e match {
      case m: HttpError => m.toResponse
      case m            => Response.fromHttpError(HttpError.InternalServerError("Internal Server Error", Option(m)))
    }
  }
}
