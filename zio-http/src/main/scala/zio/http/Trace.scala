package zio.http

trait Trace {}

object Trace {
  implicit val trace: zio.http.Trace = new Trace {}
}
