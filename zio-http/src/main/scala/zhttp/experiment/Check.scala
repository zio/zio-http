package zhttp.experiment

import zhttp.http._

case class Check[-A](is: A => Boolean) { self =>
  def &&[A1 <: A](other: Check[A1]): Check[A1] = Check(a => self.is(a) && other.is(a))
  def ||[A1 <: A](other: Check[A1]): Check[A1] = Check(a => self.is(a) || other.is(a))
}

object Check {
  def isTrue: Check[Any]  = Check(_ => true)
  def isFalse: Check[Any] = Check(_ => false)

  sealed trait AutoCheck[-A, -B] {
    def toCheck(a: A): Check[B]
  }

  implicit object PathChecker extends AutoCheck[Path, AnyRequest] {
    override def toCheck(a: Path): Check[AnyRequest] = Check[AnyRequest](_.url.path == a)
  }

  implicit object MethodChecker extends AutoCheck[Method, AnyRequest] {
    override def toCheck(a: Method): Check[AnyRequest] = Check[AnyRequest](_.method == a)
  }

  implicit object RouteChecker extends AutoCheck[(Method, Path), AnyRequest] {
    override def toCheck(a: (Method, Path)): Check[AnyRequest] =
      Check[AnyRequest](_.method == a._1) && Check[AnyRequest](_.url.path == a._2)
  }
}
