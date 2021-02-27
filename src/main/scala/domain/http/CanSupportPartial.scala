package zio-http.domain.http

import zio-http.domain.http.model.{HttpError, Request}

sealed trait CanSupportPartial[-A, +E] {
  def get(A: A): E
  def apply(A: A): E = get(A)
}

object CanSupportPartial {
  implicit object HttpPartial extends CanSupportPartial[Request, HttpError] {
    override def get(A: Request): HttpError = HttpError.NotFound(A.url.path)
  }

  implicit object RoutePartial extends CanSupportPartial[Route, HttpError] {
    override def get(A: Route): HttpError = HttpError.NotFound(A._2)
  }
}
