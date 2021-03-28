package zhttp.http

trait CanBeSilenced[-E, +A] {
  def silent(e: E): A
}

object CanBeSilenced {
  implicit object SilenceHttpError extends CanBeSilenced[Throwable, UResponse] {
    override def silent(e: Throwable): UResponse = e match {
      case m: HttpError => m.toResponse
      case m            => Response.fromHttpError(HttpError.InternalServerError("Internal Server Error", Option(m)))
    }
  }
}
