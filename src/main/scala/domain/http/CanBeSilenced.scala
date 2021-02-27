package zio-http.domain.http

import zio-http.domain.http.model.{HttpError, Response}

trait CanBeSilenced[-E, +A] {
  def silent(e: E): A
}

object CanBeSilenced {
  implicit object SilenceHttpError extends CanBeSilenced[HttpError, Response] {
    override def silent(e: HttpError): Response = e.toResponse
  }
}
