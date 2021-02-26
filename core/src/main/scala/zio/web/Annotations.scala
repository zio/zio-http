package zio.web

sealed trait Annotations[+M[+_], +A] { self =>

  def +[M1[+_] >: M[_], B](that: M1[B])(implicit ac: Annotations.Combine[B, A]): Annotations[M1, ac.Out] =
    Annotations.Cons[M1, B, A, ac.Out](that, self, ac.combine)
}

object Annotations {
  sealed trait Combine[-A, -B] {
    type Out

    val combine: (A, B) => Out
  }

  object Combine extends CombineMidPriority {
    type Aux[-A, -B, Out0] = Combine[A, B] { type Out = Out0 }

    implicit def combineUnitB[B]: Combine.Aux[Unit, B, B] =
      new Combine[Unit, B] {
        type Out = B

        val combine: (Unit, B) => B = (_, b) => b
      }
  }

  trait CombineMidPriority extends CombineLowPriority {
    implicit def combineAUnit[A]: Combine.Aux[A, Unit, A] =
      new Combine[A, Unit] {
        type Out = A

        val combine: (A, Unit) => A = (a, _) => a
      }
  }

  trait CombineLowPriority {
    implicit def combineAB[A, B]: Combine.Aux[A, B, (A, B)] =
      new Combine[A, B] {
        type Out = (A, B)

        val combine: (A, B) => (A, B) = (a, b) => (a, b)
      }
  }

  case object None extends Annotations[NothingF, Unit]
  final case class Cons[M[+_], A, B, X](head: M[A], tail: Annotations[M, B], combine: (A, B) => X)
      extends Annotations[M, X]

  val none: Annotations[NothingF, Unit] = None
}
