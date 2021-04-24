package zhttp.http

trait HttpEmpty[E] {
  def get: E
}

object HttpEmpty {
  implicit def throwableEmpty[E >: HttpError]: HttpEmpty[E] = new HttpEmpty[E] {
    override def get: E = HttpError.NotFound(Path.empty)
  }

  def apply[E](e: E): HttpEmpty[E] = new HttpEmpty[E] {
    def get: E = e
  }
}
