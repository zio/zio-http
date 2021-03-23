package zhttp.http

trait CanSupportPartial[-A, +E] {
  def get(a: A): E
  def apply(a: A): E = get(a)
}

object CanSupportPartial {
  implicit object HttpPartial extends CanSupportPartial[Request, Throwable] {
    override def get(a: Request): Throwable = HttpError.NotFound(a.url.path)
  }
}
