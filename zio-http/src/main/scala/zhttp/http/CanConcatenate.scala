package zhttp.http

trait CanConcatenate[-E] {
  def is(e: E): Boolean
}

object CanConcatenate {
  implicit object ChainableHttpError extends CanConcatenate[Throwable] {
    override def is(e: Throwable): Boolean = e match {
      case HttpError.NotFound(_) => true
      case _                     => false
    }
  }
}
