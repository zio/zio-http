package zhttp.http

trait CanBeSilenced[-E, +A] {
  def silent(e: E): A
}

object CanBeSilenced {
  implicit object SilenceHttpError extends CanBeSilenced[Throwable, Response[Any, Nothing, Complete]] {
    override def silent(e: Throwable): Response[Any, Nothing, Complete] = e match {
      case m: HttpError => m.toResponse
      case m            => Response.fromHttpError(HttpError.InternalServerError("Internal Server Error", Option(m)))
    }
  }
}
