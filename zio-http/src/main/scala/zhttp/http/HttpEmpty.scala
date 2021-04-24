package zhttp.http

trait HttpEmpty[E] {
  def get: E
}

object HttpEmpty {
  implicit def throwableEmpty[E <: Throwable]: HttpEmpty[Throwable] = new HttpEmpty[Throwable] {
    override def get: Throwable = HttpError.NotFound(Path.empty)
  }

  def apply[E](e: E): HttpEmpty[E] = new HttpEmpty[E] {
    def get: E = e
  }

  import scala.language.implicitConversions

  implicit def toEmpty[E](e: E): HttpEmpty[E] = HttpEmpty(e)
}
