package zhttp.http

trait CanSupportPartial[-A, +E] {
  def get(a: A): E
  def apply(a: A): E = get(a)
}

object CanSupportPartial {
  implicit object HttpPartial extends CanSupportPartial[Request, HttpError] {
    override def get(a: Request): HttpError = HttpError.NotFound(a.url.path)
  }

  implicit object RoutePartial extends CanSupportPartial[Route, HttpError] {
    override def get(a: Route): HttpError = HttpError.NotFound(a._2)
  }
}
