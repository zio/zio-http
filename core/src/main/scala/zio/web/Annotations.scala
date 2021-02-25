package zio.web

sealed trait Annotations[A] { self =>
  def +[M](that: M): Annotations[M with A] = Annotations.Cons[M, A](that, self)

  def add[M](that: M): Annotations[M with A] = Annotations.Cons[M, A](that, self)
}

object Annotations {
  case object None                                                  extends Annotations[Any]
  sealed case class Cons[M, Tail](head: M, tail: Annotations[Tail]) extends Annotations[M with Tail]

  val none: Annotations[Any] = None
}
