package zio.web

import zio.web.internal.Combine

sealed trait Annotations[+M[+_], A] { self =>

  def +[M1[+_] >: M[_], B](that: M1[B])(implicit combine: Combine[B, A]): Annotations[M1, combine.Out] =
    Annotations.Cons[M1, B, A, combine.Out](that, self, combine)
}

object Annotations {

  case object None extends Annotations[NothingF, Unit]
  final case class Cons[M[+_], A, B, X](head: M[A], tail: Annotations[M, B], combine: Combine.Aux[A, B, X])
      extends Annotations[M, X]

  val none: Annotations[NothingF, Unit] = None
}
