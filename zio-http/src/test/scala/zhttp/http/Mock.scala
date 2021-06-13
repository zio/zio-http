package zhttp.http

import scala.annotation.unused

final class Mock[A]() { self =>
  def map[B](@unused ab: A => B): Mock[B] = Mock[B]
  def is[B](implicit ev: A =:= B): Unit   = ()
}

object Mock {
  def apply[A]: Mock[A] = new Mock[A]()
}
